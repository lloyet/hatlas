package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small domains grouped in one file: combat, custom blocks, auctions, ore zones, tags, spawn. */
public final class MiscData {

    private MiscData() {}

    /** Domain COMBAT: player → in-combat-until (epoch ms). */
    public record CombatData(Map<UUID, Long> inCombatUntil) {
        public static CombatData empty() { return new CombatData(Map.of()); }
    }

    /** Domain BLOCKS: placed custom blocks (note-block trick). */
    public record BlocksData(List<CustomBlock> blocks) {
        public static BlocksData empty() { return new BlocksData(List.of()); }
    }

    public record CustomBlock(BlockPos pos, String blockId) {}

    /** Domain AUCTIONS: auction-house listings. */
    public record AuctionsData(List<Listing> listings) {
        public static AuctionsData empty() { return new AuctionsData(List.of()); }
    }

    /** @param item Paper item bytes ({@code ItemStack.serializeAsBytes}) */
    public record Listing(UUID id, UUID seller, String faction, byte[] item, int price,
                          String description, long createdAt) {}

    /** Domain OREZONES: refill regions and their ore weights. */
    public record OreZonesData(List<OreZoneRow> zones) {
        public static OreZonesData empty() { return new OreZonesData(List.of()); }
    }

    /** @param world null when the zone has no bounds yet */
    public record OreZoneRow(String name, String world, int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ, List<OreWeight> weights) {}

    public record OreWeight(String ore, int weight) {}

    /** Domain TAGS: text-display holograms. */
    public record TagsData(List<TagRow> tags) {
        public static TagsData empty() { return new TagsData(List.of()); }
    }

    public record TagRow(String name, LocationData location, List<String> lines, UUID textDisplay) {}

    /** Domain SPAWN: the custom spawn set with /spawn set (null = world spawn). */
    public record SpawnData(LocationData spawn) {
        public static SpawnData empty() { return new SpawnData(null); }
    }
}
