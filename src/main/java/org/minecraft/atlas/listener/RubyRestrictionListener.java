package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Item.RubyItem;
import org.minecraft.atlas.block.RubyBlockManager;
import org.minecraft.atlas.gui.CrystalVaultGui;

/**
 * Global guard for ruby items. Allowlist policy — any inventory not explicitly permitted
 * rejects every ruby variant:
 * <ul>
 *   <li>Player inventory + the player's 2×2 crafting grid + creative menu: any ruby allowed.</li>
 *   <li>Crafting table ({@link InventoryType#WORKBENCH}): {@code ruby_block} only (so the
 *       9-gem recipe works).</li>
 *   <li>{@link CrystalVaultGui}: ruby gem only.</li>
 *   <li>Everything else (chest, barrel, ender chest, shulker box, hopper, dropper, dispenser,
 *       furnace, blast furnace, smoker, anvil, smithing, grindstone, stonecutter, loom,
 *       brewing, enchanting, beacon, decorated pot, chiseled bookshelf, faction crystal
 *       chest, …): all ruby variants rejected.</li>
 * </ul>
 *
 * <p>Hopper / dispenser / dropper transfers and world pickups are guarded by
 * {@link InventoryMoveItemEvent} and {@link InventoryPickupItemEvent}.
 */
public final class RubyRestrictionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top    = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();
        if (top == null) return;

        InventoryAction action = event.getAction();
        ItemStack moving = null;
        Inventory destination = null;

        switch (action) {
            case PLACE_ONE, PLACE_SOME, PLACE_ALL, SWAP_WITH_CURSOR -> {
                moving = event.getCursor();
                destination = clicked;
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                moving = event.getCurrentItem();
                // Shift-click moves from the clicked inventory to the OTHER one.
                destination = (clicked == top) ? bottom : top;
            }
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int hotbarBtn = event.getHotbarButton();
                if (hotbarBtn < 0) return;
                moving = bottom.getItem(hotbarBtn);
                destination = clicked;
            }
            default -> { return; }
        }

        if (moving == null || moving.isEmpty()) return;
        if (destination == null) return;

        if (isDisallowed(destination, moving)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendMessage(rejectionMessage(destination, moving));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top    = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        ItemStack moving = event.getOldCursor();
        if (moving == null || moving.isEmpty()) return;

        for (int rawSlot : event.getRawSlots()) {
            Inventory dest = rawSlot < top.getSize() ? top : bottom;
            if (isDisallowed(dest, moving)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p) {
                    p.sendMessage(rejectionMessage(dest, moving));
                }
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (isDisallowed(event.getDestination(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (isDisallowed(event.getInventory(), event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * Core policy: returns true if {@code item} must NOT be allowed into {@code destination}.
     */
    private static boolean isDisallowed(Inventory destination, ItemStack item) {
        boolean isGem = RubyItem.isRubyGem(item);
        // Faction vault: ruby gems only — checked first, so non-ruby items are refused too.
        if (destination.getHolder() instanceof CrystalVaultGui) return !isGem;

        String blockId = RubyBlockManager.customBlockIdForItem(item);
        boolean isRubyBlock = "ruby_block".equals(blockId);
        boolean isRubyOre   = "ruby_ore".equals(blockId) || "deepslate_ruby_ore".equals(blockId);
        if (!isGem && !isRubyBlock && !isRubyOre) return false;

        InventoryType type = destination.getType();

        // Player's own inventory + their 2×2 crafting grid + creative menu — always allowed.
        if (type == InventoryType.PLAYER
                || type == InventoryType.CRAFTING
                || type == InventoryType.CREATIVE) return false;

        // Crafting table (3×3): ruby_block (decomposition recipe) and ruby gem
        // (compaction recipe — 9 gems → 1 block) both allowed. Ores remain forbidden.
        if (type == InventoryType.WORKBENCH) return isRubyOre;

        // Everything else (chest, barrel, ender chest, shulker box, hopper, dropper,
        // dispenser, furnace, smoker, anvil, smithing, grindstone, stonecutter, loom,
        // brewing, enchanting, beacon, decorated pot, chiseled bookshelf, faction crystal
        // chest, …): reject any ruby variant.
        return true;
    }

    private static Component rejectionMessage(Inventory destination, ItemStack item) {
        if (destination.getHolder() instanceof CrystalVaultGui) {
            return Component.text("The faction vault only accepts ruby gems.", NamedTextColor.RED);
        }
        InventoryType type = destination.getType();
        String blockId = RubyBlockManager.customBlockIdForItem(item);
        if (RubyItem.isRubyGem(item)) {
            return Component.text(
                    "Ruby gems can only be stored in your faction vault or placed in a crafting table.",
                    NamedTextColor.RED);
        }
        if ("ruby_block".equals(blockId)) {
            return Component.text("Ruby blocks can only be placed in a crafting table.", NamedTextColor.RED);
        }
        if ("ruby_ore".equals(blockId) || "deepslate_ruby_ore".equals(blockId)) {
            return Component.text("Ruby ores cannot be stored in any container.", NamedTextColor.RED);
        }
        return Component.text("That item cannot be stored here.", NamedTextColor.RED);
    }
}
