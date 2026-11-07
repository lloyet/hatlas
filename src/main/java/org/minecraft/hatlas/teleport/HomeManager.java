package org.minecraft.hatlas.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.storage.model.HomesData;
import org.minecraft.hatlas.storage.model.LocationData;
import org.minecraft.hatlas.util.TitleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HomeManager {

    /** Reserved name for the player's primary home — set/used by /sethome and /home with no args. */
    public static final String MAIN_HOME = "main";

    /**
     * UUID → (home name → location), insertion-ordered so "first home" is deterministic.
     */
    private static final Map<UUID, LinkedHashMap<String, Location>> homes = new HashMap<>();

    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    private static int countdownSeconds = 10;
    private static long cooldownMs = 300_000L;

    public static void loadConfig(FileConfiguration config) {
        cooldownMs = config.getLong("home_teleport.cooldown_seconds", 300L) * 1000L;
        countdownSeconds = config.getInt("home_teleport.teleport_delay_seconds", 10);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /** Stored homes whose world is not loaded: kept verbatim and written back. */
    private static final List<HomesData.Home> unresolvedHomes = new ArrayList<>();

    public static void restore(HomesData data) {
        homes.clear();
        unresolvedHomes.clear();
        for (HomesData.Home h : data.homes()) {
            Location loc = h.location() == null ? null : h.location().toLocation();
            if (loc == null) {
                if (h.location() != null) unresolvedHomes.add(h);
                continue;
            }
            homes.computeIfAbsent(h.player(), k -> new LinkedHashMap<>()).put(h.name(), loc);
        }
    }

    /** Every home in insertion order, for storage. Main thread only. */
    public static HomesData snapshot() {
        List<HomesData.Home> out = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashMap<String, Location>> e : homes.entrySet()) {
            for (Map.Entry<String, Location> h : e.getValue().entrySet()) {
                LocationData loc = LocationData.of(h.getValue());
                if (loc != null) out.add(new HomesData.Home(e.getKey(), h.getKey(), loc));
            }
        }
        for (HomesData.Home h : unresolvedHomes) {
            Map<String, Location> current = homes.get(h.player());
            if (current == null || !current.containsKey(h.name())) out.add(h);
        }
        return new HomesData(List.copyOf(out));
    }

    // -------------------------------------------------------------------------
    // Home management
    // -------------------------------------------------------------------------

    public static void setHome(UUID playerUUID, String name, Location location) {
        homes.computeIfAbsent(playerUUID, k -> new LinkedHashMap<>())
                .put(name, location.clone());
    }

    public static boolean hasAnyHome(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null && !m.isEmpty();
    }

    public static boolean hasHome(UUID playerUUID, String name) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null && m.containsKey(name);
    }

    public static Location getHomeLocation(UUID playerUUID, String name) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null ? m.get(name) : null;
    }

    public static String getFirstHomeName(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        if (m == null || m.isEmpty()) return null;
        return m.keySet().iterator().next();
    }

    public static Set<String> getHomeNames(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null ? Collections.unmodifiableSet(m.keySet()) : Collections.emptySet();
    }

    public static boolean deleteHome(UUID playerUUID, String name) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        if (m == null) return false;
        boolean removed = m.remove(name) != null;
        if (removed && m.isEmpty()) homes.remove(playerUUID);
        return removed;
    }

    /**
     * Returns the maximum number of homes (main + secondaries) the player is allowed,
     * computed as 1 + the bonus from their faction's homes-skill purchases.
     */
    public static int getHomeLimit(UUID playerUUID) {
        String factionName = FactionManager.getPlayerFaction(playerUUID);
        if (factionName == null) return 1;
        Faction faction = FactionManager.getFaction(factionName);
        return 1 + (faction != null ? faction.getBonusHomes() : 0);
    }

    /**
     * Removes every home except the reserved {@link #MAIN_HOME}.
     * Used when a player loses faction membership and forfeits the bonus slots.
     */
    public static void removeSecondaryHomes(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        if (m == null) return;
        m.keySet().removeIf(name -> !MAIN_HOME.equals(name));
        if (m.isEmpty()) homes.remove(playerUUID);
    }

    // -------------------------------------------------------------------------
    // Teleport
    // -------------------------------------------------------------------------

    /**
     * Starts the teleport countdown.
     * Pass {@code null} for {@code homeName} to teleport to the {@link #MAIN_HOME}.
     */
    public static boolean startTeleport(Player player, String homeName) {
        UUID uuid = player.getUniqueId();

        if (!hasAnyHome(uuid)) {
            player.sendMessage(Component.text(
                    "You have no home set. Use /sethome first.", NamedTextColor.RED));
            return false;
        }

        String resolvedName = (homeName == null) ? MAIN_HOME : homeName;
        Location dest = getHomeLocation(uuid, resolvedName);
        if (dest == null) {
            if (homeName == null) {
                player.sendMessage(Component.text(
                        "You have no main home set. Use /sethome to set one.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(
                        "Home '" + resolvedName + "' not found.", NamedTextColor.RED));
            }
            return false;
        }

        if (DeathTeleportCooldownManager.denyIfOnCooldown(player)) return false;

        long now = System.currentTimeMillis();
        Long expiry = cooldownExpiry.get(uuid);
        if (!player.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before using /home again.", NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        if (player.isOp()) {
            player.teleport(dest);
            player.sendActionBar(Component.text("Teleported to '" + resolvedName + "'!", NamedTextColor.GREEN));
            return true;
        }

        Location startLocation = player.getLocation().clone();
        String displayName = resolvedName;

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
                            "Teleporting to '" + displayName + "' in " + remaining + "s…",
                            NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text(
                            "Teleported to '" + displayName + "'!", NamedTextColor.GREEN));
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
}
