# Ruby economy and the ruby ledger

Rubies are the only currency. The **ruby ledger** (table `ruby_ledger`, schema V3) records every
ruby move, so the economy can be traced per player, per faction and over time. It is
append-only: rows are never changed or deleted.

## Where rubies come from and go

| Kind | Sender → receiver | When |
|---|---|---|
| `MINE` | — → player | A ruby ore (natural, in an ore zone, or placed) broken with an iron+ pickaxe: 1 gem. **The only natural source** of rubies. `ref` = the ore zone. |
| `ADMIN_CLEAR` | player → — | The vanilla `/clear` removed rubies (measured one tick after the command): burned. |
| `ADMIN_GIVE` | — → player | `/item give` of rubies or ruby blocks (ores count 0 until mined), or the vanilla `/give` of an item with the `hatlas:ruby` model (measured on each target one tick after the command). `ref` = who gave them. |
| `BLOCK_PLACE` | player → faction | A ruby block can only be placed in the placer's own claim, so a placed block **belongs to that faction** (9 gems, owner stored with the block). |
| `BLOCK_BREAK` | faction → player | A placed block broken with an iron+ pickaxe: its 9 gems go from the owning faction to the breaker (a member, or a raider). Still true after the faction lost the claim or was disbanded: the row names the former owner. |
| `UNTRACKED_BLOCK` | — → player | A block with no owner was broken (placed in creative mode, or before the ledger existed): counted as emitted. |
| `VAULT_DEPOSIT` | player → faction | Gems put into the faction vault. |
| `VAULT_WITHDRAW` | faction → player | Gems taken out (owner / BANKER+). |
| `SHOP_BUY` | faction → — | Paid to a job shop: rubies leave the economy (the main sink). `ref = shop:<name>/<tier>/<item>`. |
| `AUCTION_BUY` | faction → faction | Paid to the seller's faction. `ref = listing:<id>`. |
| `TRADE` | player → player | Rubies given in a completed trade window (one row per side that gave rubies; a cancelled trade writes nothing). |
| `PICKUP` | owner → player | Someone picked up rubies another party dropped. `ref` = why they were dropped: `DROP` (thrown), `DEATH`, `COMBAT_LOG`, `PROTECTION_BREAK`, `DISBAND` (crystal losses), `AUCTION_OVERFLOW`, `MINE`, `BLOCK_BREAK`, `ADMIN_GIVE`. |
| `LOST` | owner → — | Rubies destroyed. `ref` = cause: `DESPAWN`, `PLUGIN` (item clear), `DEATH` (lava, fire, cactus, explosion), `OUT_OF_WORLD`; `BROKEN_WITHOUT_PICKAXE` = a placed ruby block broken without a valid pickaxe (nothing drops)…; `DISBAND` = gems still in the vault when the owner disbands the faction (`/f disband` deletes them; a crystal loss drops them instead). |
| `UNTRACKED_PICKUP` | — → player | Rubies of unknown origin picked up. Should not happen; a row means a leak to fix. |

— = no party. Amounts are in gems (a ruby block = 9). Crafting gems ↔ blocks changes nothing.

**Emitted and burned are counted by kind** (`web_ruby_daily`, `/hatlas economy supply`):
- **emitted** (new rubies): `MINE`, `ADMIN_GIVE`, `UNTRACKED_PICKUP`, `UNTRACKED_BLOCK`;
- **burned** (gone for good): `SHOP_BUY`, `LOST`, `ADMIN_CLEAR`;
- everything else is a transfer. Total supply = emitted − burned. `/hatlas economy supply` also
  shows how much of it sits in placed ruby blocks (counted from the blocks themselves).

**Crafting writes nothing** (✔ 2026-11-07): 9 gems ↔ 1 block keeps the value (a block counts 9) and
the owner (the same player), so it is neither a burn nor an emission. Recording it as burn +
emit would inflate both totals every time someone compacts their rubies.

**Placed ruby blocks behave like dropped rubies** with the faction as owner: an explosion drops the
block still owned by the faction (`ref = EXPLOSION`), and whoever picks it up gets a `PICKUP` from
that faction.

**Rubies on the ground keep their owner.** Hatlas tags every dropped ruby stack with the party it
belongs to (a player or a faction) and the reason. Nothing is written when it drops: a pickup
by someone else writes `PICKUP` (e.g. "faction Red → raider, 12, PROTECTION_BREAK"), and a
disappearance writes `LOST`. Picking up your own drop writes nothing. Stacks of different
owners never merge, and mobs cannot pick up rubies.

Rubies cannot move any other way: they are refused in every container except the vault, the
crafting grids and the trade window (`RubyRestrictionListener`; the trade window moves items
itself, recorded as `TRADE`).

### Not tracked
- Creative mode (creative menu, pick block): admins only (a block placed in creative mode has no owner, see `UNTRACKED_BLOCK`).
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
`economy/Party`, `economy/LedgerKind`, `economy/AdminCommandListener` (vanilla `/give`, `/clear`).
Block owners: `RubyBlockManager.setOwner/ownerAt`, column `custom_blocks.owner` (V3). Calls at each move: `BlockListener` (mine, place, break),
`ItemCommand`, `CrystalVaultGui` (deposit/withdraw measured from the vault and the player's
gems one tick after the click), `ShopManager`, `AuctionConfirmGui`, `FactionVault.drop`,
`CombatLogManager`. Storage: `Storage.appendLedger` / `Storage.readAsync`, `LedgerTable`.
