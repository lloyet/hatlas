# Local PostgreSQL (Hatlas network)

`docker-compose.yml` runs the PostgreSQL shared by Hatlas (schema `hatlas`), hatlas-proxy and
hatlas-hub (schema `web`, read/write as granted) and hatlas-webapp (owns schema `web`). Development
and testing; production uses the same file on the private server.

```bash
cp docker/postgres/.env.example docker/postgres/.env    # set HATLAS_DB_PASSWORD (+ role passwords)
docker compose -f docker/postgres/docker-compose.yml up -d --wait
```

- Bound to `127.0.0.1` only. Joins the Docker network **`hatlas-db`** (created by this file under that
  fixed name), which the website's `deploy/compose.yml` uses as an external network (host `postgres`).
- `POSTGRES_USER` `hatlas` is the superuser of the container and the role Hatlas uses today.

## Roles (`init/10-roles.sh`, hatlas-webapp Decision 6)

Run by itself on an **empty** volume. Idempotent; on an existing database run it by hand (Git Bash
needs `MSYS_NO_PATHCONV=1`, or use PowerShell):

```bash
docker compose -f docker/postgres/docker-compose.yml exec -T postgres sh /docker-entrypoint-initdb.d/10-roles.sh
```

| Role | Used by | Rights given by the script |
|---|---|---|
| `hatlas_webapp_owner` | website migrations (`MIGRATE_DATABASE_URL`) | owns schema `web` |
| `hatlas_webapp` | website app (`DATABASE_URL`) | DML on every `web` table (default privileges), member of `hatlas_web` |
| `hatlas_proxy` | hatlas-proxy (`HATLAS_PROXY_DB_PASSWORD`) | `USAGE` on `web`, no `CREATE` |
| `hatlas_hub` | hatlas-hub | `USAGE` on `web` |
| `hatlas_web` | read-only website reads | none here: Hatlas grants `SELECT` on `hatlas.web_*` at every start |

Per-table grants (`link_codes` / `presence` to the proxy, `tickets` to the proxy, the hub and
`hatlas`) are made by the website's migrations, which create the `web` tables. Passwords come from the
`HATLAS_*_PASSWORD` variables of `.env`; an empty one leaves the role without a password (cannot log in)
until the script is re-run with it set. Checked 2026-11-08 on a throwaway container: first run, re-run,
`hatlas_proxy` has no `CREATE` on `web`, `hatlas_webapp` gets `INSERT` on a table created by the owner.
