package org.minecraft.hatlas.economy;

/**
 * Why rubies moved. Stored as text in {@code ruby_ledger.kind}: never rename a value.
 * Sender "—" = rubies created; receiver "—" = rubies leaving the economy.
 */
public enum LedgerKind {
    /** — → player: a ruby ore broken with a valid pickaxe (the only natural source). */
    MINE,
    /** faction → player: a placed ruby block broken with a valid pickaxe (9 gems, the block owner's). */
    BLOCK_BREAK,
    /** player → faction: a ruby block placed in the faction's claim now belongs to the faction (9 gems). */
    BLOCK_PLACE,
    /** — → player: a ruby block placed in creative mode or before the ledger existed was broken. */
    UNTRACKED_BLOCK,
    /** — → player: {@code /item give} or vanilla {@code /give} (admin). */
    ADMIN_GIVE,
    /** player → —: rubies removed by the vanilla {@code /clear} (admin). */
    ADMIN_CLEAR,
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
    /** owner → —: dropped rubies disappeared (despawn, item clear, lava, void…), a ruby block broken
     *  without a valid pickaxe, or a disbanded faction's vault. */
    LOST
}
