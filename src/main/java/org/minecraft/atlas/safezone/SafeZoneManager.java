package org.minecraft.atlas.safezone;

import org.bukkit.Location;
import org.minecraft.atlas.storage.model.ChunkPos;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.storage.model.SafeZonesData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SafeZoneManager {

    /** name → SafeZone (insertion-ordered for /safezone list). */
    private static final Map<String, SafeZone>      safeZones    = new LinkedHashMap<>();
    /** playerUUID → set of safe zone names the player has visited. */
    private static final Map<UUID, Set<String>>     playerVisits = new ConcurrentHashMap<>();

    // ── Area management ────────────────────────────────────────────────────────

    /** Creates a new safe zone. Returns false if the name is already taken. */
    public static boolean create(String name) {
        String key = name.toLowerCase();
        if (safeZones.containsKey(key)) return false;
        safeZones.put(key, new SafeZone(key));
        return true;
    }

    public static SafeZone get(String name) {
        return name == null ? null : safeZones.get(name.toLowerCase());
    }

    /**
     * Permanently removes a safe zone — its claimed chunks and spawn point go away with it.
     * Visit history for the zone is also cleared. Returns true if a zone was removed.
     */
    public static boolean delete(String name) {
        if (name == null) return false;
        String key = name.toLowerCase();
        SafeZone removed = safeZones.remove(key);
        if (removed == null) return false;
        for (Set<String> visits : playerVisits.values()) visits.remove(key);
        return true;
    }

    public static Collection<SafeZone> getAll() {
        return Collections.unmodifiableCollection(safeZones.values());
    }

    // ── Location queries ───────────────────────────────────────────────────────

    /** Returns true if the location is inside any safe zone. */
    public static boolean isProtected(Location loc) {
        return getAt(loc) != null;
    }

    /** Returns the first safe zone that contains the location, or null. */
    public static SafeZone getAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (SafeZone p : safeZones.values()) {
            if (p.contains(loc)) return p;
        }
        return null;
    }

    // ── Visit tracking ─────────────────────────────────────────────────────────

    public static void recordVisit(UUID uuid, String safeZoneName) {
        playerVisits.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(safeZoneName.toLowerCase());
    }

    public static boolean hasVisited(UUID uuid, String safeZoneName) {
        Set<String> visited = playerVisits.get(uuid);
        return visited != null && visited.contains(safeZoneName.toLowerCase());
    }

    // ── Display helpers ────────────────────────────────────────────────────────

    /**
     * Returns a display-friendly form of a safe zone name: underscores become spaces
     * and each word is capitalized. {@code "spawn_island"} → {@code "Spawn Island"}.
     */
    public static String capitalizedName(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    /** Spawn points whose world is not loaded, by zone name: written back unchanged. */
    private static final Map<String, LocationData> unresolvedSpawns = new HashMap<>();

    /** Safe zones and visits, for storage. Main thread only. */
    public static SafeZonesData snapshot() {
        List<SafeZonesData.Zone> zones = new ArrayList<>();
        for (SafeZone z : safeZones.values()) {
            List<ChunkPos> chunks = new ArrayList<>();
            for (String key : z.getChunks()) {
                ChunkPos p = ChunkPos.fromRuntimeKey(key);
                if (p != null) chunks.add(p);
            }
            LocationData spawn = LocationData.of(z.getSpawnPoint());
            if (spawn == null) spawn = unresolvedSpawns.get(z.getName());
            zones.add(new SafeZonesData.Zone(z.getName(), z.getDescription(), spawn, List.copyOf(chunks)));
        }
        Map<UUID, Set<String>> visits = new HashMap<>();
        playerVisits.forEach((u, set) -> {
            if (!set.isEmpty()) visits.put(u, Set.copyOf(set));
        });
        return new SafeZonesData(List.copyOf(zones), Map.copyOf(visits));
    }

    public static void restore(SafeZonesData data) {
        safeZones.clear();
        playerVisits.clear();
        unresolvedSpawns.clear();
        for (SafeZonesData.Zone row : data.zones()) {
            SafeZone z = new SafeZone(row.name().toLowerCase());
            for (ChunkPos p : row.chunks()) z.getChunks().add(p.runtimeKey());
            if (row.spawn() != null) {
                Location loc = row.spawn().toLocation();
                if (loc != null) z.setSpawnPoint(loc);
                else unresolvedSpawns.put(z.getName(), row.spawn());
            }
            z.setDescription(row.description());
            safeZones.put(z.getName(), z);
        }
        data.visits().forEach((u, set) -> {
            if (!set.isEmpty()) playerVisits.computeIfAbsent(u, k -> ConcurrentHashMap.newKeySet()).addAll(set);
        });
    }
}
