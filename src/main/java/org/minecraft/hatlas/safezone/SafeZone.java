package org.minecraft.hatlas.safezone;

import org.bukkit.Location;

import java.util.LinkedHashSet;
import java.util.Set;

public class SafeZone {

    private final String name;
    private final Set<String> chunks = new LinkedHashSet<>();
    private Location spawnPoint = null;
    private String description = "";

    public SafeZone(String name) {
        this.name = name;
    }

    public String getName()            { return name; }
    public Set<String> getChunks()     { return chunks; }
    public int getChunkCount()         { return chunks.size(); }
    public Location getSpawnPoint()    { return spawnPoint; }
    public void setSpawnPoint(Location loc) { this.spawnPoint = loc != null ? loc.clone() : null; }
    public String getDescription()     { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }

    public boolean claim(String worldName, int cx, int cz) {
        return chunks.add(worldName + ":" + cx + ":" + cz);
    }

    public boolean unclaim(String worldName, int cx, int cz) {
        return chunks.remove(worldName + ":" + cx + ":" + cz);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return chunks.contains(loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4));
    }
}
