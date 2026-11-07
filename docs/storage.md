# Storage: data audit and adapter

Written 2026-11-06. Code: `src/main/java/org/minecraft/hatlas/storage/`. Schema: `src/main/resources/db/`.

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
                                             └─ PostgresDialect (driver via HatlasLoader)
YamlDataStore: read-only reader of the legacy *-data.yml, used once by Storage.importYaml
```
YAML is **not** a backend any more (decided 2026-11-07, Open Question 19). `storage.type: yaml` in an old `config.yml` is read as `sqlite` with a warning, so the `*-data.yml` files are imported on the first start.

- **Domains are written whole.** `write(DataSet)` replaces every domain in the set inside **one transaction**: SQL `DELETE` + batched `INSERT … ON CONFLICT DO NOTHING`. FACTIONS and CRYSTALS are always saved together (lesson from known issue §7.10).
- **When data is saved:** right after important actions (`Storage.save(...)` at the old save call sites), every `storage.autosave_minutes` (default 5) for all domains, and synchronously in `onDisable`.
- **Threading:** snapshots are taken on the main thread (items are serialized to Paper bytes there). Writes run on a single `Hatlas-Storage` thread. `shutdown()` waits for that thread, then writes everything once more.
- **Safety:** nothing is written before `Storage.markReady()`, i.e. before every manager has restored its data. If the backend can't open or load, the plugin disables itself instead of running with empty state that would later overwrite the database.
- **Unresolved rows:** donjons, homes, tags, custom blocks, the spawn and safe-zone spawns whose world is not loaded (or whose donjon type was removed) are kept as raw rows and written back unchanged, instead of being silently dropped as the YAML loaders did.
- **Items** (vault, crystal chests, auctions, shop items) use `ItemStack.serializeAsBytes` / `serializeItemsAsBytes`. These embed the data version, so the DataFixer upgrades items after a Minecraft update. They are stored as `BLOB`/`BYTEA` (the legacy YAML files had them as Base64 `*_b64` keys, still read by the import).

## 3. Operating it

`config.yml`:
```yaml
storage:
  type: postgresql      # postgresql (default, production) | sqlite (dev); choose once, no migration between them
  autosave_minutes: 5
  sqlite: { file: hatlas.db }
  postgresql: { host: localhost, port: 5432, database: hatlas, username: hatlas, password: '', ssl: false }
