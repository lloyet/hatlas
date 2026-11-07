#!/usr/bin/env bash
# Daily backup / restore of the Hatlas faction server: PostgreSQL dump + world copy, taken
# together so they always match (a data-only restore brings crystals back without entities).
#
#   hatlas-backup.sh backup                  take a backup into daily/ (and weekly/ once a week), then prune
#   hatlas-backup.sh list                    list the backups
#   hatlas-backup.sh verify <backup>         check checksums and that the dump is readable
#   hatlas-backup.sh restore <backup> [--full] [--stop] [--start] [--yes]
#   hatlas-backup.sh prune                   apply the rotation only
#
# <backup> is a name from `list` (e.g. 2026-11-08_043000) or a backup folder path.
#
# backup, with the server running (hot):  save-off -> save-all flush -> hatlas storage save
#   -> pg_dump -Fc + tar of BACKUP_PATHS -> save-on. Server stopped (cold): dump + tar only.
# restore needs the server stopped (--stop stops it). It first saves the current state into
#   pre-restore/, then puts the files back and replaces the GAME_SCHEMAS only (the website's
#   data, e.g. tickets, is kept). --full drops and recreates the whole database instead.
#
# Settings: see backup.env.example. Read from $HATLAS_BACKUP_ENV, else /etc/hatlas/backup.env,
# else backup.env next to this script; variables already in the environment win.
# Needs: bash, GNU tar/coreutils, python3 (RCON), docker compose. Doc: docs/backup.md.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { printf '%s %s\n' "$(date '+%F %T')" "$*"; }
warn() { printf '%s WARN %s\n' "$(date '+%F %T')" "$*" >&2; }
die()  { printf '%s ERROR %s\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

# ── Settings ─────────────────────────────────────────────────────────────────
load_env_file() {
    local file="$1" line key value
    [[ -f "$file" ]] || return 0
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
        key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
        value="${value#"${value%%[![:space:]]*}"}"; value="${value%"${value##*[![:space:]]}"}"
        if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
        [[ -n "${!key+x}" ]] || export "$key=$value"
    done < "$file"
}

if [[ -n "${HATLAS_BACKUP_ENV:-}" ]]; then
    [[ -f "$HATLAS_BACKUP_ENV" ]] || die "HATLAS_BACKUP_ENV=$HATLAS_BACKUP_ENV does not exist"
    load_env_file "$HATLAS_BACKUP_ENV"
elif [[ -f /etc/hatlas/backup.env ]]; then
    load_env_file /etc/hatlas/backup.env
else
    load_env_file "$SCRIPT_DIR/backup.env"
fi

: "${SERVER_DIR:?SERVER_DIR is not set (see backup.env.example)}"
: "${BACKUP_DIR:?BACKUP_DIR is not set (see backup.env.example)}"
: "${COMPOSE_FILE:?COMPOSE_FILE is not set (see backup.env.example)}"
BACKUP_PATHS="${BACKUP_PATHS:-world world_nether world_the_end plugins/hatlas}"
PG_SERVICE="${PG_SERVICE:-postgres}"
PG_USER="${PG_USER:-hatlas}"
PG_DATABASE="${PG_DATABASE:-hatlas}"
GAME_SCHEMAS="${GAME_SCHEMAS:-public}"
KEEP_DAILY="${KEEP_DAILY:-7}"
KEEP_WEEKLY="${KEEP_WEEKLY:-4}"
KEEP_PRE_RESTORE="${KEEP_PRE_RESTORE:-3}"
PYTHON="${PYTHON:-python3}"
read -r -a DOCKER_CMD <<< "${DOCKER:-docker}"
export MC_RCON_HOST="${MC_RCON_HOST:-127.0.0.1}" MC_RCON_PORT="${MC_RCON_PORT:-25575}"

read -r -a PATHS <<< "$BACKUP_PATHS"
for p in "${PATHS[@]}"; do
    [[ "$p" != /* && "$p" != *..* ]] || die "BACKUP_PATHS entries must be relative, without '..': $p"
done

# ── Helpers ──────────────────────────────────────────────────────────────────
compose() { "${DOCKER_CMD[@]}" compose -f "$COMPOSE_FILE" "$@"; }
psql_on() { compose exec -T "$PG_SERVICE" psql -U "$PG_USER" -d "$1" -v ON_ERROR_STOP=1 -qtAc "$2"; }

rcon() { "$PYTHON" "$SCRIPT_DIR/rcon.py" "$@"; }

# 0 = running (RCON answers), 1 = stopped (cannot connect). Anything else aborts.
server_running() {
    local rc=0
    rcon list > /dev/null 2>&1 || rc=$?
    case "$rc" in
        0) return 0 ;;
        2) return 1 ;;
        3) die "RCON authentication failed: check MC_RCON_PASSWORD" ;;
        *) die "RCON error (exit $rc); is MC_RCON_PASSWORD set?" ;;
    esac
}

LOCK_DIR="$BACKUP_DIR/.lock"
take_lock() {
    mkdir -p "$BACKUP_DIR"
    if ! mkdir "$LOCK_DIR" 2> /dev/null; then
        local pid; pid="$(cat "$LOCK_DIR/pid" 2> /dev/null || true)"
        if [[ -n "$pid" ]] && kill -0 "$pid" 2> /dev/null; then die "another backup/restore is running (pid $pid)"; fi
        warn "removing stale lock (pid ${pid:-?})"
        rm -rf "$LOCK_DIR"; mkdir "$LOCK_DIR"
    fi
    echo $$ > "$LOCK_DIR/pid"
}

SAVE_OFF=0
TMP_DIR=""
cleanup() {
    local rc=$?
    if [[ "$SAVE_OFF" == 1 ]]; then
        rcon save-on > /dev/null 2>&1 && log "save-on (cleanup)" || warn "could not send save-on: run it in the console!"
    fi
    [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]] && rm -rf "$TMP_DIR"
    [[ -d "$LOCK_DIR" && "$(cat "$LOCK_DIR/pid" 2> /dev/null)" == "$$" ]] && rm -rf "$LOCK_DIR"
    exit "$rc"
}
trap cleanup EXIT

# Writes database.dump, files.tar.gz, manifest.txt, SHA256SUMS into $1.
# $2 = "hot" (server running: freeze saves around the copy) or "cold".
snapshot_into() {
    local dest="$1" mode="$2" existing=() p rc reply
    mkdir -p "$dest"
    for p in "${PATHS[@]}"; do [[ -e "$SERVER_DIR/$p" ]] && existing+=("$p") || warn "skipping missing $SERVER_DIR/$p"; done
    [[ ${#existing[@]} -gt 0 ]] || die "nothing to copy under $SERVER_DIR (BACKUP_PATHS=$BACKUP_PATHS)"

    if [[ "$mode" == hot ]]; then
        rcon save-off > /dev/null; SAVE_OFF=1; log "save-off"
        rcon "save-all flush" > /dev/null; log "save-all flush done"
        reply="$(rcon "hatlas storage save")"
        [[ "$reply" == *"Storage saved"* ]] || die "hatlas storage save failed: ${reply:-no reply (is Hatlas enabled?)}"
        log "hatlas storage save done"
    fi

    log "pg_dump $PG_DATABASE"
    compose exec -T "$PG_SERVICE" pg_dump -U "$PG_USER" -Fc "$PG_DATABASE" > "$dest/database.dump"

    log "tar ${existing[*]}"
    rc=0
    tar -C "$SERVER_DIR" --exclude='session.lock' -czf "$dest/files.tar.gz" "${existing[@]}" || rc=$?
    if [[ "$rc" == 1 ]]; then warn "tar: some files changed while being read"; elif [[ "$rc" != 0 ]]; then die "tar failed ($rc)"; fi

    if [[ "$SAVE_OFF" == 1 ]]; then rcon save-on > /dev/null; SAVE_OFF=0; log "save-on"; fi

    {
        echo "created=$(date '+%F %T %z')"
        echo "host=$(hostname)"
        echo "mode=$mode"
        echo "server_dir=$SERVER_DIR"
        echo "paths=${existing[*]}"
        echo "database=$PG_DATABASE"
    } > "$dest/manifest.txt"
    (cd "$dest" && sha256sum database.dump files.tar.gz > SHA256SUMS)
    verify_dir "$dest"
}

verify_dir() {
    local dir="$1"
    [[ -f "$dir/SHA256SUMS" ]] || die "$dir is not a backup (no SHA256SUMS)"
    (cd "$dir" && sha256sum -c --quiet SHA256SUMS) || die "checksum mismatch in $dir"
    compose exec -T "$PG_SERVICE" pg_restore --list < "$dir/database.dump" > /dev/null \
        || die "database.dump in $dir is not readable by pg_restore"
    tar -tzf "$dir/files.tar.gz" > /dev/null || die "files.tar.gz in $dir is not readable"
    log "verified $dir"
}

resolve_backup() {
    local ref="$1"
    if [[ -d "$ref" ]]; then (cd "$ref" && pwd); return; fi
    for kind in daily weekly pre-restore; do
        [[ -d "$BACKUP_DIR/$kind/$ref" ]] && { echo "$BACKUP_DIR/$kind/$ref"; return; }
    done
    die "no backup '$ref' in $BACKUP_DIR (see: $0 list)"
}

prune_kind() {
    local kind="$1" keep="$2" old
    [[ -d "$BACKUP_DIR/$kind" ]] || return 0
    # Names are timestamps (YYYY-MM-DD_HHMMSS): newest first, drop everything after $keep.
    while IFS= read -r old; do
        [[ -n "$old" ]] || continue
        rm -rf "${BACKUP_DIR:?}/$kind/$old"; log "pruned $kind/$old"
    done < <(ls -1 "$BACKUP_DIR/$kind" | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{6}$' | sort -r | tail -n +"$((keep + 1))")
}

prune() {
    prune_kind daily "$KEEP_DAILY"
    prune_kind weekly "$KEEP_WEEKLY"
    prune_kind pre-restore "$KEEP_PRE_RESTORE"
}

# ── Commands ─────────────────────────────────────────────────────────────────
cmd_backup() {
    take_lock
    rm -rf "$BACKUP_DIR"/.incomplete-* 2> /dev/null || true
    local name mode final cutoff latest_weekly
    name="$(date +%Y-%m-%d_%H%M%S)"
    if server_running; then mode=hot; else mode=cold; warn "server not running: cold backup"; fi
    TMP_DIR="$BACKUP_DIR/.incomplete-$name"
    log "backup $name ($mode) of $SERVER_DIR + database $PG_DATABASE"
    snapshot_into "$TMP_DIR" "$mode"

    mkdir -p "$BACKUP_DIR/daily"
    final="$BACKUP_DIR/daily/$name"
    mv "$TMP_DIR" "$final"; TMP_DIR=""

    # Weekly: promote this backup when the newest weekly one is 6+ days old (or none exists).
    cutoff="$(date -d '6 days ago' +%Y-%m-%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR/weekly"
    latest_weekly="$(ls -1 "$BACKUP_DIR/weekly" | sort -r | head -n 1)"
    if [[ -z "$latest_weekly" || "$latest_weekly" < "$cutoff" ]]; then
        cp -al "$final" "$BACKUP_DIR/weekly/$name" 2> /dev/null || cp -a "$final" "$BACKUP_DIR/weekly/$name"
        log "promoted to weekly/$name"
    fi
    prune
    log "backup done: $final ($(du -sh "$final" | cut -f1))"
}

cmd_list() {
    local kind d
    for kind in daily weekly pre-restore; do
        [[ -d "$BACKUP_DIR/$kind" ]] || continue
        for d in $(ls -1 "$BACKUP_DIR/$kind" | sort -r); do
            printf '%-12s %-18s %6s  %s\n' "$kind" "$d" "$(du -sh "$BACKUP_DIR/$kind/$d" | cut -f1)" \
                "$(grep -E '^mode=' "$BACKUP_DIR/$kind/$d/manifest.txt" 2> /dev/null | cut -d= -f2)"
        done
    done
}

wait_until_stopped() {
    local i
    for ((i = 0; i < 180; i++)); do
        server_running || return 0
        sleep 1
    done
    die "server still answers RCON after 180 s"
}

cmd_restore() {
    local ref="" full=0 stop=0 start=0 yes=0 dir answer s p restore_args=() paths
    for arg in "$@"; do
        case "$arg" in
            --full) full=1 ;; --stop) stop=1 ;; --start) start=1 ;; --yes) yes=1 ;;
            -*) die "unknown option $arg" ;;
            *) [[ -z "$ref" ]] || die "only one backup can be restored"; ref="$arg" ;;
        esac
    done
    [[ -n "$ref" ]] || die "usage: $0 restore <backup> [--full] [--stop] [--start] [--yes]"
    take_lock
    dir="$(resolve_backup "$ref")"
    verify_dir "$dir"
    paths="$(grep -E '^paths=' "$dir/manifest.txt" | cut -d= -f2-)"
    read -r -a RESTORE_PATHS <<< "$paths"
    for p in "${RESTORE_PATHS[@]}"; do [[ "$p" != /* && "$p" != *..* ]] || die "unsafe path in manifest: $p"; done

    if server_running; then
        [[ "$stop" == 1 ]] || die "the server is running: stop it first, or pass --stop"
        if [[ -n "${MC_STOP_CMD:-}" ]]; then log "stopping: $MC_STOP_CMD"; bash -c "$MC_STOP_CMD"
        else log "stopping over RCON"; rcon stop > /dev/null || true; fi
        wait_until_stopped
        [[ -n "${MC_STOP_CMD:-}" ]] || { log "waiting ${STOP_GRACE_SECONDS:-15} s for the process to exit"; sleep "${STOP_GRACE_SECONDS:-15}"; }
    fi

    local scope="game schemas ($GAME_SCHEMAS) of $PG_DATABASE"
    [[ "$full" == 1 ]] && scope="the WHOLE database $PG_DATABASE (website data included)"
    echo "Restore $dir"
    echo "  files:    ${RESTORE_PATHS[*]} in $SERVER_DIR (replaced)"
    echo "  database: $scope (replaced)"
    if [[ "$yes" != 1 ]]; then
        read -r -p "Type RESTORE to continue: " answer
        [[ "$answer" == RESTORE ]] || die "cancelled"
    fi

    local safety; safety="$BACKUP_DIR/pre-restore/$(date +%Y-%m-%d_%H%M%S)"
    log "saving the current state to $safety"
    snapshot_into "$safety" cold

    log "restoring files"
    for p in "${RESTORE_PATHS[@]}"; do rm -rf "${SERVER_DIR:?}/$p"; done
    tar -C "$SERVER_DIR" -xzf "$dir/files.tar.gz"

    if [[ "$full" == 1 ]]; then
        log "recreating database $PG_DATABASE"
        psql_on postgres "DROP DATABASE IF EXISTS \"$PG_DATABASE\" WITH (FORCE)"
        psql_on postgres "CREATE DATABASE \"$PG_DATABASE\" OWNER \"$PG_USER\""
    else
        for s in $GAME_SCHEMAS; do
            log "replacing schema $s"
            psql_on "$PG_DATABASE" "SET client_min_messages = warning; DROP SCHEMA IF EXISTS \"$s\" CASCADE"
            # pg_dump does not emit CREATE SCHEMA public: recreate it; other schemas come from the dump.
            [[ "$s" != public ]] || psql_on "$PG_DATABASE" "CREATE SCHEMA public AUTHORIZATION \"$PG_USER\""
            restore_args+=(-n "$s")
        done
    fi
    log "pg_restore"
    compose exec -T "$PG_SERVICE" pg_restore -U "$PG_USER" -d "$PG_DATABASE" --no-owner --exit-on-error \
        "${restore_args[@]}" < "$dir/database.dump"
    prune_kind pre-restore "$KEEP_PRE_RESTORE"
    log "restore done (previous state kept in $safety)"

    if [[ "$start" == 1 ]]; then
        [[ -n "${MC_START_CMD:-}" ]] || die "--start needs MC_START_CMD"
        log "starting: $MC_START_CMD"; bash -c "$MC_START_CMD"
    else
        log "start the server when ready"
    fi
}

case "${1:-}" in
    backup)  cmd_backup ;;
    list)    cmd_list ;;
    verify)  [[ -n "${2:-}" ]] || die "usage: $0 verify <backup>"; verify_dir "$(resolve_backup "$2")" ;;
    restore) shift; cmd_restore "$@" ;;
    prune)   take_lock; prune ;;
    *)       sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
