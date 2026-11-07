package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.auction.AuctionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Auction system entry point. Two centered buttons: the bundle opens the seller's own
 * listings ({@link AuctionSellListGui}), the spruce hanging sign opens the global market
 * ({@link AuctionMarketListGui}).
 */
public class AuctionMainGui implements HatlasGui {

    private static final int SLOT_OWN_LISTINGS = 20; // row 2, col 2
    private static final int SLOT_MARKET       = 24; // row 2, col 6

    private final Inventory inventory;

    public AuctionMainGui(Player player) {
        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Auction House", NamedTextColor.GOLD));

        int marketCount = AuctionManager.getMarketListings(player.getUniqueId()).size();

        this.inventory.setItem(SLOT_OWN_LISTINGS, buildSellListButton());
        this.inventory.setItem(SLOT_MARKET,       buildMarketButton(marketCount));
        finishGui();
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == SLOT_OWN_LISTINGS) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new AuctionSellListGui(player).open(player);
            return;
        }
        if (slot == SLOT_MARKET) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new AuctionMarketListGui(player).open(player);
            return;
        }
        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
        }
    }

    private static ItemStack buildSellListButton() {
        // No custom lore — vanilla bundle UI shows its own contents tooltip, and the user
        // wants the button surface kept minimal (display name only).
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("My Listings", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMarketButton(int count) {
        ItemStack item = new ItemStack(Material.SPRUCE_HANGING_SIGN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Market", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Listings available: ", NamedTextColor.GRAY)
                .append(Component.text(count, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to browse & buy", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
