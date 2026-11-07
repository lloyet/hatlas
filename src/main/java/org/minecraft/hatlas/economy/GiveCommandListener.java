package org.minecraft.hatlas.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.minecraft.hatlas.Hatlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ruby ledger for the vanilla {@code /give} (rubies are emeralds with the {@code hatlas:ruby}
 * item model, so an admin can create them that way). The command result is not exposed, so
 * the ruby value held by each target is measured before the command and one tick after it;
 * a gain is recorded as {@code ADMIN_GIVE}. Only commands that mention a ruby are measured.
 * Not seen: commands run by plugins through {@code Bukkit.dispatchCommand} (no command event).
 */
public final class GiveCommandListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        measure(event.getPlayer(), event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        measure(event.getSender(), event.getCommand().startsWith("/") ? event.getCommand().substring(1) : event.getCommand());
    }

    /** RCON commands fire their own event (separate handler list), not {@link ServerCommandEvent}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteCommand(org.bukkit.event.server.RemoteServerCommandEvent event) {
        measure(event.getSender(), event.getCommand().startsWith("/") ? event.getCommand().substring(1) : event.getCommand());
    }

    private static void measure(CommandSender sender, String command) {
        String[] args = command.trim().split("\\s+", 3);
        if (args.length < 3) return;
        String label = args[0].toLowerCase(Locale.ROOT);
        if (!label.equals("give") && !label.equals("minecraft:give")) return;
        if (!args[2].toLowerCase(Locale.ROOT).contains("ruby")) return;

        List<Entity> targets;
        try {
            targets = Bukkit.selectEntities(sender, args[1]);
        } catch (IllegalArgumentException badSelector) {
            return; // vanilla reports the error
        }
        Map<Player, Integer> before = new LinkedHashMap<>();
        for (Entity e : targets) {
            if (e instanceof Player p) before.put(p, RubyLedger.rubyValueHeld(p));
        }
        if (before.isEmpty()) return;
        Player actor = sender instanceof Player p ? p : null;
        Bukkit.getScheduler().runTask(Hatlas.instance, () -> {
            for (Map.Entry<Player, Integer> e : before.entrySet()) {
                Player p = e.getKey();
                if (!p.isOnline()) continue;
                int gained = RubyLedger.rubyValueHeld(p) - e.getValue();
                RubyLedger.record(LedgerKind.ADMIN_GIVE, gained, null, Party.player(p), actor,
                        "/give by " + sender.getName(), p.getLocation());
            }
        });
    }
}
