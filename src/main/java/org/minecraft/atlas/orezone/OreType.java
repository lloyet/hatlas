package org.minecraft.atlas.orezone;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.minecraft.atlas.block.RubyBlockManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The set of ore blocks an {@link OreZone} can spawn. Each entry knows how
 * to place itself: vanilla ores go through {@link Block#setType(Material)},
 * while custom ores (currently only {@code ruby}) defer to their own manager.
 */
public enum OreType {

    DIAMOND("diamond", Material.DIAMOND_ORE),
    GOLD   ("gold",    Material.GOLD_ORE),
    EMERALD("emerald", Material.EMERALD_ORE),
    IRON   ("iron",    Material.IRON_ORE),
    COPPER ("copper",  Material.COPPER_ORE),
    COAL   ("coal",    Material.COAL_ORE),
    STONE  ("stone",   Material.STONE),
    RUBY   ("ruby",    null) {
        @Override
        public void placeAt(Block block) {
            RubyBlockManager.placeAt(block, "ruby_ore");
        }
    };

    private static final Map<String, OreType> BY_KEY = new LinkedHashMap<>();
    static {
        for (OreType t : values()) BY_KEY.put(t.key, t);
    }

    private final String key;
    private final Material material;

    OreType(String key, Material material) {
        this.key = key;
        this.material = material;
    }

    public String key() { return key; }

    /** Place this ore at the given block, removing any prior custom-block tracking. */
    public void placeAt(Block block) {
        // Clear any prior custom-block record at this location before overwriting.
        RubyBlockManager.removeAt(block);
        block.setType(material, false);
    }

    public static OreType fromKey(String key) {
        return key == null ? null : BY_KEY.get(key.toLowerCase());
    }
}
