package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.UUID;

/** Domain CRYSTALS: every Hatlas Crystal with its claims, chests and protections. */
public record CrystalsData(List<CrystalRow> crystals) {

    public static CrystalsData empty() { return new CrystalsData(List.of()); }

    /**
     * Upgrade stats ({@code hpBonus} … {@code immuneUntil}) are also kept on the entity PDC.
     * They are null when unknown (data imported from YAML, where only the PDC had them).
     */
    public record CrystalRow(UUID uuid, String faction, String name, double hp, double maxHp,
                             boolean outpost, LocationData home,
                             Double hpBonus, Double regenBonus, Integer claimCapacity,
                             Integer spentSkillPoints, Long immuneUntil,
                             List<ChunkPos> claims, List<Chest> chests,
                             List<Protection> protections) {}

    /**
     * @param size     purchased size, null if the index holds contents but no purchase
     * @param contents Paper item bytes, null if empty
     */
    public record Chest(int index, Integer size, byte[] contents) {}

    /** @param brokenAt epoch ms when it broke, null if intact */
    public record Protection(long durationMs, boolean purchased, Long brokenAt) {}
}
