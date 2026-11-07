package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.auction.AuctionListing;
import org.minecraft.hatlas.auction.AuctionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global market view — every listing in the auction except those owned by the viewer
 * (and excluding listings whose owning faction has been disbanded; see
 * {@link AuctionManager#getMarketListings(UUID)}).
 *
 * <p>Layout (54 slots): items in slots 0-44 (45/page), next-page arrow at slot 52 just
 * left of the back-barrier at slot 53. Clicking a listing opens
 * {@link AuctionConfirmGui} in BUY mode.
 */
public class AuctionMarketListGui implements HatlasGui {

    private static final int ITEMS_PER_PAGE  = 45;
    private static final int ITEM_AREA_START = 0;
    private static final int ITEM_AREA_END   = 44;
    private static final int SLOT_PREV_ARROW = 46;
    private static final int SLOT_NEXT_ARROW = 52; // left of the back-barrier (slot 53)

    private final UUID viewerUUID;
    private final Inventory inventory;
    private final int page;
    private final List<AuctionListing> pageListings;

    public AuctionMarketListGui(Player player) {
        this(player, 0);
    }

    public AuctionMarketListGui(Player player, int page) {
        this.viewerUUID = player.getUniqueId();

        List<AuctionListing> all = AuctionManager.getMarketListings(viewerUUID);
        int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        this.page = ((page % totalPages) + totalPages) % totalPages;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Market [Page " + (this.page + 1) + "/" + totalPages + "]",
                        NamedTextColor.GOLD));

        int from = this.page * ITEMS_PER_PAGE;
        int to   = Math.min(from + ITEMS_PER_PAGE, all.size());
        this.pageListings = new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageListings.size(); i++) {
            inventory.setItem(ITEM_AREA_START + i,
                    AuctionSellListGui.renderListing(pageListings.get(i), true));
        }
        if (totalPages > 1) {
            inventory.setItem(SLOT_PREV_ARROW,
                    AuctionSellListGui.buildPrevArrow(this.page, totalPages));
            inventory.setItem(SLOT_NEXT_ARROW,
                    AuctionSellListGui.buildNextArrow(this.page, totalPages));
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
            List<AuctionListing> all = AuctionManager.getMarketListings(viewerUUID);
            int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            if (totalPages <= 1) return;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            int delta = slot == SLOT_NEXT_ARROW ? 1 : -1;
            new AuctionMarketListGui(player, page + delta).open(player);
            return;
        }
        if (slot < ITEM_AREA_START || slot > ITEM_AREA_END) return;
        int idx = slot - ITEM_AREA_START;
        if (idx >= pageListings.size()) return;

        AuctionListing listing = pageListings.get(idx);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        new AuctionConfirmGui(player, listing, AuctionConfirmGui.Mode.BUY).open(player);
    }
}
