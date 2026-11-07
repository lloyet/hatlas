package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.auction.AuctionListing;
import org.minecraft.hatlas.auction.AuctionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paged view of the viewer's own auction listings. Layout matches
 * {@link AuctionMarketListGui}: items in slots 0-44 (45/page), prev-page arrow at
 * slot 46, next-page arrow at slot 52, back-barrier at slot 53.
 *
 * <p>Clicking a listing opens {@link AuctionConfirmGui} configured as REMOVE — confirm
 * cancels the listing and returns the item to the player.
 */
public class AuctionSellListGui implements HatlasGui {

    private static final int ITEMS_PER_PAGE  = 45;
    private static final int ITEM_AREA_START = 0;
    private static final int ITEM_AREA_END   = 44;
    private static final int SLOT_PREV_ARROW = 46;
    private static final int SLOT_NEXT_ARROW = 52;

    private final UUID viewerUUID;
    private final Inventory inventory;
    private final int page;
    private final List<AuctionListing> pageListings;

    public AuctionSellListGui(Player player) {
        this(player, 0);
    }

    public AuctionSellListGui(Player player, int page) {
        this.viewerUUID = player.getUniqueId();

        List<AuctionListing> all = AuctionManager.getBySeller(viewerUUID);
        int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        this.page = ((page % totalPages) + totalPages) % totalPages;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("My Listings [Page " + (this.page + 1) + "/" + totalPages + "]",
                        NamedTextColor.AQUA));

        int from = this.page * ITEMS_PER_PAGE;
        int to   = Math.min(from + ITEMS_PER_PAGE, all.size());
        this.pageListings = new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageListings.size(); i++) {
            inventory.setItem(ITEM_AREA_START + i, renderListing(pageListings.get(i), false));
        }
        if (totalPages > 1) {
            inventory.setItem(SLOT_PREV_ARROW, buildPrevArrow(this.page, totalPages));
            inventory.setItem(SLOT_NEXT_ARROW, buildNextArrow(this.page, totalPages));
        }
        finishGui();
    }

    public void open(Player player) { player.openInventory(this.inventory); }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
            return;
        }
        if (slot == SLOT_NEXT_ARROW || slot == SLOT_PREV_ARROW) {
            List<AuctionListing> all = AuctionManager.getBySeller(viewerUUID);
            int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            if (totalPages <= 1) return;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            int delta = slot == SLOT_NEXT_ARROW ? 1 : -1;
            new AuctionSellListGui(player, page + delta).open(player);
            return;
        }
        if (slot < ITEM_AREA_START || slot > ITEM_AREA_END) return;
        int idx = slot - ITEM_AREA_START;
        if (idx >= pageListings.size()) return;

        AuctionListing listing = pageListings.get(idx);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        new AuctionConfirmGui(player, listing, AuctionConfirmGui.Mode.REMOVE).open(player);
    }

    /**
     * Builds a display-only copy of {@code listing.item()} with price + faction + seller +
     * description + action hint appended to lore. Original name and components are preserved.
     * {@code isMarketView} flips the action-hint text and is also used to tag orphan
     * (disbanded-faction) listings in the seller's own sales view.
     */
    static ItemStack renderListing(AuctionListing listing, boolean isMarketView) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        boolean factionAlive = AuctionManager.isFactionAlive(listing);

        lore.add(Component.empty());
        lore.add(Component.text("  Price: ", NamedTextColor.GRAY)
                .append(Component.text(listing.price() + " ruby", NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        String factionLabel = listing.factionName().isEmpty() ? "—" : listing.factionName();
        lore.add(Component.text("  Faction: ", NamedTextColor.GRAY)
                .append(Component.text(factionLabel,
                        factionAlive ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Seller: ", NamedTextColor.GRAY)
                .append(Component.text(sellerName(listing.seller()), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        if (!listing.description().isEmpty()) {
            lore.add(Component.empty());
            for (String line : listing.description().split("\\\\n")) {
                lore.add(Component.text("  " + line, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, true));
            }
        }
        lore.add(Component.empty());
        if (isMarketView) {
            lore.add(Component.text("  Click to buy", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (!factionAlive) {
            lore.add(Component.text("  Faction disbanded — click to retrieve only",
                            NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Click to retrieve", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static String sellerName(UUID uuid) {
        var online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String off = Bukkit.getOfflinePlayer(uuid).getName();
        return off != null ? off : uuid.toString().substring(0, 8);
    }

    static ItemStack buildNextArrow(int currentPage, int totalPages) {
        int nextPage = ((currentPage + 1) % totalPages) + 1;
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Next Page →", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view page " + nextPage + "/" + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack buildPrevArrow(int currentPage, int totalPages) {
        // Wrap: page 0 → last page, otherwise → page-1. Match the modulo wrap used by next.
        int prevPage = ((currentPage - 1 + totalPages) % totalPages) + 1;
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("← Previous Page", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view page " + prevPage + "/" + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
