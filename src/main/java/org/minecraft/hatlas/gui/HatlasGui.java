package org.minecraft.hatlas.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.minecraft.hatlas.util.GuiUtil;

public interface HatlasGui extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
    default void handleClose(InventoryCloseEvent event) {}
    default void handleDrag(InventoryDragEvent event) { event.setCancelled(true); }

    /** Places the back-barrier at the last slot then fills remaining slots with gray panes. */
    default void finishGui() {
        Inventory inv = getInventory();
        inv.setItem(inv.getSize() - 1, GuiUtil.buildBackItem("Back"));
        GuiUtil.fillGray(inv);
    }
}
