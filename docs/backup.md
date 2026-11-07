# Backups (database + world)

Decided 2026-11-07 (Open Question 25, M2): a **daily** backup of the PostgreSQL database
**and** the world, taken together and restored together, kept **on the same machine** in a
dedicated folder for now. Up to 24 h of play can be lost. Production runs on **Linux** with
PostgreSQL in **docker compose** (✔ 2026-11-07).

Why together: Hatlas keeps part of its state in the world (crystal entities, NPCs, ruby note
blocks) and part in the database (factions, crystal HP and claims, vaults…). Restoring only
one side gives crystals without entities (stubs), or entities whose faction no longer exists.

Files: `scripts/backup/hatlas-backup.sh` (the tool), `rcon.py` (stdlib RCON client),
`backup.env.example` (settings), `systemd/hatlas-backup.{service,timer}` (schedule).

## 1. What a backup is

```
$BACKUP_DIR/
  daily/2026-11-08_043012/     database.dump  files.tar.gz  manifest.txt  SHA256SUMS
  weekly/2026-11-08_043012/    (hard links to a daily one, promoted once every 6+ days)
  pre-restore/<timestamp>/     the state just before each restore (undo)
```

- `database.dump`: `pg_dump -Fc` of the whole database (game **and** website schemas).
- `files.tar.gz`: `BACKUP_PATHS` under `SERVER_DIR` (default `world world_nether world_the_end plugins/hatlas`), without `session.lock`.
- `manifest.txt`: date, host, `mode=hot|cold`, paths, database. `SHA256SUMS`: both archives.

**Hot backup** (server running), in this order: `save-off` → `save-all flush` →
`hatlas storage save` (Hatlas writes every domain and waits until the database has it) →
`pg_dump` + `tar` → `save-on`. While saves are off the game keeps running; only disk writes
of chunks are paused. `save-on` is also sent if anything fails. **Cold backup** (server
stopped): dump + tar only. Each backup is verified (checksums, `pg_restore --list`, `tar -t`)
before it counts.

Rotation: `KEEP_DAILY=7`, `KEEP_WEEKLY=4`, `KEEP_PRE_RESTORE=3`. A lock (`$BACKUP_DIR/.lock`)
stops two runs at once; an unfinished run leaves only `.incomplete-*`, removed next time.

## 2. Installing on the server (Linux)

```bash
sudo mkdir -p /etc/hatlas /srv/hatlas-backups
sudo cp scripts/backup/backup.env.example /etc/hatlas/backup.env
sudo chmod 600 /etc/hatlas/backup.env && sudo chown minecraft: /etc/hatlas/backup.env
sudoedit /etc/hatlas/backup.env           # SERVER_DIR, BACKUP_DIR, COMPOSE_FILE, MC_RCON_PASSWORD…
sudo chown minecraft: /srv/hatlas-backups
sudo usermod -aG docker minecraft         # or DOCKER="sudo docker"

# check by hand once
sudo -u minecraft /opt/hatlas/scripts/backup/hatlas-backup.sh backup
sudo -u minecraft /opt/hatlas/scripts/backup/hatlas-backup.sh list

# schedule (edit User= and the script path in the unit first)
sudo cp scripts/backup/systemd/hatlas-backup.{service,timer} /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hatlas-backup.timer
systemctl list-timers hatlas-backup.timer
journalctl -u hatlas-backup.service       # logs of the runs
```

Needs: `enable-rcon=true` and `rcon.password` in `server.properties` (RCON bound to
localhost only, never exposed), `python3`, GNU `tar`/coreutils, `docker compose`.

## 3. Restoring

```bash
hatlas-backup.sh list
hatlas-backup.sh verify 2026-11-08_043012
hatlas-backup.sh restore 2026-11-08_043012 --stop            # asks to type RESTORE
hatlas-backup.sh restore 2026-11-08_043012 --stop --start --yes
```

1. Verifies the backup, then needs the server stopped: `--stop` runs `MC_STOP_CMD` (e.g.
   `sudo systemctl stop minecraft-faction`) or, without it, sends `stop` over RCON, waits
   until RCON is gone and `STOP_GRACE_SECONDS` (15) more.
2. Saves the current state into `pre-restore/<timestamp>` (cold backup), so a restore can be undone by restoring that one.
3. Replaces the backed-up paths, then the database:
   - **default**: only `GAME_SCHEMAS` (default `public`, later `hatlas`) are dropped and
     restored from the dump. The website's data (accounts, links, **tickets and payments**
     in schema `web`) is left untouched, so a game rollback never undoes a purchase.
   - **`--full`**: drops and recreates the whole database (disaster recovery, e.g. a lost
     volume). Website data goes back to the backup time too.
4. `--start` runs `MC_START_CMD`; otherwise start the server yourself.

The PostgreSQL user must be allowed to drop/create schemas and databases (the docker image's
`POSTGRES_USER` is a superuser).

## 4. Limits / later

- Same disk as the server: protects against mistakes, griefing and bad updates, **not**
  against losing the machine. Next step: copy `$BACKUP_DIR` off-site (rsync/restic) once there
  is a place for it.
- Hatlas keeps writing to the database during the few seconds of `tar`; those writes are
  saved by the next autosave/backup. The dump itself is one consistent snapshot.
- The test server (`scripts/test-server.ps1`) keeps its own `baseline`/`reset`: same idea
  (worlds + `plugins/hatlas` + `pg_dump -Fc`), but it restores a fixed reference state.
