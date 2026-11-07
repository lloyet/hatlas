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
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Confirmation GUI for promoting an outpost crystal to the faction's main crystal.
 * Confirming routes /faction home to this crystal's home location.
 */
public class CrystalHomeConfirmGui implements HatlasGui {

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    public CrystalHomeConfirmGui(Player player, String factionName, UUID crystalEntityUUID) {
        this.factionName = factionName;
        this.crystalEntityUUID = crystalEntityUUID;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("Set as Main? - " + GuiUtil.truncateFactionName(factionName), NamedTextColor.GOLD));

        ItemStack green = GuiUtil.labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Confirm", NamedTextColor.GREEN));
        ItemStack red = GuiUtil.labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));
        ItemStack gray = GuiUtil.emptyPane();

        for (int slot : GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4, gray);
        this.inventory.setItem(22, gray);
        this.inventory.setItem(GuiUtil.SLOT_CONFIRM_INFO, buildConfirmInfoItem());
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

        if (GuiUtil.CONFIRM_GREEN.contains(slot)) {
            boolean success = HatlasCrystalManager.setAsMainCrystal(crystalEntityUUID);
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(Component.text(
                        "Crystal set as main — /faction home now teleports here.",
                        NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "Could not set as main — crystal not found or already main.",
                        NamedTextColor.RED));
            }
            player.closeInventory();
        } else if (GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            GuiNavigator.back(player);
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private ItemStack buildConfirmInfoItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Set as Main Crystal?", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        HatlasCrystal crystal = HatlasCrystalManager.getCrystal(crystalEntityUUID);
        String label = (crystal == null || crystal.getName().isEmpty()) ? "Crystal" : crystal.getName();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Faction", factionName, NamedTextColor.AQUA));
        lore.add(GuiUtil.loreLine("Crystal", label, NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("  This crystal will become the new main", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  crystal of the faction. /faction home", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  will teleport here from now on.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
