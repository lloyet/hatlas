-- Hatlas storage schema, version 2: read model for the website (M2, feat/web-read-model).
-- The website reads ONLY the web_* views below (PostgreSQL role storage.postgresql.web_role),
-- never the tables: the tables may change, the views are the contract. Portable SQL (SQLite too).
-- Readable columns: item and vault contents are Paper item bytes, so the plugin also writes
-- what the website needs to show (written on every save; NULL until the next save).

ALTER TABLE factions ADD COLUMN vault_gems INTEGER;
ALTER TABLE shop_items ADD COLUMN material TEXT;
ALTER TABLE shop_items ADD COLUMN amount INTEGER;

-- One row per faction member. The owner is stored on the faction row and comes out as OWNER.
CREATE VIEW web_faction_members AS
SELECT f.name AS faction, f.owner_uuid AS player_uuid, 'OWNER' AS role
  FROM factions f
UNION
SELECT m.faction, m.player_uuid, COALESCE(m.role, 'MEMBER') AS role
  FROM faction_members m
  JOIN factions f ON f.name = m.faction
 WHERE m.is_member AND m.player_uuid <> f.owner_uuid;

CREATE VIEW web_factions AS
SELECT f.name,
       f.color,
       f.description,
       f.level,
       f.exp,
       f.skill_points,
       f.vault_gems,
       f.owner_uuid,
       f.outpost_unlocked,
       (SELECT COUNT(*) FROM web_faction_members m WHERE m.faction = f.name) AS member_count,
       (SELECT COUNT(*) FROM crystals c WHERE c.faction = f.name) AS crystal_count,
       (SELECT COUNT(*) FROM crystal_claims cc JOIN crystals c ON c.uuid = cc.crystal_uuid
         WHERE c.faction = f.name) AS claim_count
  FROM factions f;

CREATE VIEW web_faction_allies AS
SELECT faction, ally FROM faction_allies;

CREATE VIEW web_crystals AS
SELECT c.faction,
       c.name,
       c.hp,
       c.max_hp,
       c.outpost,
       (SELECT COUNT(*) FROM crystal_claims cc WHERE cc.crystal_uuid = c.uuid) AS claim_count
  FROM crystals c;

-- level_up_at = job XP needed for the next level ((level + 1) * 64, JobManager).
CREATE VIEW web_player_jobs AS
SELECT player_uuid, job, level, progress, (level + 1) * 64 AS level_up_at
  FROM player_jobs;

CREATE VIEW web_shops AS
SELECT name, job FROM shops;

-- on_sale = in the current weekly rotation. price is in ruby gems (paid from the faction vault).
CREATE VIEW web_shop_items AS
SELECT i.shop,
       i.tier,
       i.item_id,
       i.name,
       i.material,
       i.amount,
       i.price,
       EXISTS (SELECT 1 FROM shop_rotation r
                WHERE r.shop = i.shop AND r.tier = i.tier AND r.item_id = i.item_id) AS on_sale
  FROM shop_items i;
