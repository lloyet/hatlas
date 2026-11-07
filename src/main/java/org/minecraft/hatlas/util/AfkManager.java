package org.minecraft.hatlas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager implements Listener {

    private static int timeoutSeconds = 300;
    private static final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public static void loadConfig(FileConfiguration config) {
        timeoutSeconds = config.getInt("afk.timeout_seconds", 300);
    }

    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, AfkManager::tick, 20L, 20L);
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            long last = lastActivity.getOrDefault(player.getUniqueId(), now);
            if (now - last >= timeoutMs) {
                player.kick(Component.text(
                        "Disconnected: AFK for more than " + timeoutSeconds + " seconds.", NamedTextColor.YELLOW));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    private static void recordActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
