package org.minecraft.hatlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.minecraft.hatlas.gui.HatlasGui;
import org.minecraft.hatlas.gui.GuiNavigator;

public class GuiListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof HatlasGui holder) holder.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof HatlasGui holder) holder.handleClose(event);
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW
                && event.getPlayer() instanceof Player player) {
            GuiNavigator.clear(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HatlasGui holder) holder.handleDrag(event);
    }
}
