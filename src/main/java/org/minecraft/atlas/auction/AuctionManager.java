package org.minecraft.atlas.auction;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.ItemBytes;
import org.minecraft.atlas.storage.Storage;
import org.minecraft.atlas.storage.model.MiscData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory registry of all open auction listings, persisted through {@code Storage}
 * (domain AUCTIONS). Listings have global scope (every player sees the market)
 * and are keyed by their own UUID. Insertion order is preserved so paging stays stable.
 */
public final class AuctionManager {

    private static final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();

    /** Loaded from {@code auctions.yml}. */
    private static int maxListingsPerPlayer = 5;

    private AuctionManager() {}

    public static int getMaxListingsPerPlayer() { return maxListingsPerPlayer; }

    /** Loads tuning values from {@code auctions.yml}. */
    public static void loadConfig(FileConfiguration config) {
        maxListingsPerPlayer = Math.max(1, config.getInt("max_listings_per_player", 5));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public static void addListing(AuctionListing listing) {
        if (listing == null) return;
        listings.put(listing.id(), listing);
        persist();
    }

    /** Removes and returns the listing with the given id, or null. Persists on success. */
    public static AuctionListing removeListing(UUID id) {
        AuctionListing removed = listings.remove(id);
        if (removed != null) persist();
        return removed;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static AuctionListing getListing(UUID id) {
        return listings.get(id);
    }

    /** All listings owned by {@code seller}, in insertion order. */
    public static List<AuctionListing> getBySeller(UUID seller) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.seller().equals(seller)) out.add(l);
        }
        return out;
    }

    /**
     * Market view: every listing whose owning faction still exists, excluding
     * the viewer's own listings. Disbanded-faction listings are hidden here but
     * remain visible (and retrievable) in the seller's own sales view.
     */
    public static List<AuctionListing> getMarketListings(UUID viewer) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.seller().equals(viewer)) continue;
            if (!isFactionAlive(l)) continue;
            out.add(l);
        }
        return out;
    }

    /** True iff {@code listing}'s owning faction still exists. */
    public static boolean isFactionAlive(AuctionListing listing) {
        return !listing.factionName().isEmpty()
                && FactionManager.getFaction(listing.factionName()) != null;
    }

    public static List<AuctionListing> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(listings.values()));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static MiscData.AuctionsData snapshot() {
        List<MiscData.Listing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            byte[] item = ItemBytes.fromItem(l.item());
            if (item == null) continue;
            out.add(new MiscData.Listing(l.id(), l.seller(), l.factionName(), item, l.price(),
                    l.description(), l.createdAtMs()));
        }
        return new MiscData.AuctionsData(List.copyOf(out));
    }

    /** Call after FactionManager.restore (orphan listings fall back to the seller's faction). */
    public static void restore(MiscData.AuctionsData data) {
        listings.clear();
        for (MiscData.Listing l : data.listings()) {
            ItemStack item = ItemBytes.toItem(l.item());
            if (item == null) continue;
            // Migration: pre-faction-field listings fall back to the seller's CURRENT
            // faction at load time. If none, the listing loads as orphan ("") — hidden
            // from market, but still retrievable via /auction sales.
            String factionName = l.faction() == null ? "" : l.faction();
            if (factionName.isEmpty()) {
                String currentFaction = FactionManager.getPlayerFaction(l.seller());
                if (currentFaction != null) factionName = currentFaction;
            }
            listings.put(l.id(), new AuctionListing(l.id(), l.seller(), factionName, item, l.price(),
                    l.description() == null ? "" : l.description(), l.createdAt()));
        }
    }

    private static void persist() {
        Storage.save(DataDomain.AUCTIONS);
    }
}
