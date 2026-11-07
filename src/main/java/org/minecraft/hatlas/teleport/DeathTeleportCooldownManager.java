package org.minecraft.hatlas.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks a per-player death-penalty teleport cooldown that escalates on repeated deaths
 * within an active escalation window.
 *
 * State flow per death:
 *   - First ever death        : mul = 1,   cooldown = base * 1
 *   - Death within window     : mul *= cfg_multiplier
 *   - Death outside window    : mul resets to cfg_multiplier
 *   window duration = current_cooldown * cfg_multiplier * 1.5
 */
public class DeathTeleportCooldownManager {

    private static int baseCooldownSeconds = 20;
    private static int multiplierBase      = 2;

    private static final Map<UUID, Integer> deathMul       = new HashMap<>();
    private static final Map<UUID, Long>    deathWindowEnd = new HashMap<>();
    private static final Map<UUID, Long>    tpCooldownUntil = new HashMap<>();

    public static void loadConfig(FileConfiguration config) {
        baseCooldownSeconds = config.getInt("death_teleport_cooldown.base_seconds", 20);
        multiplierBase      = config.getInt("death_teleport_cooldown.multiplier", 2);
    }

    public static void onPlayerDeath(UUID uuid) {
        long now      = System.currentTimeMillis();
        int  curMul   = deathMul.getOrDefault(uuid, 1);
        long windowEnd = deathWindowEnd.getOrDefault(uuid, 0L);

        if (windowEnd > 0 && now < windowEnd) {
            curMul *= multiplierBase;
        } else if (windowEnd > 0) {
            curMul = multiplierBase;
        }
        // else: first-ever death — keep curMul = 1

        long cooldownMs    = (long) baseCooldownSeconds * curMul * 1000L;
        long newWindowEnd  = now + (long)(cooldownMs * multiplierBase * 1.5);

        deathMul.put(uuid, curMul);
        deathWindowEnd.put(uuid, newWindowEnd);
        tpCooldownUntil.put(uuid, now + cooldownMs);
    }

    public static boolean isOnCooldown(UUID uuid) {
        Long expiry = tpCooldownUntil.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public static long getRemainingSeconds(UUID uuid) {
        Long expiry = tpCooldownUntil.get(uuid);
        if (expiry == null) return 0L;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000 : 0L;
    }

    /**
     * If the player is on a death-penalty cooldown, sends them an error message and returns true.
     * Returns false (and does nothing) if they are allowed to teleport.
     * OPs bypass the cooldown.
     */
    public static boolean denyIfOnCooldown(Player player) {
        if (player.isOp()) return false;
        if (!isOnCooldown(player.getUniqueId())) return false;
        long secs = getRemainingSeconds(player.getUniqueId());
        player.sendMessage(Component.text(
                "You cannot teleport for " + secs + "s (death penalty).", NamedTextColor.RED));
        return true;
    }
}
