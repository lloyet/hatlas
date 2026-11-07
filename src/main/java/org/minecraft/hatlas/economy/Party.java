package org.minecraft.hatlas.economy;

import org.bukkit.OfflinePlayer;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.UUID;

/**
 * One side of a ruby move: a player (their inventory) or a faction (its vault).
 * {@code faction} is the player's faction when the move happened (null for a faction party).
 */
public record Party(Type type, String id, String name, String faction) {

    public enum Type { PLAYER, FACTION }

    public static Party player(UUID uuid, String name) {
        return new Party(Type.PLAYER, uuid.toString(), name, FactionManager.getPlayerFaction(uuid));
    }

    public static Party player(OfflinePlayer p) {
        return player(p.getUniqueId(), p.getName());
    }

    public static Party faction(Faction f) {
        return new Party(Type.FACTION, f.getName(), f.getName(), null);
    }

    /** Faction party by name (the faction may already be disbanded). */
    public static Party faction(String name) {
        return new Party(Type.FACTION, name, name, null);
    }

    /** Same owner (type and id), ignoring the name and faction. */
    public boolean sameAs(Party other) {
        return other != null && type == other.type && id.equals(other.id);
    }

    /** The same party with its current faction (a player's faction can change while their drop lies around). */
    public Party refreshed() {
        if (type != Type.PLAYER) return this;
        try {
            return new Party(type, id, name, FactionManager.getPlayerFaction(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return this;
        }
    }
}
