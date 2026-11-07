# access — login ticket check

`check-command.json` runs on every campaign. It reads the ticket fixture that
`scripts/test-server.ps1 reset` loads into `hatlas_test` (`scripts/test-world/web-tickets.sql`):
Tester1 valid (plus an older expired row), Tester2 expired, Tester3 no ticket.

The test server keeps `access.require_ticket: false` (bots connect directly). The refusal at
login is checked by hand when the check changes, and end to end through Velocity by the
`hatlas-proxy` test stack (Hatlas M2 task 6):

1. `test-server.ps1 reset`, then set `access.require_ticket: true` and
   `renew_url: https://example.test/renew` in `<server>/plugins/hatlas/config.yml`, `restart`.
2. Log `Access: login ticket check ON (web.tickets), operators bypass it`.
3. Tester1 joins. Tester2 is refused with "Your access expired on … Renew it at https://example.test/renew".
   Tester3 is refused with "You have no access ticket for this server."
4. `op Tester3` (console): Tester3 joins (ops bypass).
5. Simulate a database problem: `ALTER TABLE web.tickets RENAME TO tickets_off;` (psql on
   `hatlas_test`), then Tester1 is refused with
   "The access check is unavailable right now" and the log names the error (fails closed).
6. `reset` to restore the default config.
