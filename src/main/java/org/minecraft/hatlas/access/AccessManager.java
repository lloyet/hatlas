package org.minecraft.hatlas.access;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.minecraft.hatlas.storage.StorageSettings;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Paid access, Hatlas side: refuses a login without a valid ticket when
 * {@code access.require_ticket} is on. The real gate is the {@code hatlas-proxy} Velocity plugin
 * (hub → faction server, expiry mid-session); this is the defence in depth for a player who
 * reaches the faction server some other way. Fails closed: if the check cannot run, the login
 * is refused (operators still get in with {@code access.ops_bypass}).
 */
public final class AccessManager implements Listener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static AccessSettings settings;
    private static TicketChecker checker;
    private static String unavailableReason;
    private static Logger log;

    private AccessManager() {}

    /** Called once from onEnable, after storage started. Returns the listener to register. */
    public static Listener init(AccessSettings config, StorageSettings.Type storageType, Logger logger) {
        settings = config;
        log = logger;
        unavailableReason = null;
        if (storageType == StorageSettings.Type.POSTGRESQL) {
            checker = new TicketChecker(config.webSchema(), log);
        } else {
            checker = null;
            unavailableReason = "tickets need storage.type: postgresql";
        }
        if (!config.requireTicket()) {
            log.info("Access: login ticket check OFF (access.require_ticket: false)");
        } else if (unavailableReason != null) {
            log.severe("Access: access.require_ticket is on but " + unavailableReason
                    + ": every login is refused" + (config.opsBypass() ? " except operators." : "."));
        } else {
            log.info("Access: login ticket check ON (" + config.webSchema() + ".tickets)"
                    + (config.opsBypass() ? ", operators bypass it" : ""));
        }
        return new AccessManager();
    }

    public static void shutdown() {
        if (checker != null) checker.close();
        checker = null;
    }

    public static boolean isRequired() {
        return settings != null && settings.requireTicket();
    }

    /** Blocking (database query): never call on the main thread. */
    public static TicketChecker.Result check(UUID player) {
        if (checker == null) {
            return TicketChecker.Result.error(unavailableReason == null ? "access check not started" : unavailableReason);
        }
        return checker.check(player);
    }

    public static String format(Instant instant) {
        return DATE.format(instant);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isRequired()) return;
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID id = event.getUniqueId();
        if (settings.opsBypass() && Bukkit.getOfflinePlayer(id).isOp()) return;

        TicketChecker.Result r = check(id);
        switch (r.status()) {
            case VALID -> { }
            case NONE, EXPIRED -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, refusal(r));
            case ERROR -> {
                log.warning("Access: ticket check failed for " + event.getName()
                        + " (" + id + "), login refused: " + r.error());
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text("The access check is unavailable right now. Please try again in a moment.",
                                NamedTextColor.RED));
            }
        }
    }

    private static Component refusal(TicketChecker.Result r) {
        Component msg = r.status() == TicketChecker.Status.EXPIRED
                ? Component.text("Your access expired on " + format(r.validUntil()) + ".", NamedTextColor.RED)
                : Component.text("You have no access ticket for this server.", NamedTextColor.RED);
        String url = settings.renewUrl();
        if (url.isEmpty()) return msg;
        return msg.append(Component.newline()).append(Component.text("Renew it at ", NamedTextColor.GRAY))
                .append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
    }
}
