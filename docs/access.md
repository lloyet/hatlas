# Paid access: tickets (M2)

Players buy a monthly **ticket** on the website. It gives access to the faction server.
Three parts check or write it:

| Part | Role |
|---|---|
| Website (SvelteKit, schema `web`) | Owns the `web` schema. Stripe webhook → writes `web.tickets`. Redeems `/link` codes. |
| `hatlas-proxy` (Velocity, repo `../hatlas-proxy`) | The real gate: hub → faction server needs a valid ticket; expiry while playing → warning, then back to the hub. Writes `web.link_codes` and presence. |
| Hatlas (this plugin) | **Defence in depth:** refuses a login that reaches the faction server without a valid ticket (`access.require_ticket`). Reads only. |

## Ticket contract (`web.tickets`)

Hatlas and `hatlas-proxy` read the table with the same rule. The website may add any column,
but it must keep these:

| Column | Type | Meaning |
|---|---|---|
| `uuid` | `uuid` | Minecraft UUID the ticket belongs to (Open Question 24: a ticket belongs to the UUID, not to the web account). |
| `valid_until` | `timestamptz` | End of access. |

- Any number of rows per player: one per purchase, or one row the website extends. **A player
  has access while `max(valid_until) > now()`**, compared with the **database** clock:
  ```sql
  SELECT max(valid_until), now() FROM web.tickets WHERE uuid = $1;
  ```
- A refund or chargeback revokes the ticket by setting `valid_until` to now (or deleting the row).
- An index on `uuid` keeps the check fast.

## Hatlas configuration (`config.yml > access`)

| Key | Default | |
|---|---|---|
| `require_ticket` | `false` | Turn on in production. Off on development and test servers (bots connect directly). |
| `web_schema` | `web` | Schema that holds `tickets`. |
| `renew_url` | `''` | Clickable link in the refusal message. |
| `ops_bypass` | `true` | Server operators always get in. |

Behaviour, once `require_ticket: true`:
- The check runs at login (`AsyncPlayerPreLoginEvent`, off the main thread) on its own
  database connection.
- No ticket: "You have no access ticket for this server." Expired: "Your access expired on
  <date UTC>." Both add "Renew it at <renew_url>".
- **Fails closed:** if the query fails (database down, table missing, no permission), the login
  is refused with "The access check is unavailable right now". The log gets the cause.
- It needs `storage.type: postgresql`. On SQLite every login is refused (except operators), with
  an error in the log.
- Only at login: expiry during a session is handled by `hatlas-proxy` (back to the hub).

`/hatlas access check <player|uuid>` (permission `hatlas.access.admin`, op) shows a player's
ticket as the check sees it, and whether the login check is on.

## Production setup

The Hatlas database user needs read access to the ticket table. Run it as the owner of the
`web` schema, after the website created it:

```sql
GRANT USAGE ON SCHEMA web TO hatlas;
GRANT SELECT ON web.tickets TO hatlas;
```

Then set `access.require_ticket: true` and `renew_url`, and restart.

## Tests

- Test server: `scripts/test-server.ps1 reset` loads `scripts/test-world/web-tickets.sql`
  (Tester1 valid, Tester2 expired, Tester3 none). Suite `src/test/ingame/access/`.
- The full chain through Velocity (login, `/link`, ticket gate, expiry, presence) is tested on the
  `hatlas-proxy` test stack with this jar on its faction server (M2 task 6).
