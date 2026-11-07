# Storage: data audit and adapter

Written 2026-11-06. Code: `src/main/java/org/minecraft/atlas/storage/`. Schema: `src/main/resources/db/`.

## 1. Audit: what is configuration, what is data

**Rule:** a value an admin *tunes* (defaults, rates, costs, templates) stays in a shipped `.yml` file. A value the *game produces* while running (players, factions, positions, timers, inventories) is data and goes to the storage backend.

### Stays in YAML (configuration)

| File | Content |
|---|---|
| `config.yml` | `storage` (backend choice), tpa, random teleport, AFK, death-teleport cooldown, resource pack |
| `factions.yml` | home teleport, SP per level, vault default size, skill tiers, crystal tuning |
| `jobs.yml` | quest tuning, task templates, Jokeyrini templates |
| `donjons.yml` | activation/timeout, difficulty, rarity, types, boss drops |
| `homes.yml`, `safezones.yml`, `combats.yml`, `auctions.yml`, `orezones.yml` | timers, limits, default ore weights |
| `shops.yml` | reset interval, items per shop, `job_defaults` (templates copied into new shops) |

### Goes to the backend (runtime data): 13 domains

| Domain | Was | Tables |
|---|---|---|
| FACTIONS | `factions-data.yml > factions` | `factions`, `faction_members`, `faction_allies`, `faction_skill_tiers` |
| CRYSTALS | `factions-data.yml > crystals` | `crystals`, `crystal_claims`, `crystal_chests`, `crystal_protections` |
| JOBS | `jobs-data.yml` | `player_jobs`, `job_cooldowns`, `job_selected_quests`, `jokeyrini_players`, `player_quests`, `player_quest_tasks` |
| DONJONS | `donjons-data.yml` | `donjons`, `donjon_spawn_points`, `donjon_chunks`, `donjon_visits`, `server_state[donjon.last_activation]` |
| HOMES | `homes-data.yml` | `player_homes` |
| SAFEZONES | `safezones-data.yml` | `safezones`, `safezone_chunks`, `safezone_visits` |
| COMBAT | `combats-data.yml` | `combat_timers` |
| BLOCKS | `blocks-data.yml` | `custom_blocks` |
| AUCTIONS | `auctions-data.yml` | `auction_listings` |
| OREZONES | `orezones-data.yml` | `ore_zones`, `ore_zone_weights` |
| SHOPS | `shops.yml > shops` **+** `shops-data.yml` | `shops`, `shop_items`, `shop_rotation`, `server_state[shops.last_reset]` |
| TAGS | `tags-data.yml` | `tags`, `tag_lines` |
| SPAWN | `config.yml > spawn` (written by `/spawn set`) | `server_state[spawn.location]` |

Changes compared with before the adapter:
- **Shops** were config *and* data in `shops.yml`. They are now data. `shops.yml` only keeps the tuning and `job_defaults`. A shop written by hand in the old `shops:` section is imported once at startup, and then the section is emptied.
- **Spawn** was written into `config.yml` by `/spawn set`. It is now data.
- **Crystal upgrade stats** (`hp_bonus`, `regen_bonus`, `claim_capacity`, `spent_skill_points`, `immune_until`) used to live only in the entity PDC, so an unloaded crystal (stub) had default values. They are now stored as well. The PDC still wins when the entity loads.

### Not stored (in memory on purpose)
Pending ally requests and invitations, trade sessions, teleport countdowns and cooldowns (rtp, tpa, home, safe zone, smuggler), death-teleport escalation, the running donjon state (waves, boss damage, players inside), the electrical-creeper hit map, AFK timers. They are short-lived; a restart resets them. Golems, NPCs, holograms and crystals are **entities** (tagged in PDC). The world stores them; storage keeps only what the entities can't carry.

## 2. Architecture

```
managers (in memory, unchanged API)
   │ snapshot()  ─ immutable records (storage/model)  ─▶ Storage.save(domains…)
   │ restore(data) ◀─ DataSet ◀─ DataStore.load()          │ main thread: snapshot
   ▼                                                        ▼ writer thread: one transaction
Storage (facade) ──▶ DataStore (adapter interface)
                       └─ SqlDataStore ──▶ SqlDialect
                                             ├─ SqliteDialect   (Paper ships sqlite-jdbc)
                                             └─ PostgresDialect (driver via AtlasLoader)
YamlDataStore: read-only reader of the legacy *-data.yml, used once by Storage.importYaml
```
YAML is **not** a backend any more (decided 2026-11-07, Open Question 19). `storage.type: yaml` in an old `config.yml` is read as `sqlite` with a warning, so the `*-data.yml` files are imported on the first start.

