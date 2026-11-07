package org.minecraft.hatlas.faction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class FactionClaimManager {

    /** "worldName:chunkX:chunkZ" → owning faction name */
    private static final Map<String, String> claimedChunks = new HashMap<>();

    /** factionName → set of "world:chunkX:chunkZ" keys */
    private static final Map<String, Set<String>> factionChunks = new HashMap<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Claims the initial center chunk when a faction is created (no freeclaim consumed). */
    public static void initializeClaim(String factionName, String worldName, int chunkX, int chunkZ) {
        String k = key(worldName, chunkX, chunkZ);
        claimedChunks.put(k, factionName);
        factionChunks.computeIfAbsent(factionName, f -> new LinkedHashSet<>()).add(k);
    }

    /** Claims a chunk manually (caller must check freeclaims first). */
    public static void claimChunk(String factionName, String worldName, int chunkX, int chunkZ) {
        String k = key(worldName, chunkX, chunkZ);
        claimedChunks.put(k, factionName);
        factionChunks.computeIfAbsent(factionName, f -> new LinkedHashSet<>()).add(k);
    }

    /**
     * Unclaims the chunk at the given position.
     * Returns the faction name that owned it, or null if it was unclaimed.
     */
    public static String unclaimChunk(String worldName, int chunkX, int chunkZ) {
        String k = key(worldName, chunkX, chunkZ);
        String owner = claimedChunks.remove(k);
        if (owner != null) {
            Set<String> set = factionChunks.get(owner);
            if (set != null) set.remove(k);
        }
        return owner;
    }

    /** Removes a batch of chunk keys from the global claim map. Used on crystal destruction. */
    public static void removeClaimsForCrystal(Collection<String> chunkKeys) {
        for (String key : chunkKeys) {
            String owner = claimedChunks.remove(key);
            if (owner != null) {
                Set<String> set = factionChunks.get(owner);
                if (set != null) set.remove(key);
            }
        }
    }

    /** Removes all claims when a faction is disbanded. */
    public static void removeAllClaims(String factionName) {
        Set<String> chunks = factionChunks.remove(factionName);
        if (chunks != null) chunks.forEach(claimedChunks::remove);
    }

    /** Re-keys all maps when a faction is renamed. */
    public static void renameFactionClaims(String oldName, String newName) {
        Set<String> chunks = factionChunks.remove(oldName);
        if (chunks != null) {
            factionChunks.put(newName, chunks);
            chunks.forEach(k -> claimedChunks.put(k, newName));
        }
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    /** Returns the faction owning this chunk, or null. */
    public static String getClaimingFaction(String worldName, int chunkX, int chunkZ) {
        return claimedChunks.get(key(worldName, chunkX, chunkZ));
    }

    /** Returns the number of chunks this faction currently owns. */
    public static int getClaimCount(String factionName) {
        Set<String> s = factionChunks.get(factionName);
        return s == null ? 0 : s.size();
    }

    /**
     * Returns true if at least one of the four axis-adjacent chunks
     * (N/S/E/W) is already claimed by {@code factionName}.
     */
    public static boolean hasAdjacentClaim(String factionName, String worldName, int chunkX, int chunkZ) {
        Set<String> chunks = factionChunks.get(factionName);
        if (chunks == null || chunks.isEmpty()) return false;
        return chunks.contains(key(worldName, chunkX + 1, chunkZ))
            || chunks.contains(key(worldName, chunkX - 1, chunkZ))
            || chunks.contains(key(worldName, chunkX, chunkZ + 1))
            || chunks.contains(key(worldName, chunkX, chunkZ - 1));
    }

    // Claims are persisted per crystal (CRYSTALS domain); this class is only the runtime index.

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static String key(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }
}
