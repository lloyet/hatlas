# Manual storage scenarios (not run automatically)

These need a hand-made server state that the disposable test world (PostgreSQL, reset to
baseline) cannot provide, so the in-game gate does not run them (only `*.json` directly in a
feature folder are suites).

| Scenario | Manual setup | Note |
|---|---|---|
| (no file) PostgreSQL unreachable | `storage.postgresql.port` set to a closed port (or the database stopped) | expect `Storage unavailable — Hatlas is disabled…` followed by the cause, the checklist (server, database/user, `HATLAS_DB_PASSWORD`) and the `storage.type: sqlite` hint, then Hatlas disabled; `reset` afterwards |
| (no file) first start with no `config.yml` | delete `plugins/hatlas/config.yml`, `HATLAS_DB_PASSWORD` set, a `hatlas` database on localhost:5432 | expect the PostgreSQL driver download and `Storage: using PostgreSQL (hatlas@localhost:5432/hatlas)` |
| `yaml-fallback-after-restart.json` | `storage.type: yaml` in `config.yml`, expects the SQLite fallback | SQLite fallback only |
| `orphan-crystal-after-restart.json` | a crystal entity whose faction row was removed from the database | needs a DB edit before start |

## Rename migration (atlas → hatlas)

Checked once on 2026-11-07 with the pre-rename test baseline (plugin `atlas`). To re-check:
1. Server stopped. Put an old install in place: `plugins/atlas/` (config with `storage.type: postgresql`), its world, and its database (`atlas_schema_version` present); only `hatlas-*.jar` in `plugins/`.
2. Start with only `ATLAS_DB_PASSWORD` set (not `HATLAS_DB_PASSWORD`).
3. Expect in the log: `Migration: moved data folder plugins/atlas to plugins/hatlas`, `Migration: renamed table atlas_schema_version to hatlas_schema_version`, `Storage: using PostgreSQL (…)`, `Hatlas enabled.`
4. `data get entity <an Atlas NPC> BukkitValues` shows `hatlas:*` keys only (e.g. `hatlas:job_npc_type`, `hatlas:shop_npc`, `hatlas:smuggler_npc`); a bot right-clicking the shop NPC gets the shop menu.