- **Domains are written whole.** `write(DataSet)` replaces every domain in the set inside **one transaction**: SQL `DELETE` + batched `INSERT … ON CONFLICT DO NOTHING`. FACTIONS and CRYSTALS are always saved together (lesson from known issue §7.10).
- **When data is saved:** right after important actions (`Storage.save(...)` at the old save call sites), every `storage.autosave_minutes` (default 5) for all domains, and synchronously in `onDisable`.
- **Threading:** snapshots are taken on the main thread (items are serialized to Paper bytes there). Writes run on a single `Atlas-Storage` thread. `shutdown()` waits for that thread, then writes everything once more.
- **Safety:** nothing is written before `Storage.markReady()`, i.e. before every manager has restored its data. If the backend can't open or load, the plugin disables itself instead of running with empty state that would later overwrite the database.
- **Unresolved rows:** donjons, homes, tags, custom blocks, the spawn and safe-zone spawns whose world is not loaded (or whose donjon type was removed) are kept as raw rows and written back unchanged, instead of being silently dropped as the YAML loaders did.
- **Items** (vault, crystal chests, auctions, shop items) use `ItemStack.serializeAsBytes` / `serializeItemsAsBytes`. These embed the data version, so the DataFixer upgrades items after a Minecraft update. They are stored as `BLOB`/`BYTEA` (the legacy YAML files had them as Base64 `*_b64` keys, still read by the import).

## 3. Operating it

`config.yml`:
```yaml
storage:
  type: sqlite          # sqlite | postgresql (choose once, no migration between them)
  autosave_minutes: 5
  sqlite: { file: atlas.db }
  postgresql: { host: localhost, port: 5432, database: atlas, username: atlas, password: '', ssl: false }
```
- The password can come from the `ATLAS_DB_PASSWORD` environment variable, which overrides the file.
- **First start on a database:** if the DB has never been initialized (`server_state[storage.initialized]`), every `*-data.yml` is imported in one transaction and then renamed to `*.yml.migrated`. Those files are a backup and are never read again.
- **No way back to YAML**, and **no migration between SQLite and PostgreSQL**: the backend is chosen once.
- **Backup:** `/atlas storage export` (permission `atlas.storage.admin`, console or op) writes the whole data set to a new, portable SQLite file `plugins/atlas/backups/atlas-<yyyyMMdd-HHmmss>.db` (journal mode DELETE, so no `-wal`/`-shm` files). It works from either backend: the in-memory snapshots are taken on the main thread (so unsaved changes are included), then written on the storage thread after any pending save. **Restore** (SQLite): stop the server, replace `atlas.db` (and delete `atlas.db-wal`/`-shm`) with the export, start. A restore brings back the **data only**, not world entities: a crystal whose entity was removed after the export comes back as a stub (no entity). Exports are named to the second, so two exports in the same second fail with "file already exists".
- **Inspecting SQLite:** `plugins/atlas/atlas.db` (WAL mode, so `-wal`/`-shm` files sit next to it). Open it read-only, or with the server stopped.

## 4. Moving to PostgreSQL later

1. Create the database and user; set `storage.type: postgresql` and the connection settings, then restart. `AtlasLoader` downloads `org.postgresql:postgresql` through Paper's Maven mirror (only for this type), and the schema is created by `SchemaMigrator`.
2. There is **no SQLite → PostgreSQL copy** by design (Open Question 19): choose PostgreSQL before the server goes live. A fresh PostgreSQL database only imports legacy `*-data.yml` files.
3. ⚠ **PostgreSQL has not been run yet.** The SQL is portable (no dialect-specific queries; `BOOLEAN`, `BIGINT`, `DOUBLE PRECISION`, `ON CONFLICT DO NOTHING` are valid on both), but it needs a test before production.

## 5. Changing the schema or adding a domain

- **Schema change:** add `db/V2__<name>.sql` and append it to `SchemaMigrator.MIGRATIONS`. Never edit an applied file. Use `${blob}` for binary columns.
- **New domain:** add a record in `storage/model`, a constant in `DataDomain`, a mapper in `sql/*Tables` (the store refuses to start if one is missing; `yaml/*` readers are only needed for data that existed in the legacy files), `snapshot()`/`restore()` in the manager, then `Storage.register` and the restore call in `Atlas.onEnable`.
