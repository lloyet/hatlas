package org.minecraft.hatlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElectricalCreeperManager {

    /**
     * Number of charged-creeper explosions required to destroy one obsidian block.
     */
    public static final int OBSIDIAN_HITS_REQUIRED = 3;

    private static NamespacedKey keyElectricalCreeperEgg;
    private static NamespacedKey keyElectricalCreeper;

    /**
     * In-memory hit counter per obsidian block location. Resets on server restart.
     */
    private static final Map<Location, Integer> obsidianHitMap = new HashMap<>();

    public static void init() {
        keyElectricalCreeperEgg = new NamespacedKey(Hatlas.instance, "electrical_creeper_egg");
        keyElectricalCreeper = new NamespacedKey(Hatlas.instance, "electrical_creeper");
    }

    public static ItemStack createCreeperEgg(int amount) {
        return new ItemStack(Material.CREEPER_SPAWN_EGG, Math.max(1, amount));
    }

    public static ItemStack createElectricalCreeperEgg() {
        ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⚡ Powered Creeper Egg", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.empty(),
                Component.text("  Right-click on ground to spawn a Powered Creeper.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("  ⛏ Damages Obsidian:", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("  " + OBSIDIAN_HITS_REQUIRED + " explosions to break 1 Obsidian block.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("  ✔ Usable in enemy faction territory!", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyElectricalCreeperEgg, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isElectricalCreeperEgg(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(keyElectricalCreeperEgg, PersistentDataType.BYTE);
    }

    public static boolean isElectricalCreeper(Entity entity) {
        return entity.getPersistentDataContainer().has(keyElectricalCreeper, PersistentDataType.BYTE);
    }

    public static NamespacedKey getElectricalCreeperKey() {
        return keyElectricalCreeper;
    }

    /**
     * Registers one explosion hit on an obsidian block.
     * Returns the new total hit count.
     */
    public static int addObsidianHit(Location blockLoc) {
        Location key = new Location(blockLoc.getWorld(),
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        return obsidianHitMap.merge(key, 1, Integer::sum);
    }

    /**
     * Removes the hit counter for a block (call when the block is destroyed or replaced).
     */
    public static void clearObsidianHit(Location blockLoc) {
        obsidianHitMap.remove(new Location(blockLoc.getWorld(),
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ()));
    }

    /**
     * Creeper egg amount: at least 1, scaling with level (0–99) and rarity tier (0–5).
     * Formula: 1 + floor(level / 99.0 * (rarity.ordinal() + 1))
     */
    public static int computeEggAmount(int level, DonjonRarity rarity) {
        return 1 + (int) (level / 99.0 * (rarity.ordinal() + 1));
    }

    /**
     * Electrical creeper egg drop chance, scaling with rarity tier.
     * COMMON=0.5%, RARE=1%, EPIC=1.5%, LEGENDARY=2%, MYSTIC=2.5%, GODDESS=3%
     */
    public static double electricalDropChance(DonjonRarity rarity) {
        return (rarity.ordinal() + 1) * 0.005;
    }
}
