package org.minecraft.hatlas.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.storage.ItemBytes;
import org.minecraft.hatlas.storage.model.ChunkPos;
import org.minecraft.hatlas.storage.model.LocationData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers shared by the YAML domain mappers. */
final class YamlUtil {

    private YamlUtil() {}

    static UUID uuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reads a legacy slot-indexed item section ({@code "<slot>": ItemStack}) into Paper bytes.
     * {@code minSize} pads the array (e.g. the chest size). Returns null if empty.
     */
    static byte[] legacyItems(ConfigurationSection sec, int minSize) {
        if (sec == null) return null;
        Map<Integer, ItemStack> bySlot = new LinkedHashMap<>();
        int max = -1;
        for (String key : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack it = sec.getItemStack(key);
                if (slot >= 0 && it != null) {
                    bySlot.put(slot, it);
                    max = Math.max(max, slot);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (bySlot.isEmpty()) return null;
        ItemStack[] arr = new ItemStack[Math.max(minSize, max + 1)];
        bySlot.forEach((slot, it) -> arr[slot] = it);
        return ItemBytes.fromArray(arr);
    }

    /** {@code world: [packed chunk keys…]} section → chunks. Also accepts a flat "world:x:z" list. */
    static List<ChunkPos> readChunks(ConfigurationSection parent, String key) {
        List<ChunkPos> out = new ArrayList<>();
        if (parent.isConfigurationSection(key)) {
            ConfigurationSection sec = parent.getConfigurationSection(key);
            for (String world : sec.getKeys(false)) {
                for (String k : sec.getStringList(world)) {
                    try {
                        long packed = Long.parseLong(k);
                        out.add(new ChunkPos(world, ChunkPos.unpackX(packed), ChunkPos.unpackZ(packed)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } else {
            for (String s : parent.getStringList(key)) {
                ChunkPos p = ChunkPos.fromRuntimeKey(s);
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    /** Reads {@code world, x, y, z, yaw, pitch} keys of a section; null if no world. */
    static LocationData readLocation(ConfigurationSection s) {
        if (s == null) return null;
        String world = s.getString("world");
        if (world == null) return null;
        return new LocationData(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

}
