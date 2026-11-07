package org.minecraft.hatlas.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One item listed on the auction market. Immutable — to mutate, remove and re-create.
 *
 * <p>{@code factionName} is locked at creation time: the listing belongs to that faction
 * for the lifetime of the listing. If the faction disbands, the listing is "orphaned" —
 * hidden from the market but still retrievable by the original seller via /auction sales.
 *
 * @param id           per-listing UUID (generated at creation, used as the YAML section key)
 * @param seller       UUID of the listing player
 * @param factionName  faction that owns this listing; payment routes to its vault
 * @param item         the item being sold (may carry full data components / NBT)
 * @param price        ruby-gem cost
 * @param description  optional seller-supplied note (empty string if none)
 * @param createdAtMs  epoch ms when the listing was created
 */
public record AuctionListing(UUID id, UUID seller, String factionName, ItemStack item,
                             int price, String description, long createdAtMs) {

    public static AuctionListing create(UUID seller, String factionName, ItemStack item,
                                        int price, String description) {
        return new AuctionListing(UUID.randomUUID(), seller,
                factionName == null ? "" : factionName, item, price,
                description == null ? "" : description, System.currentTimeMillis());
    }
}
