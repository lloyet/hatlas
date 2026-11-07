package org.minecraft.hatlas.economy;

/**
 * Why rubies moved. Stored as text in {@code ruby_ledger.kind}: never rename a value.
 * Sender "—" = rubies created; receiver "—" = rubies leaving the economy.
 */
public enum LedgerKind {
    /** — → player: a ruby ore broken with a valid pickaxe (the only natural source). */
    MINE,
    /** — → player: a placed ruby block broken (9 gems). */
    BLOCK_BREAK,
    /** player → —: a ruby block placed in a claim (9 gems, back with BLOCK_BREAK). */
    BLOCK_PLACE,
    /** — → player: {@code /item give} or vanilla {@code /give} (admin). */
    ADMIN_GIVE,
    /** player → faction: gems put into the vault. */
    VAULT_DEPOSIT,
    /** faction → player: gems taken out of the vault. */
    VAULT_WITHDRAW,
    /** faction → —: paid to a job shop. */
    SHOP_BUY,
    /** faction → faction: paid to the seller's faction on the auction house. */
    AUCTION_BUY,
    /** player → player: rubies given in a completed trade (one row per side that gave rubies). */
    TRADE,
    /** owner → player: someone else picked up dropped rubies (ref = why they were dropped). */
    PICKUP,
    /** — → player: picked up rubies of unknown origin (should not happen; shows a leak). */
    UNTRACKED_PICKUP,
    /** owner → —: dropped rubies disappeared (despawn, item clear, lava, void…). */
    LOST
}
