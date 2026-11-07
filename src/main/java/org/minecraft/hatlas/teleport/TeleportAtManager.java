package org.minecraft.hatlas.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.util.TitleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportAtManager {

    private record TpaRequest(UUID requesterUUID, UUID targetUUID, long expiryMs) {
    }

    private static final Map<UUID, TpaRequest> pendingRequests = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    private static final long REQUEST_EXPIRY_MS = 30_000L;
    private static int  countdownSeconds = 10;
    private static long cooldownMs       = 60_000L;

    public static void loadConfig(FileConfiguration config) {
        cooldownMs       = config.getLong("tpa.cooldown_seconds",         60L) * 1000L;
        countdownSeconds = config.getInt ("tpa.teleport_delay_seconds",   10);
    }

    public static boolean sendRequest(Player requester, Player target) {
        UUID rUUID = requester.getUniqueId();
        UUID tUUID = target.getUniqueId();

        if (rUUID.equals(tUUID)) {
            requester.sendMessage(Component.text("You cannot teleport to yourself.", NamedTextColor.RED));
            return false;
        }

        if (DeathTeleportCooldownManager.denyIfOnCooldown(requester)) return false;

        long now = System.currentTimeMillis();
        Long expiry = cooldownExpiry.get(rUUID);
        if (!requester.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            requester.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before using /tpa again.", NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(rUUID)) {
            requester.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        pendingRequests.put(tUUID, new TpaRequest(rUUID, tUUID, now + REQUEST_EXPIRY_MS));

        requester.sendMessage(Component.text("Teleport request sent to ", NamedTextColor.YELLOW)
                .append(Component.text(target.getName(), NamedTextColor.GOLD))
                .append(Component.text(". Expires in 30s.", NamedTextColor.YELLOW)));

        Component acceptBtn = Component.text("[Accept]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tpa accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Accept teleport request", NamedTextColor.GREEN)));
        Component denyBtn = Component.text("[Deny]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/tpa deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Deny teleport request", NamedTextColor.RED)));

        target.sendMessage(
                Component.text(requester.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" wants to teleport to you. ", NamedTextColor.YELLOW))
                        .append(acceptBtn)
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(denyBtn));

        // Schedule request expiry notification
        Bukkit.getScheduler().runTaskLater(Hatlas.instance, () -> {
            TpaRequest current = pendingRequests.get(tUUID);
            if (current != null && current.requesterUUID().equals(rUUID)) {
                pendingRequests.remove(tUUID);
                Player r = Bukkit.getPlayer(rUUID);
                if (r != null) r.sendMessage(Component.text(
                        "Your teleport request to " + target.getName() + " expired.", NamedTextColor.GRAY));
                Player t = Bukkit.getPlayer(tUUID);
                if (t != null) t.sendMessage(Component.text(
                        "Teleport request from " + requester.getName() + " expired.", NamedTextColor.GRAY));
            }
        }, REQUEST_EXPIRY_MS / 50L);

        return true;
    }

    public static boolean acceptRequest(Player target) {
        UUID tUUID = target.getUniqueId();
        TpaRequest request = pendingRequests.remove(tUUID);

        if (request == null) {
            target.sendMessage(Component.text("You have no pending teleport request.", NamedTextColor.RED));
            return false;
        }
        if (System.currentTimeMillis() > request.expiryMs()) {
            target.sendMessage(Component.text("That teleport request has expired.", NamedTextColor.RED));
            return false;
        }

        Player requester = Bukkit.getPlayer(request.requesterUUID());
        if (requester == null) {
            target.sendMessage(Component.text("The requesting player is no longer online.", NamedTextColor.RED));
            return false;
        }

        target.sendMessage(Component.text("Teleport request accepted.", NamedTextColor.GREEN));

        if (requester.isOp()) {
            requester.teleport(target.getLocation());
            requester.sendActionBar(Component.text("Teleported to " + target.getName() + "!", NamedTextColor.GREEN));
        } else {
            requester.sendMessage(Component.text(
                    target.getName() + " accepted. Teleporting in " + countdownSeconds + "s… Don't move!",
                    NamedTextColor.YELLOW));
            startCountdown(requester, target);
        }
        return true;
    }

    public static boolean denyRequest(Player target) {
        UUID tUUID = target.getUniqueId();
        TpaRequest request = pendingRequests.remove(tUUID);

        if (request == null) {
            target.sendMessage(Component.text("You have no pending teleport request.", NamedTextColor.RED));
            return false;
        }

        Player requester = Bukkit.getPlayer(request.requesterUUID());
        target.sendMessage(Component.text("Teleport request denied.", NamedTextColor.RED));
        if (requester != null) {
            requester.sendMessage(Component.text(
                    target.getName() + " denied your teleport request.", NamedTextColor.RED));
        }
        return true;
    }

    private static void startCountdown(Player requester, Player target) {
        UUID rUUID = requester.getUniqueId();
        UUID tUUID = target.getUniqueId();
        Location startLocation = requester.getLocation().clone();

        BukkitRunnable task = new BukkitRunnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                Player r = Bukkit.getPlayer(rUUID);
                if (r == null) {
                    activeTeleports.remove(rUUID);
                    cancel();
                    return;
                }

                Player t = Bukkit.getPlayer(tUUID);
                if (t == null) {
                    activeTeleports.remove(rUUID);
                    cancel();
                    r.sendMessage(Component.text("Teleport cancelled — target went offline.", NamedTextColor.RED));
                    return;
                }

                Location current = r.getLocation();
                double delta = Math.abs(current.getX() - startLocation.getX())
                        + Math.abs(current.getY() - startLocation.getY())
                        + Math.abs(current.getZ() - startLocation.getZ());
                if (delta > 0.1) {
                    activeTeleports.remove(rUUID);
                    cancel();
                    TitleUtil.notify(r, "Teleport cancelled — you moved!", NamedTextColor.RED);
                    return;
                }

                if (remaining > 0) {
                    r.sendActionBar(Component.text(
                            "Teleporting to " + t.getName() + " in " + remaining + "s…", NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(rUUID);
                    cancel();
                    r.teleport(t.getLocation());
                    r.sendActionBar(Component.text("Teleported to " + t.getName() + "!", NamedTextColor.GREEN));
                    if (!r.isOp()) cooldownExpiry.put(rUUID, System.currentTimeMillis() + cooldownMs);
                }
            }
        };

        activeTeleports.put(rUUID, task);
        task.runTaskTimer(Hatlas.instance, 0L, 20L);
    }

    public static boolean cancelTeleport(UUID playerUUID) {
        BukkitRunnable task = activeTeleports.remove(playerUUID);
        if (task == null) return false;
        task.cancel();
        return true;
    }
}
