package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopItem;
import org.minecraft.atlas.shop.ShopManager;
import org.minecraft.atlas.shop.ShopTier;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared layout for {@link ShopCommonGui} and {@link ShopRareGui}: the tier's current
 * selection laid out in the 28 interior slots of a 54-slot chest, a ruby-balance / restock
 * info item on top, and the back-barrier. Clicking an item buys one.
 */
public abstract class ShopItemsGui implements AtlasGui {

    private static final int SLOT_INFO = 4;

    private final Shop shop;
    private final ShopTier tier;
    private final Inventory inventory;
    // slot -> entry id, captured at render time
    private final Map<Integer, String> slotItems = new HashMap<>();

    protected ShopItemsGui(Player player, Shop shop, ShopTier tier) {
        this.shop = shop;
        this.tier = tier;
        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text(ShopManager.displayName(shop) + " — " + tier.displayName(), tier.color()));

        int[] slots = GuiUtil.contentSlots54();
        List<ShopItem> items = shop.getCurrentItems(tier);
        for (int i = 0; i < items.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], render(items.get(i)));
            slotItems.put(slots[i], items.get(i).id());
        }
        inventory.setItem(SLOT_INFO, buildInfoItem(player));
        finishGui();
    }

    /** Re-renders this GUI for {@code player} (after a purchase). */
    protected abstract ShopItemsGui reopen(Player player);

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
        String itemId = slotItems.get(slot);
        if (itemId == null) return;
        if (ShopManager.purchase(player, shop, tier, itemId)) {
            reopen(player).open(player); // refresh the ruby balance
        }
    }

    private ItemStack render(ShopItem shopItem) {
        ItemStack item = shopItem.createStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text(shopItem.name(), tier.color()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Price", shopItem.price() + " ruby", NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to buy", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tier.displayName() + " Items", tier.color())
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Your rubies", String.valueOf(ShopManager.countRubies(player)), NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("Restock in",
                GuiUtil.formatTime(ShopManager.getNextResetMs() - System.currentTimeMillis()), NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
