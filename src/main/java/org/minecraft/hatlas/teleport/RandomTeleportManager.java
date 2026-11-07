package org.minecraft.hatlas.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.util.TitleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RandomTeleportManager {

    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();
    private static final Random random = new Random();

    private static int  countdownSeconds = 10;
    private static long cooldownMs       = 30_000L;
    private static int  teleportRadius   = 1024;

    public static void loadConfig(FileConfiguration config) {
        cooldownMs       = config.getLong("random_teleport.cooldown_seconds",      30L) * 1000L;
        teleportRadius   = config.getInt ("random_teleport.radius",                1024);
        countdownSeconds = config.getInt ("random_teleport.teleport_delay_seconds", 10);
    }

    public static boolean startTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        World.Environment env = player.getWorld().getEnvironment();
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            player.sendMessage(Component.text(
                    "/rtp is only available in the Overworld.", NamedTextColor.RED));
            return false;
        }

        Long expiry = cooldownExpiry.get(uuid);
        if (!player.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before using /air tp again.", NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        Location dest = findRandomLocation(player.getWorld());
        if (dest == null) {
            player.sendMessage(Component.text(
                    "Could not find a safe location. Please try again.", NamedTextColor.RED));
            return false;
        }

        if (player.isOp()) {
            player.teleport(dest);
            player.sendActionBar(Component.text("Teleported!", NamedTextColor.GREEN));
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
                double delta = Math.abs(current.getX() - startLocation.getX())
                        + Math.abs(current.getY() - startLocation.getY())
                        + Math.abs(current.getZ() - startLocation.getZ());
                if (delta > 0.1) {
                    activeTeleports.remove(uuid);
                    cancel();
                    TitleUtil.notify(player, "Teleport cancelled — you moved!", NamedTextColor.RED);
                    return;
                }
                if (remaining > 0) {
                    player.sendActionBar(Component.text(
                            "Random teleport in " + remaining + "s… Don't move!", NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text("Teleported!", NamedTextColor.GREEN));
                    if (!player.isOp()) cooldownExpiry.put(uuid, System.currentTimeMillis() + cooldownMs);
                }
            }
        };

        activeTeleports.put(uuid, task);
        task.runTaskTimer(Hatlas.instance, 0L, 20L);
        return true;
    }

    public static boolean cancelTeleport(UUID playerUUID) {
        BukkitRunnable task = activeTeleports.remove(playerUUID);
        if (task == null) return false;
        task.cancel();
        return true;
    }

    private static Location findRandomLocation(World world) {
        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * teleportRadius;
            int x = (int) (spawn.getX() + dist * Math.cos(angle));
            int z = (int) (spawn.getZ() + dist * Math.sin(angle));
            Location ground = world.getHighestBlockAt(x, z).getLocation();
            Location feet = ground.clone().add(0.5, 1, 0.5);
            Location head = feet.clone().add(0, 1, 0);
            if (ground.getBlock().getType().isSolid()
                    && feet.getBlock().getType() == Material.AIR
                    && head.getBlock().getType() == Material.AIR) {
                feet.setYaw(0);
                feet.setPitch(0);
                return feet;
            }
        }
        return null;
    }
}
