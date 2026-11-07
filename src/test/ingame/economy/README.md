# economy — ruby ledger

`ledger-flows.json` covers admin give, vault deposit and withdrawal, a ground drop picked up by
another player, mining a ruby ore, placing and breaking a ruby block, and `/hatlas economy`
(history of a player and of a faction, supply, unknown name, refused without op).
`ledger-persist-before-restart.json` / `ledger-persist-after-restart.json` check that ledger rows
survive a restart. `vault-fast-clicks.json` sends a refused click (ruby block) and a deposit in the
same tick: one `VAULT_DEPOSIT` row is expected (it caught the double count of `CrystalVaultGui.trackMove`).

Checked by hand when the ledger code changes (psql on `hatlas_test`:
`SELECT kind, amount, sender_name, receiver_name, ref FROM hatlas.ruby_ledger ORDER BY id DESC LIMIT 20;`):
- a shop purchase gives `SHOP_BUY` faction → nobody, `ref = shop:<name>/<tier>/<item>` (buy with a funded vault);
- an auction purchase gives `AUCTION_BUY` buyer faction → seller faction (`auction/banker-buy.json`);
- `/kill @e[type=item]` on dropped rubies gives `LOST` owner → nobody (`ref` = the removal cause);
- the item clear (`ItemClearManager`) gives `LOST` with `ref = PLUGIN`;
- a crystal protection break: picking up the dropped gems gives `PICKUP` faction → player, `ref = PROTECTION_BREAK`.
- a placed ruby block blown up (TNT) then picked up by another player gives `PICKUP` faction → player, `ref = EXPLOSION`;
- a placed ruby block broken by hand (no pickaxe) gives `LOST` faction → nobody, `ref = BROKEN_WITHOUT_PICKAXE`.
