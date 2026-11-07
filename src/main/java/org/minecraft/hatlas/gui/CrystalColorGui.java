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
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CrystalColorGui implements HatlasGui {

    private static final NamedTextColor[] ALL_COLORS = {
        NamedTextColor.WHITE,        NamedTextColor.GRAY,       NamedTextColor.DARK_GRAY,  NamedTextColor.BLACK,
        NamedTextColor.YELLOW,       NamedTextColor.GOLD,       NamedTextColor.RED,        NamedTextColor.DARK_RED,
        NamedTextColor.GREEN,        NamedTextColor.DARK_GREEN, NamedTextColor.AQUA,       NamedTextColor.DARK_AQUA,
        NamedTextColor.BLUE,         NamedTextColor.DARK_BLUE,  NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE
    };

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    public CrystalColorGui(Player player, Faction faction, UUID crystalEntityUUID) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("Color - " + GuiUtil.truncateFactionName(faction.getName()), NamedTextColor.GOLD));

        for (int i = 0; i < ALL_COLORS.length; i++) {
            this.inventory.setItem(i, buildColorPickerItem(ALL_COLORS[i], ALL_COLORS[i].equals(faction.getColor())));
        }

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

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot == inventory.getSize() - 1) { GuiNavigator.back(player); return; }
        if (slot < 0 || slot >= ALL_COLORS.length) return;

        if (!player.hasPermission("hatlas.faction.color")) {
            player.sendMessage(Component.text(
                    "You don't have permission to change the faction color.", NamedTextColor.RED));
            return;
        }

        NamedTextColor chosen = ALL_COLORS[slot];
        boolean changed = FactionManager.setFactionColor(player.getUniqueId(), chosen);

        if (changed) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            player.sendMessage(Component.text("Faction color changed to ", NamedTextColor.GREEN)
                    .append(Component.text(GuiUtil.colorDisplayName(chosen), chosen))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text(
                    "You don't have permission to change the faction color.", NamedTextColor.RED));
        }
        player.closeInventory();
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildColorPickerItem(NamedTextColor color, boolean selected) {
        ItemStack item = new ItemStack(GuiUtil.colorToTerracotta(color));
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(Component.text(GuiUtil.colorDisplayName(color),
                selected ? NamedTextColor.YELLOW : color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (selected) {
            lore.add(Component.text("  ✔ Current color", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Click to select", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
