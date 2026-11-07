package org.minecraft.hatlas.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.trade.Trade;

public class TradeGui implements HatlasGui {

    private final Trade trade;
    private final boolean isInitiator;
    private Inventory inv;

    public TradeGui(Trade trade, boolean isInitiator) {
        this.trade = trade;
        this.isInitiator = isInitiator;
    }

    public void setInventory(Inventory inv) { this.inv = inv; }

    public Trade getTrade() { return trade; }
    public boolean isInitiator() { return isInitiator; }

    @Override
    public @NotNull Inventory getInventory() { return inv; }

    // ── HatlasHolder ────────────────────────────────────────────────────────────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (trade.isFinished()) return;

        int rawSlot = event.getRawSlot();

        // ── Click in the player's own inventory (bottom half, rawSlot >= 54) ──
        if (rawSlot >= 54) {
            if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return;
            if (isAccepted()) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            Inventory tradeInv = getPlayerInv();
            for (int leftSlot : Trade.LEFT_SLOTS) {
                ItemStack existing = tradeInv.getItem(leftSlot);
                if (existing == null || existing.getType().isAir()) {
                    tradeInv.setItem(leftSlot, clicked.clone());
                    event.setCurrentItem(null);
                    trade.onItemChanged();
                    trade.sync();
                    return;
                }
            }
            return;
        }

        // ── Click in the trade inventory (top half, rawSlot 0-53) ──
        if (rawSlot == Trade.CANCEL_SLOT) { trade.cancel(player); return; }
        if (rawSlot == Trade.ACCEPT_SLOT) { trade.toggleAccept(isInitiator); return; }
        if (Trade.isGlassSlot(rawSlot))   return;
        if (Trade.isRightSlot(rawSlot))   return;
        if (!Trade.isLeftSlot(rawSlot))   return;
        if (isAccepted()) return;

        Inventory tradeInv = getPlayerInv();
        handleLeftSlotClick(event, tradeInv, rawSlot, player);
        trade.onItemChanged();
        trade.sync();
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!trade.isFinished()) {
            trade.cancel((Player) event.getPlayer());
        }
    }

    // ── Manual item-movement logic for a left-side slot ──────────────────────

    private void handleLeftSlotClick(InventoryClickEvent event, Inventory tradeInv, int slot, Player player) {
        ClickType click   = event.getClick();
        ItemStack cursor  = player.getItemOnCursor();
        ItemStack current = tradeInv.getItem(slot);

        boolean cursorEmpty  = cursor.getType().isAir();
        boolean currentEmpty = current == null || current.getType().isAir();

        switch (click) {
            case LEFT -> {
                if (cursorEmpty && currentEmpty) return;
                if (cursorEmpty) {
                    player.setItemOnCursor(current.clone());
                    tradeInv.setItem(slot, null);
                } else if (currentEmpty) {
                    tradeInv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(null);
                } else if (current.isSimilar(cursor)) {
                    int space = current.getMaxStackSize() - current.getAmount();
                    int add   = Math.min(space, cursor.getAmount());
                    if (add > 0) {
                        ItemStack merged = current.clone();
                        merged.setAmount(current.getAmount() + add);
                        tradeInv.setItem(slot, merged);
                        ItemStack remaining = cursor.clone();
                        remaining.setAmount(cursor.getAmount() - add);
                        player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                    }
                } else {
                    tradeInv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(current.clone());
                }
            }
            case RIGHT -> {
                if (cursorEmpty && currentEmpty) return;
                if (cursorEmpty) {
                    int half = (int) Math.ceil(current.getAmount() / 2.0);
                    ItemStack picked = current.clone(); picked.setAmount(half);
                    ItemStack left   = current.clone(); left.setAmount(current.getAmount() - half);
                    player.setItemOnCursor(picked);
                    tradeInv.setItem(slot, left.getAmount() == 0 ? null : left);
                } else if (currentEmpty) {
                    ItemStack placed = cursor.clone(); placed.setAmount(1);
                    tradeInv.setItem(slot, placed);
                    ItemStack remaining = cursor.clone(); remaining.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                } else if (current.isSimilar(cursor) && current.getAmount() < current.getMaxStackSize()) {
                    ItemStack grown = current.clone(); grown.setAmount(current.getAmount() + 1);
                    tradeInv.setItem(slot, grown);
                    ItemStack remaining = cursor.clone(); remaining.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                } else if (!current.isSimilar(cursor)) {
                    tradeInv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(current.clone());
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (!currentEmpty) {
                    player.getInventory().addItem(current.clone()).values()
                            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    tradeInv.setItem(slot, null);
                }
            }
            default -> {}
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private boolean isAccepted() {
        return isInitiator ? trade.isInitiatorAccepted() : trade.isTargetAccepted();
    }

    private Inventory getPlayerInv() {
        return isInitiator ? trade.getInitiatorInv() : trade.getTargetInv();
    }
}
