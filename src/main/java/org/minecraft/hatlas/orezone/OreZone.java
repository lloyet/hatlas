package org.minecraft.hatlas.orezone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cuboid region in a single world that is periodically re-filled with a
 * randomised distribution of ore blocks. Each entry in {@link #getOreWeights()}
 * is an ore key (matching {@link OreType#key()}) and its relative weight.
 */
public class OreZone {

    private final String name;
    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    /** ore key → relative weight. Insertion-ordered for stable YAML output. */
    private final Map<String, Integer> oreWeights = new LinkedHashMap<>();

    public OreZone(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public String getWorldName() { return worldName; }

    public World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public Map<String, Integer> getOreWeights() { return oreWeights; }

    /**
     * Records the two corner points that define this zone's cuboid. The two
     * locations must belong to the same world; otherwise the call is ignored.
     */
    public void setBounds(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return;
        if (!a.getWorld().getName().equals(b.getWorld().getName())) return;
        this.worldName = a.getWorld().getName();
        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    /** Used by {@link OreZoneManager} when restoring a zone from YAML. */
    public void setRawBounds(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldName = worldName;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    /** True if {@code loc} is a block inside this zone's cuboid (bounds inclusive). */
    public boolean contains(Location loc) {
        if (!hasBounds() || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean hasBounds() {
        return worldName != null;
    }

    /** Total block count of the cuboid (inclusive of both corners). */
    public long volume() {
        if (!hasBounds()) return 0;
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
