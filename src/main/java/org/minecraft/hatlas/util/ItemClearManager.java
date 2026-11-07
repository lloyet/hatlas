package org.minecraft.hatlas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;

public class ItemClearManager {

    private static final int CYCLE_SECONDS = 20 * 60;
    private static int secondsRemaining = CYCLE_SECONDS;

    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, ItemClearManager::tick, 20L, 20L);
    }

    private static void tick() {
        secondsRemaining--;

        switch (secondsRemaining) {
            case 600 -> broadcast("Ground items will be cleared in 10 minutes!", NamedTextColor.YELLOW);
            case 300 -> broadcast("Ground items will be cleared in 5 minutes!", NamedTextColor.YELLOW);
            case 60 -> broadcast("Ground items will be cleared in 1 minute!", NamedTextColor.GOLD);
            case 30 -> broadcast("Ground items will be cleared in 30 seconds!", NamedTextColor.GOLD);
            case 10 -> broadcast("Ground items will be cleared in 10 seconds!", NamedTextColor.GOLD);
            case 3 -> broadcast("Ground items will be cleared in 3 seconds!", NamedTextColor.RED);
            case 2 -> broadcast("Ground items will be cleared in 2 seconds!", NamedTextColor.RED);
            case 1 -> broadcast("Ground items will be cleared in 1 second!", NamedTextColor.RED);
            case 0 -> clearItems();
        }

        if (secondsRemaining <= 0) {
            secondsRemaining = CYCLE_SECONDS;
        }
    }

    private static void clearItems() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    count++;
                }
            }
        }
        broadcast("Cleared " + count + " ground item" + (count == 1 ? "" : "s") + ".", NamedTextColor.GREEN);
    }

    private static void broadcast(String message, NamedTextColor color) {
        Component msg = Component.text("[AutoClear] ", NamedTextColor.AQUA)
                .append(Component.text(message, color));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }
}
