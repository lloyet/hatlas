package org.minecraft.hatlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.hatlas.auction.AuctionNpcManager;
import org.minecraft.hatlas.gui.AuctionMainGui;

public class AuctionNpcListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AuctionNpcManager.isAuctioneer(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        // Anyone can browse the auction house. Faction-gated actions (buy, sell) enforce
        // their own checks at action time and surface the relevant error in chat.
        new AuctionMainGui(player).open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (AuctionNpcManager.isAuctioneer(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
