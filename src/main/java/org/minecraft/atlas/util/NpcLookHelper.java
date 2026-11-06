package org.minecraft.atlas.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.donjon.SmugglerManager;
import org.minecraft.atlas.safezone.SafeZoneNpcManager;
import org.minecraft.atlas.shop.ShopManager;

/**
 * Periodically rotates atlas NPCs (donjon Smugglers and safe-zone Explorers) toward the
 * nearest nearby player. These NPCs are spawned with {@code setAI(false)} so vanilla mob
 * AI does not turn them — this helper provides the visual look-at behavior instead.
 */
public final class NpcLookHelper {

    /** Maximum distance from which an NPC will look at a player. */
    private static final double MAX_RANGE     = 16.0;
    private static final double MAX_RANGE_SQ  = MAX_RANGE * MAX_RANGE;

    private NpcLookHelper() {}

    public static void schedule(Plugin plugin) {
        new BukkitRunnable() {
            @Override public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (world.getPlayers().isEmpty()) continue;
                    for (Entity entity : world.getEntities()) {
                        if (!isAtlasNpc(entity)) continue;
                        Player nearest = findNearestPlayer(entity);
                        if (nearest != null) lookAt(entity, nearest);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L); // every 5 ticks (0.25s) — smooth without spamming
    }

    private static boolean isAtlasNpc(Entity entity) {
        return SmugglerManager.isSmugglerNpc(entity) || SafeZoneNpcManager.isExplorer(entity)
                || ShopManager.isShopNpc(entity);
    }

    private static Player findNearestPlayer(Entity entity) {
        Player nearest = null;
        double bestDistSq = MAX_RANGE_SQ;
        Location loc = entity.getLocation();
        for (Player player : entity.getWorld().getPlayers()) {
            double distSq = player.getLocation().distanceSquared(loc);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void lookAt(Entity entity, Player player) {
        Location entityLoc = entity.getLocation();
        Location playerLoc = player.getLocation();

        double npcEye = (entity instanceof LivingEntity le) ? le.getEyeHeight() : 1.0;
        double dx = playerLoc.getX() - entityLoc.getX();
        double dy = (playerLoc.getY() + player.getEyeHeight()) - (entityLoc.getY() + npcEye);
        double dz = playerLoc.getZ() - entityLoc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        entity.setRotation(yaw, pitch);
    }
}
