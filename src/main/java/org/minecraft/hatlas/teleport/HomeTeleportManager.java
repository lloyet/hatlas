package org.minecraft.hatlas.teleport;

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

public class HomeTeleportManager {

    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    private static int countdownSeconds = 10;
    private static long cooldownMs = 300_000L;

    public static void loadConfig(FileConfiguration config) {
        cooldownMs = config.getLong("faction_home.cooldown_seconds", 300L) * 1000L;
        countdownSeconds = config.getInt("faction_home.teleport_delay_seconds", 10);
    }

    /**
     * Starts a 5-second countdown then teleports the player to {@code dest}.
     * Sends an error and returns {@code false} if the player is on cooldown or already teleporting.
     */
    public static boolean startTeleport(Player player, Location dest, String locationName) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (DeathTeleportCooldownManager.denyIfOnCooldown(player)) return false;

        Long expiry = cooldownExpiry.get(uuid);
        if (!player.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before teleporting to faction home again.",
                    NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        if (player.isOp()) {
            player.teleport(dest);
            player.sendActionBar(Component.text("Teleported to " + locationName + "!", NamedTextColor.GREEN));
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
                // Cancel if the player has moved more than 0.1 blocks (ignores head rotation)
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
                            "Teleporting to " + locationName + " in " + remaining + "s…",
                            NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text(
                            "Teleported to " + locationName + "!", NamedTextColor.GREEN));
                    if (!player.isOp()) cooldownExpiry.put(uuid, System.currentTimeMillis() + cooldownMs);
                }
            }
        };

        activeTeleports.put(uuid, task);
        task.runTaskTimer(Hatlas.instance, 0L, 20L);
        return true;
    }

    /**
     * Cancels an active teleport countdown.
     * Returns {@code true} if a countdown was active and has been stopped.
     */
    public static boolean cancelTeleport(UUID playerUUID) {
        BukkitRunnable task = activeTeleports.remove(playerUUID);
        if (task == null) return false;
        task.cancel();
        return true;
    }
}
