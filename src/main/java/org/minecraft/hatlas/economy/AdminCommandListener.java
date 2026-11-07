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
 * Ruby ledger for the vanilla {@code /give} and {@code /clear} (rubies are emeralds with the
 * {@code hatlas:ruby} item model). The command result is not exposed, so the ruby value held by
 * each target is measured before the command and one tick after it: a gain after {@code /give}
 * is {@code ADMIN_GIVE}, a loss after {@code /clear} is {@code ADMIN_CLEAR}. A {@code /give} is
 * only measured when it mentions a ruby; a {@code /clear} for every target holding rubies.
 * Not seen: commands run by plugins through {@code Bukkit.dispatchCommand} (no command event).
 */
public final class AdminCommandListener implements Listener {

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
        String[] args = command.trim().split("\\s+", 4);
        String label = args[0].toLowerCase(Locale.ROOT);
        boolean give = label.equals("give") || label.equals("minecraft:give");
        boolean clear = label.equals("clear") || label.equals("minecraft:clear");
        if (give && (args.length < 3 || !args[2].toLowerCase(Locale.ROOT).contains("ruby"))) return;
        if (!give && !clear) return;

        List<Entity> targets;
        if (args.length < 2) {
            if (!(sender instanceof Player self)) return;
            targets = List.of(self); // "/clear" alone clears the sender
        } else {
            try {
                targets = Bukkit.selectEntities(sender, args[1]);
            } catch (IllegalArgumentException badSelector) {
                return; // vanilla reports the error
            }
        }
        Map<Player, Integer> before = new LinkedHashMap<>();
        for (Entity e : targets) {
            if (!(e instanceof Player p)) continue;
            int held = RubyLedger.rubyValueHeld(p);
            if (give || held > 0) before.put(p, held);
        }
        if (before.isEmpty()) return;
        Player actor = sender instanceof Player p ? p : null;
        String ref = (give ? "/give by " : "/clear by ") + sender.getName();
        Bukkit.getScheduler().runTask(Hatlas.instance, () -> {
            for (Map.Entry<Player, Integer> e : before.entrySet()) {
                Player p = e.getKey();
                if (!p.isOnline()) continue;
                int delta = RubyLedger.rubyValueHeld(p) - e.getValue();
                Party party = Party.player(p);
                if (give) RubyLedger.record(LedgerKind.ADMIN_GIVE, delta, null, party, actor, ref, p.getLocation());
                else RubyLedger.record(LedgerKind.ADMIN_CLEAR, -delta, party, null, actor, ref, p.getLocation());
            }
        });
    }
}
