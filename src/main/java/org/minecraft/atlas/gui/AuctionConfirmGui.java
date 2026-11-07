package org.minecraft.atlas.gui;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

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
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.Item.RubyItem;
import org.minecraft.atlas.auction.AuctionListing;
import org.minecraft.atlas.auction.AuctionManager;
import org.minecraft.atlas.command.AuctionCommand;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.FactionVault;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared confirm/cancel screen for auction actions. Mode controls the action that fires
 * on green-pane click: REMOVE retrieves the seller's own listing, BUY purchases another
 * player's listing.
 */
public class AuctionConfirmGui implements AtlasGui {

    public enum Mode { REMOVE, BUY }

    private final UUID listingId;
    private final Mode mode;
    private final Inventory inventory;

    public AuctionConfirmGui(Player player, AuctionListing listing, Mode mode) {
        this.listingId = listing.id();
        this.mode = mode;

        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text(mode == Mode.BUY ? "Confirm Purchase?" : "Cancel Listing?",
                        NamedTextColor.GOLD));

        ItemStack green = org.minecraft.atlas.util.GuiUtil.labeledPane(
                Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Confirm", NamedTextColor.GREEN));
        ItemStack red = org.minecraft.atlas.util.GuiUtil.labeledPane(
                Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));
        ItemStack gray = org.minecraft.atlas.util.GuiUtil.emptyPane();

        for (int slot : org.minecraft.atlas.util.GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : org.minecraft.atlas.util.GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4, gray);
        this.inventory.setItem(22, gray);
        this.inventory.setItem(org.minecraft.atlas.util.GuiUtil.SLOT_CONFIRM_INFO, buildInfoItem(listing, mode));
    }

    public void open(Player player) { player.openInventory(this.inventory); }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (org.minecraft.atlas.util.GuiUtil.CONFIRM_GREEN.contains(slot)) {
            AuctionListing listing = AuctionManager.getListing(listingId);
            if (listing == null) {
                player.sendMessage(Component.text("Listing no longer exists.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            boolean success = switch (mode) {
                case REMOVE -> handleRemove(player, listing);
                case BUY    -> handleBuy(player, listing);
            };
            if (!success) {
                // Failure: close the confirm GUI; the player's chat already shows the reason.
                player.closeInventory();
                return;
            }
            // Success: discard the cached parent (which has stale page data — the
            // listing was just removed) and reopen a fresh page of the same kind.
            GuiNavigator.pop(player.getUniqueId());
            switch (mode) {
                case REMOVE -> new AuctionSellListGui(player).open(player);
                case BUY    -> new AuctionMarketListGui(player).open(player);
            }
        } else if (org.minecraft.atlas.util.GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            GuiNavigator.back(player);
        }
    }

    // ── Action handlers ───────────────────────────────────────────────────────

    /** Seller cancels their own listing; item returns to them. Returns true on success. */
    private static boolean handleRemove(Player player, AuctionListing listing) {
        if (!listing.seller().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("This is not your listing.", NamedTextColor.RED));
            return false;
        }
        AuctionManager.removeListing(listing.id());
        giveOrDrop(player, listing.item().clone());
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
        player.sendMessage(Component.text("Listing cancelled — item returned.", NamedTextColor.GREEN));
        return true;
    }

    /**
     * Buyer purchases another's listing. Both buyer and seller must be in factions —
     * payment is withdrawn from the BUYER's faction vault and deposited into the
     * SELLER's faction vault. Atomic: balance checks pass before any state mutation.
     * Overflow into the seller's full vault drops at the buyer's location.
     * Returns true iff the purchase succeeded (item delivered, payment moved).
     */
    private static boolean handleBuy(Player player, AuctionListing listing) {
        if (listing.seller().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You can't buy your own listing.", NamedTextColor.RED));
            return false;
        }
        String buyerFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
        Faction buyerFaction = buyerFactionName == null ? null : FactionManager.getFaction(buyerFactionName);
        if (buyerFaction == null) {
            player.sendMessage(Component.text(
                    "You must be in a faction to buy — payment comes from your faction vault.",
                    NamedTextColor.RED));
            return false;
        }
        if (!FactionManager.canSpendVault(buyerFaction, player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "Only the faction owner or a banker (or higher) can spend from the vault.",
                    NamedTextColor.RED));
            return false;
        }
        int balance = FactionVault.countGems(buyerFaction);
        if (balance < listing.price()) {
            player.sendMessage(Component.text(
                    "Your faction vault has " + balance + " rubies — need " + listing.price() + ".",
                    NamedTextColor.RED));
            return false;
        }
        // Payment is routed to the listing's RECORDED faction (locked at creation).
        // If that faction has been disbanded, the listing is orphaned — hidden from
        // market display by getMarketListings, and any stray buy attempt fails here.
        Faction sellerFaction = listing.factionName().isEmpty()
                ? null : FactionManager.getFaction(listing.factionName());
        if (sellerFaction == null) {
            player.sendMessage(Component.text(
                    "The seller's faction was disbanded — this listing cannot be bought.",
                    NamedTextColor.RED));
            return false;
        }
        // Re-check the listing wasn't removed/bought concurrently.
        if (AuctionManager.removeListing(listing.id()) == null) {
            player.sendMessage(Component.text("Listing was just taken — try another.", NamedTextColor.RED));
            return false;
        }

        if (!FactionVault.withdraw(buyerFaction, listing.price())) {
            // Race-safety refund: re-list (best-effort) and bail.
            AuctionManager.addListing(listing);
            player.sendMessage(Component.text("Payment failed — listing restored.", NamedTextColor.RED));
            return false;
        }

        int overflow = FactionVault.deposit(sellerFaction, listing.price());
        // Save the faction state (both vaults mutated).
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        if (overflow > 0 && player.getLocation().getWorld() != null) {
            ItemStack overflowStack = RubyItem.get("ruby");
            overflowStack.setAmount(overflow);
            player.getWorld().dropItemNaturally(player.getLocation(), overflowStack);
            player.sendMessage(Component.text(
                    "Seller's vault was full — " + overflow + " rubies dropped at your feet for them.",
                    NamedTextColor.YELLOW));
        }

        giveOrDrop(player, listing.item().clone());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.sendMessage(Component.text(
                "Purchased for " + listing.price() + " ruby (paid from your faction vault).",
                NamedTextColor.GREEN));
        return true;
    }

    /** Main-hand → first free slot → drop at player feet. */
    static void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        var inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            inv.setItemInMainHand(item);
            return;
        }
        var overflow = inv.addItem(item);
        if (overflow.isEmpty()) return;
        if (player.getWorld() != null) {
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    // ── Info item ─────────────────────────────────────────────────────────────

    private static ItemStack buildInfoItem(AuctionListing listing, Mode mode) {
        ItemStack item = listing.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
                mode == Mode.BUY ? "Confirm purchase?" : "Cancel this listing?",
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(org.minecraft.atlas.util.GuiUtil.loreLine("Price",
                listing.price() + " ruby", NamedTextColor.RED));
        if (mode == Mode.BUY) {
            lore.add(org.minecraft.atlas.util.GuiUtil.loreLine("Payment",
                    "Sent to seller's faction vault", NamedTextColor.AQUA));
        } else {
            lore.add(org.minecraft.atlas.util.GuiUtil.loreLine("Action",
                    "Item returned to you", NamedTextColor.AQUA));
        }
        if (!listing.description().isEmpty()) {
            lore.add(Component.empty());
            for (String line : listing.description().split("\\\\n")) {
                lore.add(Component.text("  " + line, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, true));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
