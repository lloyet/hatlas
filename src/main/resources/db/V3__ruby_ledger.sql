-- Hatlas storage schema, version 3: the ruby ledger (append-only history of every ruby move).
-- A row moves `amount` gems (a ruby block counts 9) from a sender to a receiver (NULL = none).
--   emitted (new rubies):   MINE, ADMIN_GIVE, UNTRACKED_PICKUP, UNTRACKED_BLOCK   (no sender)
--   burned (gone for good): SHOP_BUY, LOST, ADMIN_CLEAR                           (no receiver)
--   everything else is a transfer, e.g. BLOCK_PLACE player -> faction (a placed ruby block belongs
--   to the faction whose claim holds it) and BLOCK_BREAK faction -> breaker.
-- Party types: PLAYER (id = UUID) or FACTION (id = faction name). *_faction = the faction of a
-- PLAYER party when the row was written. Rubies dropped on the ground stay owned by whoever
-- dropped them until someone else picks them up (PICKUP row) or they disappear (LOST row).
-- ${autoid} is the dialect's auto-increment primary key. Times are epoch milliseconds.

-- Faction that owns a placed ruby block (placed only in its own claim). NULL = an ore, a block
-- placed in creative mode or before the ledger existed.
ALTER TABLE custom_blocks ADD COLUMN owner TEXT;

CREATE TABLE ruby_ledger (
    id               ${autoid},
    at               BIGINT NOT NULL,
    kind             TEXT NOT NULL,
    amount           INTEGER NOT NULL,
    sender_type      TEXT,
    sender_id        TEXT,
    sender_name      TEXT,
    sender_faction   TEXT,
    receiver_type    TEXT,
    receiver_id      TEXT,
    receiver_name    TEXT,
    receiver_faction TEXT,
    actor_uuid       TEXT,                   -- player who caused the move (NULL = server)
    actor_name       TEXT,
    ref              TEXT,                   -- context: ore zone, shop item, listing, drop origin…
    world            TEXT,                   -- where it happened (NULL = no place)
    x                INTEGER,
    y                INTEGER,
    z                INTEGER
);
CREATE INDEX idx_ruby_ledger_at ON ruby_ledger(at);
CREATE INDEX idx_ruby_ledger_sender ON ruby_ledger(sender_id);
CREATE INDEX idx_ruby_ledger_receiver ON ruby_ledger(receiver_id);
CREATE INDEX idx_ruby_ledger_sender_faction ON ruby_ledger(sender_faction);
CREATE INDEX idx_ruby_ledger_receiver_faction ON ruby_ledger(receiver_faction);

-- Website: every transaction, without coordinates (they would reveal bases).
CREATE VIEW web_ruby_transactions AS
SELECT id, at, kind, amount,
       sender_type, sender_id, sender_name, sender_faction,
       receiver_type, receiver_id, receiver_name, receiver_faction,
       actor_uuid, actor_name, ref
  FROM ruby_ledger;

-- Website: the economy per UTC day (day = days since 1970-01-01). Total supply = running sum
-- of emitted - burned. placed / broken = rubies put into / taken out of placed ruby blocks.
CREATE VIEW web_ruby_daily AS
SELECT at / 86400000 AS day,
       SUM(CASE WHEN kind IN ('MINE', 'ADMIN_GIVE', 'UNTRACKED_PICKUP', 'UNTRACKED_BLOCK') THEN amount ELSE 0 END) AS emitted,
       SUM(CASE WHEN kind IN ('SHOP_BUY', 'LOST', 'ADMIN_CLEAR') THEN amount ELSE 0 END) AS burned,
       SUM(CASE WHEN kind = 'BLOCK_PLACE' THEN amount ELSE 0 END) AS placed,
       SUM(CASE WHEN kind = 'BLOCK_BREAK' THEN amount ELSE 0 END) AS broken,
       SUM(CASE WHEN sender_type IS NOT NULL AND receiver_type IS NOT NULL THEN amount ELSE 0 END) AS transferred,
       COUNT(*) AS transactions
  FROM ruby_ledger
 GROUP BY at / 86400000;
