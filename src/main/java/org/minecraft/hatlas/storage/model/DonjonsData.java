package org.minecraft.hatlas.storage.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Domain DONJONS: donjon instances, player visits and the activation timer. */
public record DonjonsData(List<DonjonRow> donjons, Map<UUID, Set<String>> visits, long lastActivation) {

    public static DonjonsData empty() { return new DonjonsData(List.of(), Map.of(), 0L); }

    /**
     * Block positions use the donjon's world. {@code protectedChunks} are packed chunk keys
     * ({@link ChunkPos#packKey}).
     */
    public record DonjonRow(String id, String type, String name, int level, String rarity, String status,
                            long activationTime, boolean timeoutWarned, String world,
                            BlockPos nametag, BlockPos vault, LocationData teleportSpawn, UUID textDisplay,
                            Map<Integer, BlockPos> spawnPoints, List<Long> protectedChunks) {}
}
