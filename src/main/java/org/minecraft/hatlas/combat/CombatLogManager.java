package org.minecraft.hatlas.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.minecraft.hatlas.storage.model.MiscData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-log state + tick scheduler. Per-player flags live here; the actual event
 * handlers (player-hit, player-quit) sit in {@code listener.CombatLogListener} which
 * drives this manager via {@link #mark(Player)} and {@link #applyCombatLogoutPenalty(Player)}.
 *
 * <p>Flagged players get a chat notice when entering combat; another chat notice when
 * their combat timer expires (via the per-second {@link #tick()}).
 */
public class CombatLogManager {

    private static int timeoutSeconds = 20;
    private static final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private static volatile boolean shuttingDown = false;

    public static void loadConfig(FileConfiguration config) {
        timeoutSeconds = config.getInt("combat_log.timeout_seconds", 20);
    }

    /**
     * Restores in-combat timers. Expired entries are skipped so a long server outage does
     * not resurrect stale fights.
     */
    public static void restore(MiscData.CombatData data) {
        combatUntil.clear();
        long now = System.currentTimeMillis();
        data.inCombatUntil().forEach((uuid, until) -> {
            if (until > now) combatUntil.put(uuid, until);
        });
    }

    /**
     * In-combat timers (UUID → until-millis), so a server restart does not erase an
     * active fight. Expired entries are dropped.
     */
    public static MiscData.CombatData snapshot() {
        long now = System.currentTimeMillis();
        Map<UUID, Long> out = new HashMap<>();
        for (Map.Entry<UUID, Long> e : combatUntil.entrySet()) {
            if (e.getValue() > now) out.put(e.getKey(), e.getValue());
        }
        return new MiscData.CombatData(Map.copyOf(out));
    }

    /** Suppresses the combat-logout kill during a legitimate server shutdown. */
    public static void setShuttingDown(boolean value) {
        shuttingDown = value;
    }

    public static boolean isInCombat(UUID uuid) {
        Long until = combatUntil.get(uuid);
        if (until == null) return false;
        return System.currentTimeMillis() < until;
    }

    /** Periodic tick that retires expired entries and sends the "combat ended" action bar. */
    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, CombatLogManager::tick, 20L, 20L);
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combatUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now < entry.getValue()) continue;
            it.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(
                        "Combat has ended — you can safely disconnect now.",
                        NamedTextColor.GREEN));
            }
        }
    }

    /**
     * Flags {@code player} as in-combat for {@code timeoutSeconds}, extending the timer
     * if they were already flagged. Sends a chat notice the first time the flag is set
     * (i.e. transition from out-of-combat → in-combat).
     */
    public static void mark(Player player) {
        boolean wasInCombat = isInCombat(player.getUniqueId());
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + timeoutSeconds * 1000L);
        if (!wasInCombat) {
            player.sendMessage(Component.text(
                    "You have entered combat — disconnecting in the next "
                            + timeoutSeconds + "s will kill you.",
                    NamedTextColor.RED));
        }
    }

    /**
     * Penalty for disconnecting while in combat: drops the full inventory at the player's
     * location and kills them. Skipped during a clean server shutdown so timers persist
     * to the data file instead. No-op if the player isn't in combat.
     */
    public static void applyCombatLogoutPenalty(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isInCombat(uuid)) return;
        // Server is stopping — preserve combat state in the file, do not penalise the player.
        if (shuttingDown) return;
        combatUntil.remove(uuid);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        PlayerInventory inv = player.getInventory();

        if (world != null) {
            org.minecraft.hatlas.economy.Party owner = org.minecraft.hatlas.economy.Party.player(player);
            for (ItemStack item : inv.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    org.minecraft.hatlas.economy.RubyLedger.drop(loc, item, owner, "COMBAT_LOG");
                }
            }
            for (ItemStack armor : inv.getArmorContents()) {
                if (armor != null && !armor.getType().isAir()) {
                    world.dropItemNaturally(loc, armor);
                }
            }
        }
        inv.clear();
        player.setHealth(0);

        Bukkit.broadcast(Component.text(
                player.getName() + " disconnected during combat and was killed.",
                NamedTextColor.RED));
    }
}
