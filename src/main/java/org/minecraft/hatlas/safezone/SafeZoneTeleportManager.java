package org.minecraft.hatlas.safezone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.util.TitleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the {@code /safezone tp <name>} countdown teleport. Mirrors HomeTeleportManager:
 * fixed delay and post-teleport cooldown configurable from config.yml, cancelled on movement,
 * ops bypass both delay and cooldown.
 */
public class SafeZoneTeleportManager {

    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long>           cooldownExpiry  = new HashMap<>();

    private static int  countdownSeconds = 10;
    private static long cooldownMs       = 30_000L;

    public static void loadConfig(FileConfiguration config) {
        countdownSeconds = config.getInt ("safezone.teleport_delay_seconds",     10);
        cooldownMs       = config.getLong("safezone.teleport_cooldown_seconds", 30L) * 1000L;
    }

    /**
     * Starts the configured countdown then teleports the player to {@code dest}.
     * Returns false if a teleport is already in progress or the player is on cooldown.
     */
    public static boolean startTeleport(Player player, Location dest, String zoneLabel) {
        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();

        Long expiry = cooldownExpiry.get(uuid);
        if (!player.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before teleporting to a safe zone again.",
                    NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        if (player.isOp()) {
            player.teleport(dest);
            player.sendActionBar(Component.text("Teleported to " + zoneLabel + "!", NamedTextColor.GREEN));
            return true;
        }

        Location startLocation = player.getLocation().clone();

        BukkitRunnable task = new BukkitRunnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeTeleports.remove(uuid);
                    cancel();
                    return;
                }
                Location current = player.getLocation();
                double dxz = Math.abs(current.getX() - startLocation.getX())
                        + Math.abs(current.getY() - startLocation.getY())
                        + Math.abs(current.getZ() - startLocation.getZ());
                if (dxz > 0.1) {
                    activeTeleports.remove(uuid);
                    cancel();
                    TitleUtil.notify(player, "Teleport cancelled — you moved!", NamedTextColor.RED);
                    return;
                }
                if (remaining > 0) {
                    player.sendActionBar(Component.text(
                            "Teleporting to " + zoneLabel + " in " + remaining + "s…",
                            NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text(
                            "Teleported to " + zoneLabel + "!", NamedTextColor.GREEN));
                    if (!player.isOp()) cooldownExpiry.put(uuid, System.currentTimeMillis() + cooldownMs);
                }
            }
        };

        activeTeleports.put(uuid, task);
        task.runTaskTimer(Hatlas.instance, 0L, 20L);
        return true;
    }

    /** Cancels an active teleport countdown. Returns true if one was active. */
    public static boolean cancelTeleport(UUID playerUUID) {
        BukkitRunnable task = activeTeleports.remove(playerUUID);
        if (task == null) return false;
        task.cancel();
        return true;
    }
}
