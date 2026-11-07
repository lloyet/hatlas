package org.minecraft.atlas.orezone;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.minecraft.atlas.storage.model.MiscData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns all {@link OreZone} instances. Holds the default ore-weight template
 * loaded from {@code orezones.yml}, drives the periodic refill scheduler at
 * {@link #intervalResetHours}, and persists zones through {@code Storage} (domain OREZONES).
 */
public final class OreZoneManager {

    private static final Map<String, OreZone> zones = new LinkedHashMap<>();

    /** Default ore-weight template, loaded from orezones.yml. */
    private static final Map<String, Integer> defaultOreWeights = new LinkedHashMap<>();

    /** Refill interval, in hours. Defaults to 1 if the config is missing. */
    private static long intervalResetHours = 1L;

    private OreZoneManager() {}

    // ── Config (orezones.yml) ──────────────────────────────────────────────────

    public static void loadConfig(FileConfiguration config) {
        defaultOreWeights.clear();
        intervalResetHours = Math.max(1L, config.getLong("interval_reset_hours", 1L));

        ConfigurationSection sec = config.getConfigurationSection("default_ore_weights");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int weight = sec.getInt(key, 0);
                if (weight > 0 && OreType.fromKey(key) != null) {
                    defaultOreWeights.put(key.toLowerCase(), weight);
                }
            }
        }
        // Safety net: if the config was empty or malformed, fall back to
        // every known ore at weight 1 so newly created zones still work.
        if (defaultOreWeights.isEmpty()) {
            for (OreType t : OreType.values()) defaultOreWeights.put(t.key(), 1);
        }
    }

    public static long getIntervalResetHours() { return intervalResetHours; }

    public static Map<String, Integer> getDefaultOreWeights() {
        return Collections.unmodifiableMap(defaultOreWeights);
    }

    // ── CRUD ───────────────────────────────────────────────────────────────────

    /** Creates a zone with the default weight template. Returns null on name collision. */
    public static OreZone create(String name) {
        String key = name.toLowerCase();
        if (zones.containsKey(key)) return null;
        OreZone z = new OreZone(key);
        z.getOreWeights().putAll(defaultOreWeights);
        zones.put(key, z);
        return z;
    }

    public static OreZone get(String name) {
        return name == null ? null : zones.get(name.toLowerCase());
    }

    public static boolean remove(String name) {
        if (name == null) return false;
        return zones.remove(name.toLowerCase()) != null;
    }

    /** True if {@code loc} is inside any ore zone. */
    public static boolean isInOreZone(Location loc) {
        for (OreZone zone : getAll()) {
            if (zone.contains(loc)) return true;
        }
        return false;
    }

    public static Collection<OreZone> getAll() {
        return Collections.unmodifiableCollection(zones.values());
    }

    // ── Fill ───────────────────────────────────────────────────────────────────

    /**
     * Re-fills every block of the zone's cuboid with a randomly-rolled ore.
     * Returns the number of blocks placed, or -1 if the zone has no bounds
     * or its world is unloaded.
     */
    public static long fill(OreZone zone) {
        if (zone == null || !zone.hasBounds()) return -1;
        World world = zone.getWorld();
        if (world == null) return -1;

        Map<String, Integer> weights = zone.getOreWeights();
        if (weights.isEmpty()) return 0;

        int totalWeight = 0;
        for (int w : weights.values()) totalWeight += Math.max(0, w);
        if (totalWeight <= 0) return 0;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long placed = 0;
        for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
            for (int y = zone.getMinY(); y <= zone.getMaxY(); y++) {
                for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                    OreType pick = pickWeighted(weights, totalWeight, rng);
                    if (pick == null) continue;
                    Block block = world.getBlockAt(x, y, z);
                    pick.placeAt(block);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Re-fills every zone. Called by the scheduler. */
    public static void fillAll() {
        for (OreZone z : zones.values()) {
            if (z.hasBounds() && z.getWorld() != null) {
                fill(z);
            }
        }
    }

    private static OreType pickWeighted(Map<String, Integer> weights, int totalWeight, ThreadLocalRandom rng) {
        int roll = rng.nextInt(totalWeight);
        int acc = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            acc += Math.max(0, e.getValue());
            if (roll < acc) return OreType.fromKey(e.getKey());
        }
        return null;
    }

    // ── Scheduler ──────────────────────────────────────────────────────────────

    public static void schedule(Plugin plugin) {
        // 20 ticks/sec × 60 sec × 60 min × intervalResetHours
        long periodTicks = 20L * 60L * 60L * intervalResetHours;
        Bukkit.getScheduler().runTaskTimer(plugin, OreZoneManager::fillAll, periodTicks, periodTicks);
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    /** Call after {@link #loadConfig} (empty zones are re-seeded with the default weights). */
    public static void restore(MiscData.OreZonesData data) {
        zones.clear();
        for (MiscData.OreZoneRow row : data.zones()) {
            OreZone zone = new OreZone(row.name().toLowerCase());
            if (row.world() != null) {
                zone.setRawBounds(row.world(), row.minX(), row.minY(), row.minZ(), row.maxX(), row.maxY(), row.maxZ());
            }
            for (MiscData.OreWeight w : row.weights()) {
                if (w.weight() > 0 && OreType.fromKey(w.ore()) != null) {
                    zone.getOreWeights().put(w.ore().toLowerCase(), w.weight());
                }
            }
            // If a saved zone has no weights (corrupted data, hand-edit, etc.),
            // re-seed with the defaults so the next reset still produces blocks.
            if (zone.getOreWeights().isEmpty()) {
                zone.getOreWeights().putAll(defaultOreWeights);
            }
            zones.put(zone.getName(), zone);
        }
    }

    public static MiscData.OreZonesData snapshot() {
        List<MiscData.OreZoneRow> out = new ArrayList<>();
        for (OreZone z : zones.values()) {
            List<MiscData.OreWeight> weights = new ArrayList<>();
            z.getOreWeights().forEach((ore, w) -> weights.add(new MiscData.OreWeight(ore, w)));
            boolean b = z.hasBounds();
            out.add(new MiscData.OreZoneRow(z.getName(), b ? z.getWorldName() : null,
                    b ? z.getMinX() : 0, b ? z.getMinY() : 0, b ? z.getMinZ() : 0,
                    b ? z.getMaxX() : 0, b ? z.getMaxY() : 0, b ? z.getMaxZ() : 0, List.copyOf(weights)));
        }
        return new MiscData.OreZonesData(List.copyOf(out));
    }
}
