package org.minecraft.hatlas.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

/**
 * Spawns and identifies the Auctioneer — an Anvil-profession Villager tagged in PDC.
 * Right-clicking an auctioneer opens {@link org.minecraft.hatlas.gui.AuctionMainGui}.
 * NPC entities persist with the world (no separate YAML), identical to {@code SafeZoneNpcManager}.
 */
public final class AuctionNpcManager {

    public static final String AUCTIONEER_TAG = "auctioneer";

    private static NamespacedKey KEY_NPC;

    public static NamespacedKey getKeyNpc() {
        if (KEY_NPC == null) KEY_NPC = new NamespacedKey(Hatlas.instance, "auction_npc");
        return KEY_NPC;
    }

    private AuctionNpcManager() {}

    public static void summonAuctioneer(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setGravity(true);
            v.setSilent(true);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
            v.setVillagerType(Villager.Type.SAVANNA);
            v.setProfession(Villager.Profession.LIBRARIAN);
            v.customName(Component.text("Auctioneer", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(getKeyNpc(), PersistentDataType.STRING, AUCTIONEER_TAG);
        });
    }

    public static boolean isAuctioneer(Entity entity) {
        if (entity == null) return false;
        return AUCTIONEER_TAG.equals(
                entity.getPersistentDataContainer().get(getKeyNpc(), PersistentDataType.STRING));
    }
}
