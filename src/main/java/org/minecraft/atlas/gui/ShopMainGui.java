package org.minecraft.atlas.gui;

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
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopManager;
import org.minecraft.atlas.shop.ShopTier;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop entry point, opened by right-clicking a shop NPC. Two buttons lead to
 * {@link ShopCommonGui} and {@link ShopRareGui}; each shows whether the viewer can open it.
 */
public class ShopMainGui implements AtlasGui {

    private static final int SLOT_COMMON = 11;
    private static final int SLOT_INFO   = 13;
    private static final int SLOT_RARE   = 15;

    private final Shop shop;
    private final Inventory inventory;

    public ShopMainGui(Player player, Shop shop) {
        this.shop = shop;
        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text(ShopManager.displayName(shop), shop.getJob().getColor()));

        inventory.setItem(SLOT_COMMON, buildTierButton(player, ShopTier.COMMON, Material.CHEST));
        inventory.setItem(SLOT_INFO,   buildInfoItem(player));
        inventory.setItem(SLOT_RARE,   buildTierButton(player, ShopTier.RARE, Material.ENDER_CHEST));
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
        ShopTier tier = slot == SLOT_COMMON ? ShopTier.COMMON : slot == SLOT_RARE ? ShopTier.RARE : null;
        if (tier == null) return;

        Component denied = ShopManager.accessError(player, shop, tier);
        if (denied != null) {
            player.sendMessage(denied);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        if (tier == ShopTier.COMMON) new ShopCommonGui(player, shop).open(player);
        else new ShopRareGui(player, shop).open(player);
    }

    private ItemStack buildTierButton(Player player, ShopTier tier, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tier.displayName() + " Items", tier.color())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("On sale", String.valueOf(shop.getCurrentItems(tier).size()), NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Requires", tier == ShopTier.RARE
                ? shop.getJob().getDisplayName() + " job" : "Any job", NamedTextColor.AQUA));
        lore.add(Component.empty());
        Component denied = ShopManager.accessError(player, shop, tier);
        lore.add((denied == null
                ? Component.text("  Click to browse", NamedTextColor.GRAY)
                : Component.text("  ✘ Locked", NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(Player player) {
        ItemStack item = new ItemStack(shop.getJob().getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ShopManager.displayName(shop), shop.getJob().getColor())
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Job", shop.getJob().getDisplayName(), shop.getJob().getColor()));
        lore.add(GuiUtil.loreLine("Your rubies", String.valueOf(ShopManager.countRubies(player)), NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("Restock in",
                GuiUtil.formatTime(ShopManager.getNextResetMs() - System.currentTimeMillis()), NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
