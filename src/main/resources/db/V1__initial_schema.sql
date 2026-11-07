-- Hatlas storage schema, version 1.
-- Portable between SQLite and PostgreSQL: ${blob} is BLOB (SQLite) or BYTEA (PostgreSQL).
-- UUIDs are stored as TEXT. Times are epoch milliseconds (BIGINT).
-- Child tables reference their parent with ON DELETE CASCADE. There are no foreign keys
-- across domains (e.g. crystals -> factions): each domain is replaced independently.

-- ── Server-wide key/value state (timers, spawn, storage metadata) ────────────────────
CREATE TABLE server_state (
    state_key   TEXT PRIMARY KEY,
    state_value TEXT NOT NULL
);

-- ── FACTIONS ──────────────────────────────────────────────────────────────────────────
CREATE TABLE factions (
    name                 TEXT PRIMARY KEY,
    owner_uuid           TEXT NOT NULL,
    color                TEXT NOT NULL,
    description          TEXT NOT NULL,
    level                INTEGER NOT NULL,
    exp                  INTEGER NOT NULL,
    skill_points         INTEGER NOT NULL,
    outpost_unlocked     BOOLEAN NOT NULL,
    vault_size           INTEGER,            -- NULL = default from factions.yml
    downgrade_mul        INTEGER NOT NULL,
    downgrade_window_end BIGINT NOT NULL,
    vault_contents       ${blob}             -- Paper item bytes, NULL = empty
);

CREATE TABLE faction_members (
    faction     TEXT NOT NULL REFERENCES factions(name) ON DELETE CASCADE,
    player_uuid TEXT NOT NULL,
    is_member   BOOLEAN NOT NULL,            -- false = role entry only
    role        TEXT,                        -- NULL = MEMBER
    PRIMARY KEY (faction, player_uuid)
);
CREATE INDEX idx_faction_members_player ON faction_members(player_uuid);

CREATE TABLE faction_allies (
    faction TEXT NOT NULL REFERENCES factions(name) ON DELETE CASCADE,
    ally    TEXT NOT NULL,
    PRIMARY KEY (faction, ally)
);

CREATE TABLE faction_skill_tiers (
    faction    TEXT NOT NULL REFERENCES factions(name) ON DELETE CASCADE,
    skill      TEXT NOT NULL,                -- 'homes' | 'vault'
    tier_index INTEGER NOT NULL,
    PRIMARY KEY (faction, skill, tier_index)
);

-- ── CRYSTALS ──────────────────────────────────────────────────────────────────────────
CREATE TABLE crystals (
    uuid               TEXT PRIMARY KEY,     -- ender crystal entity UUID
    faction            TEXT NOT NULL,
    name               TEXT NOT NULL,
    hp                 DOUBLE PRECISION NOT NULL,
    max_hp             DOUBLE PRECISION NOT NULL,
    outpost            BOOLEAN NOT NULL,
    home_world         TEXT,
    home_x             DOUBLE PRECISION,
    home_y             DOUBLE PRECISION,
    home_z             DOUBLE PRECISION,
    home_yaw           REAL,
    home_pitch         REAL,
    hp_bonus           DOUBLE PRECISION,     -- upgrade stats, NULL = unknown (PDC only)
    regen_bonus        DOUBLE PRECISION,
    claim_capacity     INTEGER,
    spent_skill_points INTEGER,
    immune_until       BIGINT
);
CREATE INDEX idx_crystals_faction ON crystals(faction);

CREATE TABLE crystal_claims (
    crystal_uuid TEXT NOT NULL REFERENCES crystals(uuid) ON DELETE CASCADE,
    world        TEXT NOT NULL,
    chunk_x      INTEGER NOT NULL,
    chunk_z      INTEGER NOT NULL,
    PRIMARY KEY (crystal_uuid, world, chunk_x, chunk_z)
);
CREATE INDEX idx_crystal_claims_chunk ON crystal_claims(world, chunk_x, chunk_z);

CREATE TABLE crystal_chests (
    crystal_uuid TEXT NOT NULL REFERENCES crystals(uuid) ON DELETE CASCADE,
    chest_index  INTEGER NOT NULL,
    size         INTEGER,                    -- NULL = contents without a purchase
    contents     ${blob},
    PRIMARY KEY (crystal_uuid, chest_index)
);

CREATE TABLE crystal_protections (
    crystal_uuid TEXT NOT NULL REFERENCES crystals(uuid) ON DELETE CASCADE,
    duration_ms  BIGINT NOT NULL,
    purchased    BOOLEAN NOT NULL,
    broken_at    BIGINT,                     -- NULL = intact
    PRIMARY KEY (crystal_uuid, duration_ms)
);

