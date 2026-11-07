package org.minecraft.hatlas.faction;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionClaimBorderRenderer {

    private static final Set<UUID> viewers     = ConcurrentHashMap.newKeySet();
    private static final int       CHUNK_RADIUS = 3;
    private static final int       STEP         = 2;

    // ── API ────────────────────────────────────────────────────────────────────

    public static void setVisible(UUID playerUUID, boolean visible) {
        if (visible) viewers.add(playerUUID);
        else         viewers.remove(playerUUID);
    }

    public static boolean isVisible(UUID playerUUID) {
        return viewers.contains(playerUUID);
    }

    public static void schedule(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, FactionClaimBorderRenderer::tick, 20L, 40L);
    }

    // ── Tick ───────────────────────────────────────────────────────────────────

    private static void tick() {
        viewers.removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return true;
            renderForPlayer(player);
            return false;
        });
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    private static void renderForPlayer(Player player) {
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) return;

        String worldName = player.getWorld().getName();
        int    pcx       = player.getLocation().getChunk().getX();
        int    pcz       = player.getLocation().getChunk().getZ();
        // Use the player's actual eye Y so borders are visible both above ground and in caves.
        double py        = player.getEyeLocation().getY();

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!factionName.equals(FactionClaimManager.getClaimingFaction(worldName, cx, cz))) continue;

                if (!factionName.equals(FactionClaimManager.getClaimingFaction(worldName, cx, cz - 1)))
                    spawnNSBorder(player, cx * 16, cz * 16 + 0.5, py);

                if (!factionName.equals(FactionClaimManager.getClaimingFaction(worldName, cx, cz + 1)))
                    spawnNSBorder(player, cx * 16, cz * 16 + 15.5, py);

                if (!factionName.equals(FactionClaimManager.getClaimingFaction(worldName, cx - 1, cz)))
                    spawnEWBorder(player, cx * 16 + 0.5, cz * 16, py);

                if (!factionName.equals(FactionClaimManager.getClaimingFaction(worldName, cx + 1, cz)))
                    spawnEWBorder(player, cx * 16 + 15.5, cz * 16, py);
            }
        }
    }

    /** Particles along a N/S chunk edge: fixed Z (block center), X varies over 16 blocks with STEP. */
    private static void spawnNSBorder(Player player, int startX, double fixedZ, double y) {
        for (int i = 0; i < 16; i += STEP) {
            player.spawnParticle(Particle.GLOW_SQUID_INK, startX + i + 0.5, y, fixedZ, 1, 0, 0, 0, 0);
        }
    }

    /** Particles along an E/W chunk edge: fixed X (block center), Z varies over 16 blocks with STEP. */
    private static void spawnEWBorder(Player player, double fixedX, int startZ, double y) {
        for (int i = 0; i < 16; i += STEP) {
            player.spawnParticle(Particle.GLOW_SQUID_INK, fixedX, y, startZ + i + 0.5, 1, 0, 0, 0, 0);
        }
    }
}
