# Ruby economy and the ruby ledger

Rubies are the only currency. The **ruby ledger** (table `ruby_ledger`, schema V3) records every
ruby move, so the economy can be traced per player, per faction and over time. It is
append-only: rows are never changed or deleted.

## Where rubies come from and go

| Kind | Sender → receiver | When |
|---|---|---|
| `MINE` | — → player | A ruby ore (natural, in an ore zone, or placed) broken with an iron+ pickaxe: 1 gem. **The only natural source** of rubies. `ref` = the ore zone. |
| `ADMIN_GIVE` | — → player | `/item give` of rubies or ruby blocks (ores count 0 until mined), or the vanilla `/give` of an item with the `hatlas:ruby` model (measured on each target one tick after the command). `ref` = who gave them. |
| `BLOCK_PLACE` | player → — | A ruby block placed in a claim takes its 9 gems out of circulation… |
| `BLOCK_BREAK` | — → player | …and breaking it puts 9 back. |
| `VAULT_DEPOSIT` | player → faction | Gems put into the faction vault. |
| `VAULT_WITHDRAW` | faction → player | Gems taken out (owner / BANKER+). |
| `SHOP_BUY` | faction → — | Paid to a job shop: rubies leave the economy (the main sink). `ref = shop:<name>/<tier>/<item>`. |
| `AUCTION_BUY` | faction → faction | Paid to the seller's faction. `ref = listing:<id>`. |
| `TRADE` | player → player | Rubies given in a completed trade window (one row per side that gave rubies; a cancelled trade writes nothing). |
| `PICKUP` | owner → player | Someone picked up rubies another party dropped. `ref` = why they were dropped: `DROP` (thrown), `DEATH`, `COMBAT_LOG`, `PROTECTION_BREAK`, `DISBAND` (crystal losses), `AUCTION_OVERFLOW`, `MINE`, `BLOCK_BREAK`, `ADMIN_GIVE`. |
| `LOST` | owner → — | Dropped rubies disappeared. `ref` = cause: `DESPAWN`, `PLUGIN` (item clear), `DEATH` (lava, fire, cactus, explosion), `OUT_OF_WORLD`…; `DISBAND` = gems still in the vault when the owner disbands the faction (`/f disband` deletes them; a crystal loss drops them instead). |
| `UNTRACKED_PICKUP` | — → player | Rubies of unknown origin picked up. Should not happen; a row means a leak to fix. |

— = no party. Amounts are in gems (a ruby block = 9). Crafting gems ↔ blocks changes nothing.

**Emitted and burned are counted by kind** (`web_ruby_daily`, `/hatlas economy supply`):
- **emitted** (new rubies): `MINE`, `ADMIN_GIVE`, `UNTRACKED_PICKUP`;
- **burned** (gone for good): `SHOP_BUY`, `LOST`;
- **stored in the world**: `BLOCK_PLACE` / `BLOCK_BREAK`. A placed ruby block still exists, so it is
  neither emitted nor burned: total supply = emitted − burned, of which placed − broken sits in
  ruby blocks.

**Rubies on the ground keep their owner.** Hatlas tags every dropped ruby stack with the party it
belongs to (a player or a faction) and the reason. Nothing is written when it drops: a pickup
by someone else writes `PICKUP` (e.g. "faction Red → raider, 12, PROTECTION_BREAK"), and a
disappearance writes `LOST`. Picking up your own drop writes nothing. Stacks of different
owners never merge, and mobs cannot pick up rubies.

Rubies cannot move any other way: they are refused in every container except the vault, the
crafting grids and the trade window (`RubyRestrictionListener`; the trade window moves items
itself, recorded as `TRADE`).

### Not tracked
- Creative mode (creative menu, pick block) and vanilla `/clear`: admins only.
- A `/give` run by another plugin through `Bukkit.dispatchCommand` (no command event); players, the console and RCON are seen.
- A crash loses the rows of the last second (they are written every second and at shutdown),
  like the other data written between saves.

## Table and views

`ruby_ledger`: `id`, `at` (epoch ms), `kind`, `amount`, then for the sender and the receiver
`*_type` (`PLAYER` | `FACTION` | NULL), `*_id` (UUID or faction name), `*_name`, `*_faction` (the
faction of a player party at that moment), `actor_uuid` / `actor_name` (the player who caused
it), `ref`, and `world`, `x`, `y`, `z`. Indexed on `at`, sender/receiver id and faction.

For the website (read-only role, like the other `web_*` views, `docs/storage.md` §5):
- `web_ruby_transactions`: every row **without coordinates** (they would reveal bases).
  Per player: `WHERE sender_id = $uuid OR receiver_id = $uuid`. Per faction: `sender_id`/`receiver_id`
  = faction name for the vault, `sender_faction`/`receiver_faction` for its members.
- `web_ruby_daily`: per UTC day (`day` = days since 1970-01-01): `emitted`, `burned`, `placed`,
  `broken`, `transferred`, `transactions`. Total supply = running sum of `emitted - burned`.

## Admin commands

Permission `hatlas.economy.admin` (op):
- `/hatlas economy history <player|faction> [n]`: the latest `n` (default 10, max 100) moves of a
  party, newest first.
- `/hatlas economy supply`: rubies emitted and burned so far, the supply, and how much of it is in
  placed ruby blocks.

## Code

`economy/RubyLedger` (buffer, flushed by the storage writer every second and before the final
save; drop tagging), `economy/RubyLedgerListener` (throws, deaths, pickups, removals, merges),
`economy/Party`, `economy/LedgerKind`. Calls at each move: `BlockListener` (mine, place, break),
`ItemCommand`, `CrystalVaultGui` (deposit/withdraw measured from the vault and the player's
gems one tick after the click), `ShopManager`, `AuctionConfirmGui`, `FactionVault.drop`,
`CombatLogManager`. Storage: `Storage.appendLedger` / `Storage.readAsync`, `LedgerTable`.
