# Manual storage scenarios (not run automatically)

These need a hand-made server state that the disposable test world (PostgreSQL, reset to
baseline) cannot provide, so the in-game gate does not run them (only `*.json` directly in a
feature folder are suites).

| Scenario | Manual setup | Note |
|---|---|---|
| `export-restore-after-restart.json` | server stopped, a `/atlas storage export` file copied over `atlas.db` | SQLite only; obsolete once the export is removed (M2) |
| `yaml-fallback-after-restart.json` | `storage.type: yaml` in `config.yml`, expects the SQLite fallback | SQLite fallback only |
| `orphan-crystal-after-restart.json` | a crystal entity whose faction row was removed from the database | needs a DB edit before start |