-- ── JOBS (jobs, daily quests, Jokeyrini) ─────────────────────────────────────────────
CREATE TABLE player_jobs (
    player_uuid      TEXT PRIMARY KEY,
    job              TEXT NOT NULL,
    level            INTEGER NOT NULL,
    progress         INTEGER NOT NULL,
    last_daily_reset BIGINT NOT NULL
);

CREATE TABLE job_cooldowns (
    player_uuid TEXT PRIMARY KEY,
    until_ms    BIGINT NOT NULL
);

CREATE TABLE job_selected_quests (
    player_uuid TEXT NOT NULL,
    position    INTEGER NOT NULL,
    quest_id    TEXT NOT NULL,
    PRIMARY KEY (player_uuid, position)
);

CREATE TABLE jokeyrini_players (
    player_uuid      TEXT PRIMARY KEY,
    last_daily_reset BIGINT NOT NULL
);

-- source: JOB_ACTIVE | JOB_OFFER | JOKEYRINI_ACTIVE | JOKEYRINI_OFFER
CREATE TABLE player_quests (
    player_uuid TEXT NOT NULL,
    source      TEXT NOT NULL,
    slot        INTEGER NOT NULL,
    quest_id    TEXT NOT NULL,
    expires_at  BIGINT NOT NULL,             -- 0 for offers
    PRIMARY KEY (player_uuid, source, slot)
);

CREATE TABLE player_quest_tasks (
    player_uuid    TEXT NOT NULL,
    source         TEXT NOT NULL,
    slot           INTEGER NOT NULL,
    position       INTEGER NOT NULL,
    task_id        TEXT NOT NULL,
    name           TEXT NOT NULL,
    description    TEXT NOT NULL,
    action_type    TEXT NOT NULL,
    targets        TEXT NOT NULL,            -- comma-separated material/entity names
    difficulty     INTEGER NOT NULL,
    amount         INTEGER NOT NULL,
    time_limit_ms  BIGINT NOT NULL,
    exp_multiplier DOUBLE PRECISION NOT NULL,
    rewards        TEXT NOT NULL,            -- "MATERIAL:amount,MATERIAL:amount"
    progress       INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, source, slot, position),
    FOREIGN KEY (player_uuid, source, slot) REFERENCES player_quests(player_uuid, source, slot) ON DELETE CASCADE
);

-- ── DONJONS ───────────────────────────────────────────────────────────────────────────
CREATE TABLE donjons (
    id                TEXT PRIMARY KEY,
    type              TEXT NOT NULL,
    name              TEXT NOT NULL,
    level             INTEGER NOT NULL,
    rarity            TEXT NOT NULL,
    status            TEXT NOT NULL,
    activation_time   BIGINT NOT NULL,
    timeout_warned    BOOLEAN NOT NULL,
    world             TEXT NOT NULL,
    nametag_x         INTEGER,
    nametag_y         INTEGER,
    nametag_z         INTEGER,
    vault_x           INTEGER,
    vault_y           INTEGER,
    vault_z           INTEGER,
    tp_x              DOUBLE PRECISION,
    tp_y              DOUBLE PRECISION,
    tp_z              DOUBLE PRECISION,
    tp_yaw            REAL,
    tp_pitch          REAL,
    text_display_uuid TEXT
);

CREATE TABLE donjon_spawn_points (
    donjon_id TEXT NOT NULL REFERENCES donjons(id) ON DELETE CASCADE,
    point_id  INTEGER NOT NULL,
    x         INTEGER NOT NULL,
    y         INTEGER NOT NULL,
    z         INTEGER NOT NULL,
    PRIMARY KEY (donjon_id, point_id)
);

CREATE TABLE donjon_chunks (
    donjon_id TEXT NOT NULL REFERENCES donjons(id) ON DELETE CASCADE,
    chunk_x   INTEGER NOT NULL,
    chunk_z   INTEGER NOT NULL,
    PRIMARY KEY (donjon_id, chunk_x, chunk_z)
);

CREATE TABLE donjon_visits (
    player_uuid TEXT NOT NULL,
    donjon_id   TEXT NOT NULL,                -- no FK: visits survive a donjon re-create
    PRIMARY KEY (player_uuid, donjon_id)
);

-- ── HOMES ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE player_homes (
    player_uuid TEXT NOT NULL,
    name        TEXT NOT NULL,
    position    INTEGER NOT NULL,
    world       TEXT NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL NOT NULL,
    pitch       REAL NOT NULL,
    PRIMARY KEY (player_uuid, name)
);

