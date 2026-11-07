package org.minecraft.hatlas.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;

public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        Faction faction = factionName != null ? FactionManager.getFaction(factionName) : null;
        boolean isOp = player.isOp();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component nameComponent = isOp
                    ? Component.text(player.getName(), NamedTextColor.DARK_RED)
                    : Component.text(player.getName(), NamedTextColor.WHITE);

            Component suffix = Component.text(": ", NamedTextColor.WHITE).append(message);

            if (faction == null) {
                return nameComponent.append(suffix);
            }
            return Component.text("[" + factionName + "] ", faction.getColor())
                    .append(nameComponent)
                    .append(suffix);
        });
    }
}
