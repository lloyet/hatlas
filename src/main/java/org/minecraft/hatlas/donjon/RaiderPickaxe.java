package org.minecraft.hatlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RaiderPickaxe {

    private static NamespacedKey keyLevel;
    private static NamespacedKey keyUses;

    /** Uses granted per level: index 1=Raider I, 2=Raider II, 3=Raider III. */
    private static final int[] USES_BY_LEVEL = {0, 5, 10, 20};

    public static void init() {
        keyLevel = new NamespacedKey(Hatlas.instance, "raider_level");
        keyUses  = new NamespacedKey(Hatlas.instance, "raider_uses");
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    /** Creates a Raider II diamond pickaxe (for admin /give). */
    public static ItemStack create() {
        return create(2);
    }

    /**
     * Creates a diamond pickaxe with the given raider level.
     * Level 1 = Raider I (5 uses), 2 = Raider II (10 uses), 3 = Raider III (20 uses).
     */
    public static ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⚔ Raider's Pickaxe", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        writeRaiderMeta(meta, clamp(level), new ArrayList<>());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Applies the Raider enchantment to any item at the given level.
     * Any existing lore on the item is preserved below the raider section.
     */
    public static void applyToItem(ItemStack item, int level) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> existing = meta.lore() != null ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
        writeRaiderMeta(meta, clamp(level), existing);
        if (meta instanceof Damageable damageable) damageable.setDamage(0);
        item.setItemMeta(meta);
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public static boolean isRaiderPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyLevel, PersistentDataType.INTEGER);
    }

    public static int getRemainingUses(ItemStack item) {
        if (!isRaiderPickaxe(item)) return 0;
        Integer u = item.getItemMeta().getPersistentDataContainer().get(keyUses, PersistentDataType.INTEGER);
        return u != null ? u : 0;
    }

    // ── Mutation ───────────────────────────────────────────────────────────────

    /**
     * Decrements the use counter by 1, rebuilds the lore, and returns the new remaining count.
     * Returns 0 when the item is exhausted (caller must remove it from the inventory).
     * The ItemStack is modified in place — call {@code setItemInMainHand} after this.
     */
    public static int decrementUses(ItemStack item) {
        if (!isRaiderPickaxe(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer current = meta.getPersistentDataContainer().get(keyUses,  PersistentDataType.INTEGER);
        Integer level   = meta.getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        int remaining = (current != null ? current : 1) - 1;
        if (remaining <= 0) return 0;

        int totalUses = level != null ? USES_BY_LEVEL[clamp(level)] : USES_BY_LEVEL[2];
        meta.getPersistentDataContainer().set(keyUses, PersistentDataType.INTEGER, remaining);
        rebuildUsesLine(meta, remaining);
        updateDamage(item, meta, remaining, totalUses);
        item.setItemMeta(meta);
        return remaining;
    }

    /**
     * Scales item damage linearly so the durability bar reflects remaining uses:
     * full bar (damage=0) when all uses remain, one tick from breaking (damage=maxDur-1)
     * when exactly 1 use remains.
     */
    private static void updateDamage(ItemStack item, ItemMeta meta, int remaining, int totalUses) {
        if (!(meta instanceof Damageable damageable)) return;
        int maxDur = item.getType().getMaxDurability();
        if (maxDur <= 0) return;
        int damage;
        if (remaining == 1) {
            damage = maxDur - 1;
        } else if (totalUses <= 1) {
            damage = 0;
        } else {
            damage = (int) Math.round((double)(totalUses - remaining) / (totalUses - 1) * (maxDur - 1));
        }
        damageable.setDamage(Math.max(0, Math.min(damage, maxDur - 1)));
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static void writeRaiderMeta(ItemMeta meta, int level, List<Component> trailingLore) {
        int uses = USES_BY_LEVEL[level];
        List<Component> lore = buildLore(level, uses);
        lore.addAll(trailingLore);
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(keyUses,  PersistentDataType.INTEGER, uses);
    }

    private static List<Component> buildLore(int level, int uses) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Raider " + toRoman(level), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Allows breaking blocks in enemy claims.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(usesLine(uses));
        return lore;
    }

    /** Rebuilds only the "⚠ X blocks remaining." line (index 3) without touching the rest. */
    private static void rebuildUsesLine(ItemMeta meta, int remaining) {
        List<Component> lore = meta.lore();
        if (lore == null || lore.size() < 4) return;
        List<Component> rebuilt = new ArrayList<>(lore);
        rebuilt.set(3, usesLine(remaining));
        meta.lore(rebuilt);
    }

    private static Component usesLine(int uses) {
        return Component.text("  ⚠ " + uses + " block" + (uses == 1 ? "" : "s") + " remaining.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String toRoman(int level) {
        return switch (level) { case 1 -> "I"; case 2 -> "II"; default -> "III"; };
    }

    private static int clamp(int level) { return Math.clamp(level, 1, 3); }
}
