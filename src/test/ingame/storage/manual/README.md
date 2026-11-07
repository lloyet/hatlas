# Manual storage scenarios (not run automatically)

These need a hand-made server state that the disposable test world (PostgreSQL, reset to
baseline) cannot provide, so the in-game gate does not run them (only `*.json` directly in a
feature folder are suites).

| Scenario | Manual setup | Note |
|---|---|---|
| `export-restore-after-restart.json` | server stopped, a `/hatlas storage export` file copied over `hatlas.db` | SQLite only; obsolete once the export is removed (M2) |
| `yaml-fallback-after-restart.json` | `storage.type: yaml` in `config.yml`, expects the SQLite fallback | SQLite fallback only |
| `orphan-crystal-after-restart.json` | a crystal entity whose faction row was removed from the database | needs a DB edit before start |

## Rename migration (atlas → hatlas)

Checked once on 2026-11-07 with the pre-rename test baseline (plugin `atlas`). To re-check:
1. Server stopped. Put an old install in place: `plugins/atlas/` (config with `storage.type: postgresql`), its world, and its database (`atlas_schema_version` present); only `hatlas-*.jar` in `plugins/`.
2. Start with only `ATLAS_DB_PASSWORD` set (not `HATLAS_DB_PASSWORD`).
3. Expect in the log: `Migration: moved data folder plugins/atlas to plugins/hatlas`, `Migration: renamed table atlas_schema_version to hatlas_schema_version`, `Storage: using PostgreSQL (…)`, `Hatlas enabled.`
4. `data get entity <an Atlas NPC> BukkitValues` shows `hatlas:*` keys only (e.g. `hatlas:job_npc_type`, `hatlas:shop_npc`, `hatlas:smuggler_npc`); a bot right-clicking the shop NPC gets the shop menu.