```
- **PostgreSQL is the default** (since 2026-11-07, M2): a missing `storage.type` means `postgresql`, and `HatlasLoader` loads the driver even on the very first start (before `config.yml` exists). Existing installs keep the type written in their `config.yml`. SQLite stays available for a local dev server (`runServer`) without PostgreSQL.
- **Database unreachable:** Hatlas logs `Storage unavailable — Hatlas is disabled to protect the data.`, then the database it tried (`user@host:port/db`), the root cause, a checklist (server running, database/user exist, password set in `HATLAS_DB_PASSWORD` or the config, flagged `NOT SET` when empty) and the `storage.type: sqlite` hint for dev; the stack trace follows as a separate WARNING. The plugin then disables itself.
- The password can come from the `HATLAS_DB_PASSWORD` environment variable, which overrides the file.
- **First start on a database:** if the DB has never been initialized (`server_state[storage.initialized]`), every `*-data.yml` is imported in one transaction and then renamed to `*.yml.migrated`. Those files are a backup and are never read again.
- **No way back to YAML**, and **no migration between SQLite and PostgreSQL**: the backend is chosen once.
- **Backup:** outside the game, the database and the world **together** (a data-only restore brings crystals back as stubs without their entity). For PostgreSQL: `save-off` → `save-all flush` → `pg_dump -Fc` + world copy → `save-on`; the daily backup/restore script is planned in M2 (`feat/backup-script`). The old `/hatlas storage export` (portable SQLite file) was **removed** on 2026-11-07 (Open Question 25); `/hatlas storage info` stays.
- **Inspecting SQLite:** `plugins/hatlas/hatlas.db` (WAL mode, so `-wal`/`-shm` files sit next to it). Open it read-only, or with the server stopped.

## 4. PostgreSQL

1. Create the database and user, set the connection settings (and `HATLAS_DB_PASSWORD`), then start. `HatlasLoader` downloads `org.postgresql:postgresql` through Paper's Maven mirror (only for this type), and the schema is created by `SchemaMigrator`. An existing install still on `storage.type: sqlite` stays on SQLite until an admin changes it.
2. There is **no SQLite → PostgreSQL copy** by design (Open Question 19): choose PostgreSQL before the server goes live. A fresh PostgreSQL database only imports legacy `*-data.yml` files.
3. ✔ **Tested 2026-11-07** against PostgreSQL 16.10 (local docker-compose below): schema creation, driver download, `*-data.yml` import (same row counts as SQLite), immediate save after a mutation, save at shutdown, reload on the next start without re-import.

### Local PostgreSQL (docker-compose)

`docker/postgres/docker-compose.yml` runs `postgres:16.10-alpine`, bound to `127.0.0.1` only, with a named volume and a `pg_isready` healthcheck.

```bash
cp docker/postgres/.env.example docker/postgres/.env    # set HATLAS_DB_PASSWORD (file is git-ignored)
docker compose -f docker/postgres/docker-compose.yml up -d --wait
# plugins/hatlas/config.yml: storage.type: postgresql, host localhost, port 5432,
# database hatlas, username hatlas; password via the HATLAS_DB_PASSWORD env variable
docker compose -f docker/postgres/docker-compose.yml down      # stop, keep data
docker compose -f docker/postgres/docker-compose.yml down -v   # stop and wipe data
```

To test without touching the shared test server, use the isolated `./gradlew runServer` instance (`./run`, git-ignored): put `eula=true`, a `server.properties` on another port (e.g. 25566, RCON on), packetevents in `run/plugins/`, and a `run/plugins/hatlas/config.yml` with `type: postgresql`. Inspect rows with `docker compose -f docker/postgres/docker-compose.yml exec postgres psql -U hatlas -d hatlas`.

### Schema `hatlas` (since 2026-11-07, M2 `feat/web-read-model`)

Hatlas owns one PostgreSQL schema, `storage.postgresql.schema` (default `hatlas`). Every connection runs `CREATE SCHEMA IF NOT EXISTS hatlas` and `SET search_path TO hatlas`, so the mappers stay unqualified. **An older install** whose tables are in `public` is moved on its first start (`ALTER TABLE public.<t> SET SCHEMA hatlas` for every table named in the `db/V*.sql` files plus the version table, one transaction; log: `Storage: moved N tables from schema public to hatlas`). Nothing else in `public` and no other schema (the website's `web`) is touched. SQLite has no schemas and ignores this.

## 5. Website read model

The website (SvelteKit, M2) reads Hatlas data **only through the `web_*` views** of the `hatlas` schema, never the tables: tables can change between versions, the views are the contract (`db/V2__web_read_model.sql`).

| View | Columns |
|---|---|
| `web_factions` | name, color, description, level, exp, skill_points, vault_gems, owner_uuid, outpost_unlocked, member_count, crystal_count, claim_count |
| `web_faction_members` | faction, player_uuid, role (`OWNER`, `LEADER`, `MODERATOR`, `BANKER`, `MEMBER`) |
| `web_faction_allies` | faction, ally (one row per side that listed the other) |
| `web_crystals` | faction, name, hp, max_hp, outpost, claim_count |
| `web_player_jobs` | player_uuid, job, level, progress, level_up_at (= `(level + 1) × 64`) |
| `web_shops` | name, job |
| `web_shop_items` | shop, tier (`common`/`rare`), item_id, name, material (`minecraft:diamond`), amount, price (ruby gems), on_sale (in the current rotation) |
| `web_ruby_transactions` | every ruby move of the ruby ledger (V3), without coordinates: at, kind, amount, sender/receiver type, id, name, faction, actor, ref. See [`economy.md`](economy.md) |
| `web_ruby_daily` | per UTC day: rubies created, destroyed, transferred, number of transactions |

- **Readable columns:** vault and item contents are Paper item bytes the website cannot read, so the plugin also writes `factions.vault_gems` and `shop_items.material`/`amount`. They are computed in the main-thread snapshot and written with every save (NULL right after the V2 migration, filled by the next save; the shutdown save is enough).
- **Read-only role** `storage.postgresql.web_role` (default `hatlas_web`, empty = off). Hatlas **never creates it** (it needs a password the website holds). If it exists, on every start Hatlas grants it `USAGE` on the schema, revokes everything on the schema's tables/views and grants `SELECT` on each `web_*` view, so a view recreated by a later migration keeps its grant (log: `Storage: role 'hatlas_web' may read N web_* views`). Create it once as an admin, then restart Hatlas:
  ```sql
  CREATE ROLE hatlas_web LOGIN PASSWORD '<secret for the website>';
  ```
  ✔ Checked on the test server: the role reads `hatlas.web_shop_items`, gets `permission denied` on `hatlas.factions` and on `DELETE FROM hatlas.web_shops`.
- **Online players** are not here: `hatlas-proxy` will write a presence table (M2 PR 4). Player **names** are not stored by Hatlas either (UUIDs only): the website resolves them through the presence table or Mojang's profile API.
- **Changing a view:** a later `V<n>` migration does `DROP VIEW` + `CREATE VIEW` (never alter a table a view depends on without recreating the view). Adding columns is backward compatible for the website; removing or renaming one is a breaking change to coordinate with the website.

**Ruby ledger** (V3, `ruby_ledger`): not a domain. It is append-only (`DataStore.appendLedger`, never replaced by a save), written by the storage writer every second. Placeholder `${autoid}` = the dialect's auto-increment key. See [`economy.md`](economy.md).

## 6. Changing the schema or adding a domain

- **Schema change:** add `db/V<n>__<name>.sql` and append it to `SchemaMigrator.MIGRATIONS`. Never edit an applied file. Use `${blob}` for binary columns. PostgreSQL: a new table is created in the `hatlas` schema automatically (search_path). Views that depend on a changed table must be dropped and recreated in the same file.
- **New domain:** add a record in `storage/model`, a constant in `DataDomain`, a mapper in `sql/*Tables` (the store refuses to start if one is missing; `yaml/*` readers are only needed for data that existed in the legacy files), `snapshot()`/`restore()` in the manager, then `Storage.register` and the restore call in `Hatlas.onEnable`.
