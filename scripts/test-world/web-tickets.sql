-- Test fixture: the website's ticket table (contract in docs/access.md) with rows for the
-- offline test bots. Applied by scripts/test-server.ps1 after every baseline restore, so the
-- dates are always relative to "now". The real table belongs to the website (schema web).
-- Offline UUIDs = UUID.nameUUIDFromBytes("OfflinePlayer:" + name).
DROP SCHEMA IF EXISTS web CASCADE;
CREATE SCHEMA web;
CREATE TABLE web.tickets (
    id          bigserial   PRIMARY KEY,
    uuid        uuid        NOT NULL,
    valid_until timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX tickets_uuid ON web.tickets (uuid);

-- Tester1: valid (an old expired ticket + a current one: the latest one counts).
INSERT INTO web.tickets (uuid, valid_until) VALUES
    ('5b30590c-7361-31a2-a5a0-ee3cb0717365', now() - interval '40 days'),
    ('5b30590c-7361-31a2-a5a0-ee3cb0717365', now() + interval '30 days');
-- Tester2: expired yesterday.
INSERT INTO web.tickets (uuid, valid_until) VALUES
    ('e4c9cd18-ee96-3574-b06a-e9866b0fa22d', now() - interval '1 day');
-- Tester3 (362bd452-26d9-3b54-8335-8aa4abf72f39): no ticket.
