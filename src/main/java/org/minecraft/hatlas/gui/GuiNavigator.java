package org.minecraft.hatlas.gui;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiNavigator {

    private static final Map<UUID, Deque<HatlasGui>> stacks = new ConcurrentHashMap<>();

    public static void push(UUID playerUUID, HatlasGui gui) {
        stacks.computeIfAbsent(playerUUID, k -> new ArrayDeque<>()).push(gui);
    }

    public static void back(Player player) {
        Deque<HatlasGui> stack = stacks.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            player.closeInventory();
        } else {
            player.openInventory(stack.pop().getInventory());
        }
    }

    /**
     * Discards the top entry without opening any inventory — used when the caller wants to
     * replace the cached parent (e.g. after a mutation) with a freshly-constructed GUI.
     * Returns the popped GUI, or null if the stack was empty.
     */
    public static HatlasGui pop(UUID playerUUID) {
        Deque<HatlasGui> stack = stacks.get(playerUUID);
        if (stack == null || stack.isEmpty()) return null;
        return stack.pop();
    }

    public static void clear(UUID playerUUID) {
        stacks.remove(playerUUID);
    }
}