-- ── SAFE ZONES ────────────────────────────────────────────────────────────────────────
CREATE TABLE safezones (
    name        TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    spawn_world TEXT,
    spawn_x     DOUBLE PRECISION,
    spawn_y     DOUBLE PRECISION,
    spawn_z     DOUBLE PRECISION,
    spawn_yaw   REAL,
    spawn_pitch REAL
);

CREATE TABLE safezone_chunks (
    safezone TEXT NOT NULL REFERENCES safezones(name) ON DELETE CASCADE,
    world    TEXT NOT NULL,
    chunk_x  INTEGER NOT NULL,
    chunk_z  INTEGER NOT NULL,
    PRIMARY KEY (safezone, world, chunk_x, chunk_z)
);

CREATE TABLE safezone_visits (
    player_uuid TEXT NOT NULL,
    safezone    TEXT NOT NULL,
    PRIMARY KEY (player_uuid, safezone)
);

-- ── COMBAT LOG ────────────────────────────────────────────────────────────────────────
CREATE TABLE combat_timers (
    player_uuid TEXT PRIMARY KEY,
    until_ms    BIGINT NOT NULL
);

-- ── CUSTOM BLOCKS ─────────────────────────────────────────────────────────────────────
CREATE TABLE custom_blocks (
    world    TEXT NOT NULL,
    x        INTEGER NOT NULL,
    y        INTEGER NOT NULL,
    z        INTEGER NOT NULL,
    block_id TEXT NOT NULL,
    PRIMARY KEY (world, x, y, z)
);

-- ── AUCTION HOUSE ─────────────────────────────────────────────────────────────────────
CREATE TABLE auction_listings (
    id          TEXT PRIMARY KEY,
    seller_uuid TEXT NOT NULL,
    faction     TEXT NOT NULL,               -- '' = orphan (pre-faction-field listing)
    item        ${blob} NOT NULL,
    price       INTEGER NOT NULL,
    description TEXT NOT NULL,
    created_at  BIGINT NOT NULL
);
CREATE INDEX idx_auction_listings_seller ON auction_listings(seller_uuid);

-- ── ORE ZONES ─────────────────────────────────────────────────────────────────────────
CREATE TABLE ore_zones (
    name  TEXT PRIMARY KEY,
    world TEXT,                              -- NULL = no bounds yet
    min_x INTEGER NOT NULL,
    min_y INTEGER NOT NULL,
    min_z INTEGER NOT NULL,
    max_x INTEGER NOT NULL,
    max_y INTEGER NOT NULL,
    max_z INTEGER NOT NULL
);

CREATE TABLE ore_zone_weights (
    zone     TEXT NOT NULL REFERENCES ore_zones(name) ON DELETE CASCADE,
    ore      TEXT NOT NULL,
    weight   INTEGER NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (zone, ore)
);

-- ── SHOPS ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE shops (
    name      TEXT PRIMARY KEY,
    job       TEXT NOT NULL,
    npc_world TEXT,
    npc_x     DOUBLE PRECISION,
    npc_y     DOUBLE PRECISION,
    npc_z     DOUBLE PRECISION,
    npc_yaw   REAL,
    npc_pitch REAL,
    npc_uuid  TEXT
);

CREATE TABLE shop_items (
    shop     TEXT NOT NULL REFERENCES shops(name) ON DELETE CASCADE,
    tier     TEXT NOT NULL,                  -- 'common' | 'rare'
    item_id  TEXT NOT NULL,
    name     TEXT NOT NULL,
    item     ${blob} NOT NULL,
    price    INTEGER NOT NULL,
    weight   INTEGER NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (shop, tier, item_id)
);

CREATE TABLE shop_rotation (
    shop     TEXT NOT NULL,
    tier     TEXT NOT NULL,
    position INTEGER NOT NULL,
    item_id  TEXT NOT NULL,
    PRIMARY KEY (shop, tier, position)
);

-- ── TAGS (holograms) ──────────────────────────────────────────────────────────────────
CREATE TABLE tags (
    name              TEXT PRIMARY KEY,
    world             TEXT NOT NULL,
    x                 DOUBLE PRECISION NOT NULL,
    y                 DOUBLE PRECISION NOT NULL,
    z                 DOUBLE PRECISION NOT NULL,
    text_display_uuid TEXT
);

CREATE TABLE tag_lines (
    tag     TEXT NOT NULL REFERENCES tags(name) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    text    TEXT NOT NULL,
    PRIMARY KEY (tag, line_no)
);
