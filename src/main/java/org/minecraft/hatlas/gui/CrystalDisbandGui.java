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

import java.util.List;

public class CrystalDisbandGui implements HatlasGui {

    private final String factionName;
    private final Inventory inventory;

    public CrystalDisbandGui(Faction faction) {
        this.factionName = faction.getName();

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("Confirm Disband? - " + GuiUtil.truncateFactionName(faction.getName()), NamedTextColor.RED));

        ItemStack green = GuiUtil.labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Disband", NamedTextColor.GREEN));
        ItemStack red   = GuiUtil.labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));

        ItemStack info = new ItemStack(Material.BARRIER);
        ItemMeta meta  = info.getItemMeta();
        meta.displayName(Component.text("⚠ Disband faction?", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("This action is permanent and", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("cannot be undone!", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(meta);

        for (int slot : GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4,  GuiUtil.emptyPane());
        this.inventory.setItem(22, GuiUtil.emptyPane());
        this.inventory.setItem(GuiUtil.SLOT_CONFIRM_INFO, info);
    }

    /** Factory method for backward compatibility with FactionCommand. */
    public static void open(Player player, Faction faction) {
        CrystalDisbandGui holder = new CrystalDisbandGui(faction);
        player.openInventory(holder.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (GuiUtil.CONFIRM_GREEN.contains(slot)) {
            String fn = FactionManager.getPlayerFaction(player.getUniqueId());
            if (fn == null) { player.closeInventory(); return; }
            Faction faction = FactionManager.getFaction(fn);
            if (!faction.getOwner().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("You are no longer the Owner.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            FactionManager.broadcastToFaction(fn,
                    Component.text("The faction has been disbanded by " + player.getName() + ".", NamedTextColor.RED),
                    player.getUniqueId());
            FactionManager.deleteFaction(player.getUniqueId());
            player.sendMessage(Component.text("Your faction has been disbanded.", NamedTextColor.GREEN));

        } else if (GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.closeInventory();
            player.sendMessage(Component.text("Disband cancelled.", NamedTextColor.YELLOW));
        }
    }
}
