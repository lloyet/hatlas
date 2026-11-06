package org.minecraft.atlas.storage.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Domain SAFEZONES: safe zones (chunks, spawn, description) and player visits. */
public record SafeZonesData(List<Zone> zones, Map<UUID, Set<String>> visits) {

    public static SafeZonesData empty() { return new SafeZonesData(List.of(), Map.of()); }

    /** @param spawn null if the zone has no spawn point */
    public record Zone(String name, String description, LocationData spawn, List<ChunkPos> chunks) {}
}
