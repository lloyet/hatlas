package org.minecraft.hatlas.safezone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Illusioner;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

/**
 * Spawns and identifies the Explorer NPC — an Illusioner riding a Camel that opens a
 * safe-zone teleport menu when right-clicked.
 */
public final class SafeZoneNpcManager {

    public static final String EXPLORER_TAG = "explorer";

    private static NamespacedKey KEY_NPC;

    public static NamespacedKey getKeyNpc() {
        if (KEY_NPC == null) KEY_NPC = new NamespacedKey(Hatlas.instance, "safezone_npc");
        return KEY_NPC;
    }

    private SafeZoneNpcManager() {}

    /** Spawns the Explorer (Illusioner mounted on a Camel) at the given location. */
    public static void summonExplorer(Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        Camel camel = loc.getWorld().spawn(loc, Camel.class, c -> {
            c.setAI(false);
            c.setInvulnerable(true);
            c.setGravity(true);
            c.setRemoveWhenFarAway(false);
            c.setPersistent(true);
            c.setBreed(false);
            c.getPersistentDataContainer().set(getKeyNpc(), PersistentDataType.STRING, EXPLORER_TAG);
        });

        Illusioner ill = loc.getWorld().spawn(loc, Illusioner.class, i -> {
            i.setAI(false);
            i.setGravity(true);
            i.setInvulnerable(true);
            i.setRemoveWhenFarAway(false);
            i.setPersistent(true);
            i.customName(Component.text("Explorer", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            i.setCustomNameVisible(true);
            i.getPersistentDataContainer().set(getKeyNpc(), PersistentDataType.STRING, EXPLORER_TAG);
        });

        camel.addPassenger(ill);
    }

    /** Returns true if the entity is part of an Explorer NPC (camel or illusioner). */
    public static boolean isExplorer(Entity entity) {
        if (entity == null) return false;
        return EXPLORER_TAG.equals(
                entity.getPersistentDataContainer().get(getKeyNpc(), PersistentDataType.STRING));
    }
}
