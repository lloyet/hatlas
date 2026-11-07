package org.minecraft.hatlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.hatlas.donjon.SmugglerManager;
import org.minecraft.hatlas.gui.SafeZoneGui;
import org.minecraft.hatlas.safezone.SafeZoneNpcManager;

public class SafeZoneNpcListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!SafeZoneNpcManager.isExplorer(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        new SafeZoneGui(player).open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (SafeZoneNpcManager.isExplorer(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
