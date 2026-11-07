package org.minecraft.hatlas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.UUID;

public class TabListManager {

    private static final NamedTextColor ADMIN_COLOR = NamedTextColor.DARK_RED;

    public static void updatePlayer(Player player) {
        NamedTextColor color;
        if (player.isOp()) {
            color = ADMIN_COLOR;
        } else {
            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
            if (factionName != null) {
                Faction faction = FactionManager.getFaction(factionName);
                color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
            } else {
                color = NamedTextColor.WHITE;
            }
        }
        player.playerListName(Component.text(player.getName(), color));
    }

    public static void updateFactionMembers(String factionName) {
        for (UUID uuid : FactionManager.getFactionPlayers(factionName)) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) updatePlayer(online);
        }
    }
}
