package org.minecraft.atlas.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.storage.model.MiscData;

/** The custom spawn set by /spawn set (persisted through Storage, domain SPAWN). */
public class SpawnManager {

    private static Location customSpawn = null;
    /** Stored spawn whose world is not loaded: written back unchanged. */
    private static LocationData unresolved = null;

    public static void restore(MiscData.SpawnData data) {
        customSpawn = null;
        unresolved = null;
        if (data.spawn() == null) return;
        customSpawn = data.spawn().toLocation();
        if (customSpawn == null) unresolved = data.spawn();
    }

    public static MiscData.SpawnData snapshot() {
        LocationData loc = LocationData.of(customSpawn);
        return new MiscData.SpawnData(loc != null ? loc : unresolved);
    }

    public static Location getSpawn(World world) {
        return customSpawn != null ? customSpawn : world.getSpawnLocation();
    }

    public static void setSpawn(Location location) {
        customSpawn = location.clone();
        unresolved = null;
    }
}
