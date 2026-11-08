#!/bin/sh
# Roles and schema web of the Hatlas network (hatlas-webapp CLAUDE.md, Decision 6).
# Runs once by itself on an EMPTY data volume (/docker-entrypoint-initdb.d). Idempotent: on an
# existing database run it by hand (see docker/postgres/README.md). It creates roles and schema
# `web` only; the website's migrations create the web tables and their per-table grants.
#
#   hatlas_webapp_owner  owns schema web (website migrations, MIGRATE_DATABASE_URL)
#   hatlas_webapp        website app: DML on every web table (default privileges), member of hatlas_web
#   hatlas_proxy         Velocity plugin: USAGE on web, no CREATE (grants per table by the migrations)
#   hatlas_hub           hub plugin: USAGE on web (SELECT on web.tickets by the migrations)
#   hatlas_web           read-only on hatlas.web_* views (granted by Hatlas itself at every start)
#
# A role whose password variable is empty is created without a password (cannot log in yet);
# re-run with the variable set to give it one.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${HATLAS_ROLES_DB:-$POSTGRES_DB}" \
  -v owner_pw="${HATLAS_WEBAPP_OWNER_PASSWORD:-}" \
  -v app_pw="${HATLAS_WEBAPP_PASSWORD:-}" \
  -v proxy_pw="${HATLAS_PROXY_DB_PASSWORD:-}" \
  -v hub_pw="${HATLAS_HUB_DB_PASSWORD:-}" \
  -v web_pw="${HATLAS_WEB_DB_PASSWORD:-}" <<'SQL'
-- psql variables are not expanded inside DO blocks: pass the passwords as session settings.
SELECT set_config('hatlas.pw_hatlas_webapp_owner', :'owner_pw', false),
       set_config('hatlas.pw_hatlas_webapp', :'app_pw', false),
       set_config('hatlas.pw_hatlas_proxy', :'proxy_pw', false),
       set_config('hatlas.pw_hatlas_hub', :'hub_pw', false),
       set_config('hatlas.pw_hatlas_web', :'web_pw', false);

DO $$
DECLARE
  r text;
  pw text;
BEGIN
  FOREACH r IN ARRAY ARRAY['hatlas_webapp_owner', 'hatlas_webapp', 'hatlas_proxy', 'hatlas_hub', 'hatlas_web'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE', r);
      RAISE NOTICE 'created role %', r;
    END IF;
    pw := current_setting('hatlas.pw_' || r);
    IF pw <> '' THEN
      EXECUTE format('ALTER ROLE %I PASSWORD %L', r, pw);
    END IF;
  END LOOP;
END
$$;

CREATE SCHEMA IF NOT EXISTS web AUTHORIZATION hatlas_webapp_owner;
ALTER SCHEMA web OWNER TO hatlas_webapp_owner;
REVOKE CREATE ON SCHEMA web FROM PUBLIC;
GRANT USAGE ON SCHEMA web TO hatlas_webapp, hatlas_proxy, hatlas_hub;
ALTER DEFAULT PRIVILEGES FOR ROLE hatlas_webapp_owner IN SCHEMA web
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hatlas_webapp;
ALTER DEFAULT PRIVILEGES FOR ROLE hatlas_webapp_owner IN SCHEMA web
  GRANT USAGE, SELECT ON SEQUENCES TO hatlas_webapp;
GRANT hatlas_web TO hatlas_webapp;
SQL
echo "hatlas roles and schema web ready"
