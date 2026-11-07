package org.minecraft.hatlas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")

public final class GuiUtil {

    private GuiUtil() {}

    // ── Confirmation slot sets (27-slot) ──────────────────────────────────────

    public static final Set<Integer> CONFIRM_GREEN =
            Set.of(0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21);
    public static final Set<Integer> CONFIRM_RED =
            Set.of(5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26);
    public static final int SLOT_CONFIRM_INFO = 13;

    // ── Fill helpers ──────────────────────────────────────────────────────────

    /** Fills all empty slots with transparent gray glass panes. */
    public static void fillGray(Inventory inv) {
        ItemStack pane = emptyPane();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    public static ItemStack emptyPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta  = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);

        return pane;
    }

    public static ItemStack labeledPane(Material mat, Component name) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta  = pane.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(meta);

        return pane;
    }

    public static Component loreLine(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    /** Maps a {@link NamedTextColor} to the closest terracotta {@link Material}. */
    public static Material colorToTerracotta(NamedTextColor c) {
        if (c == NamedTextColor.WHITE)        return Material.WHITE_TERRACOTTA;
        if (c == NamedTextColor.BLACK)        return Material.BLACK_TERRACOTTA;
        if (c == NamedTextColor.DARK_BLUE)    return Material.BLUE_TERRACOTTA;
        if (c == NamedTextColor.DARK_GREEN)   return Material.GREEN_TERRACOTTA;
        if (c == NamedTextColor.DARK_AQUA)    return Material.CYAN_TERRACOTTA;
        if (c == NamedTextColor.DARK_RED)     return Material.RED_TERRACOTTA;
        if (c == NamedTextColor.DARK_PURPLE)  return Material.PURPLE_TERRACOTTA;
        if (c == NamedTextColor.GOLD)         return Material.ORANGE_TERRACOTTA;
        if (c == NamedTextColor.GRAY)         return Material.LIGHT_GRAY_TERRACOTTA;
        if (c == NamedTextColor.DARK_GRAY)    return Material.GRAY_TERRACOTTA;
        if (c == NamedTextColor.BLUE)         return Material.LIGHT_BLUE_TERRACOTTA;
        if (c == NamedTextColor.GREEN)        return Material.LIME_TERRACOTTA;
        if (c == NamedTextColor.AQUA)         return Material.LIGHT_BLUE_TERRACOTTA;
        if (c == NamedTextColor.RED)          return Material.RED_TERRACOTTA;
        if (c == NamedTextColor.LIGHT_PURPLE) return Material.MAGENTA_TERRACOTTA;
        if (c == NamedTextColor.YELLOW)       return Material.YELLOW_TERRACOTTA;

        return Material.WHITE_TERRACOTTA;
    }

    /** Converts a {@link NamedTextColor} key like {@code "dark_blue"} to {@code "Dark Blue"}. */
    public static String colorDisplayName(NamedTextColor color) {
        String key = NamedTextColor.NAMES.key(color);
        if (key == null) return "Unknown";

        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }

        return sb.toString();
    }

    // ── Slot layout helpers ───────────────────────────────────────────────────

    /**
     * Returns the interior content slots of a 54-slot inventory (rows 1–4, cols 1–7),
     * giving 28 usable slots in left-to-right, top-to-bottom order.
     */
    public static int[] contentSlots54() {
        List<Integer> list = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                list.add(row * 9 + col);
            }
        }

        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Returns slot indices for the chest list, centered in the middle row (slots 9–17)
     * of a 27-slot inventory. For more than 9 chests, falls back to sequential slots from 0.
     */
    public static int[] chestListSlots(int count) {
        if (count <= 9) {
            int[] slots = new int[count];
            int start = 9 + (9 - count) / 2;
            for (int i = 0; i < count; i++) slots[i] = start + i;
            return slots;
        }
        int capped = Math.min(count, 27);
        int[] slots = new int[capped];
        for (int i = 0; i < capped; i++) slots[i] = i;

        return slots;
    }

    // ── Label / value text helpers ────────────────────────────────────────────

    public static Component label(String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
    }

    public static Component value(String text) {
        return Component.text(text, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    public static String formatTime(long ms) {
        if (ms <= 0) return "Expired";
        long seconds = ms / 1000;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public static String formatMaterial(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    // ── Action type helpers ───────────────────────────────────────────────────

    public static String actionVerb(String actionType) {
        if (actionType == null) return "Doing";
        return switch (actionType.toLowerCase()) {
            case "break_block" -> "Mining";
            case "kill_entity" -> "Hunting";
            case "harvest_crop" -> "Harvesting";
            case "brew_potion" -> "Brewing";
            case "craft_item" -> "Crafting";
            default -> capitalize(actionType.replace('_', ' '));
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Difficulty helpers ────────────────────────────────────────────────────

    public static String difficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 1 -> "Easy";
            case 2 -> "Normal";
            case 3 -> "Hard";
            case 4 -> "Hardcore";
            case 5 -> "Legendary";
            default -> "?";
        };
    }

    public static NamedTextColor difficultyColor(int difficulty) {
        return switch (difficulty) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.YELLOW;
            case 3 -> NamedTextColor.RED;
            case 4 -> NamedTextColor.DARK_RED;
            case 5 -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }

    // ── Common item builders ──────────────────────────────────────────────────

    public static ItemStack buildBackItem(String label) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the faction name truncated to fit in GUI titles.
     * Names longer than 18 characters are cut to 15 and suffixed with "..."
     */
    public static String truncateFactionName(String name) {
        return name.length() > 18 ? name.substring(0, 15) + "..." : name;
    }
}