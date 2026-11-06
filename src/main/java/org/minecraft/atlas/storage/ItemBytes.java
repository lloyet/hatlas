package org.minecraft.atlas.storage;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Item (de)serialization for storage. Uses Paper's byte format, which embeds the data
 * version so items are upgraded by the DataFixer after a Minecraft update.
 * Call on the main thread (snapshots are built there; loading happens in onEnable).
 */
public final class ItemBytes {

    private ItemBytes() {}

    /** Serializes an inventory (null slots allowed). Returns null if every slot is empty. */
    public static byte[] fromArray(ItemStack[] items) {
        if (items == null) return null;
        boolean any = false;
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it != null && it.getType() != Material.AIR && it.getAmount() > 0) {
                copy[i] = it;
                any = true;
            } else {
                copy[i] = ItemStack.empty();
            }
        }
        return any ? ItemStack.serializeItemsAsBytes(copy) : null;
    }

    /** Deserializes an inventory; empty slots come back as null. Returns an empty array for null. */
    public static ItemStack[] toArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new ItemStack[0];
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].isEmpty()) items[i] = null;
        }
        return items;
    }

    /** Serializes a single non-empty item; returns null for null/air. */
    public static byte[] fromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return null;
        return item.serializeAsBytes();
    }

    public static ItemStack toItem(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
    }

    public static String toBase64(byte[] bytes) {
        return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] fromBase64(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
