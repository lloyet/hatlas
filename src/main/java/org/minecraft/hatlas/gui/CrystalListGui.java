package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.teleport.HomeTeleportManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CrystalListGui implements HatlasGui {


    private final String factionName;
    private final UUID mainCrystalUUID;
    private final List<HatlasCrystal> crystalList = new ArrayList<>();
    private final Inventory inventory;

    public CrystalListGui(Player player, Faction faction, UUID mainCrystalUUID) {
        this.factionName     = faction.getName();
        this.mainCrystalUUID = mainCrystalUUID;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Crystals - " + GuiUtil.truncateFactionName(faction.getName()), faction.getColor()));

        Collection<HatlasCrystal> crystals = HatlasCrystalManager.getFactionCrystals(factionName);
        int[] slots = GuiUtil.contentSlots54();
        int i = 0;
        for (HatlasCrystal crystal : crystals) {
            if (i >= slots.length) break;
            crystalList.add(crystal);
            this.inventory.setItem(slots[i], buildCrystalItem(crystal, faction));
            i++;
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

        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
            return;
        }

        int[] slots = GuiUtil.contentSlots54();
        int crystalIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { crystalIndex = i; break; }
        }
        if (crystalIndex < 0 || crystalIndex >= crystalList.size()) return;

        HatlasCrystal target = crystalList.get(crystalIndex);

        Location home = target.getHome();
        if (home == null) {
            player.sendMessage(Component.text("This crystal has no home location set.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        String label = target.getName().isEmpty() ? factionName : target.getName();
        HomeTeleportManager.startTeleport(player, home, label);
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildCrystalItem(HatlasCrystal crystal, Faction faction) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta  = item.getItemMeta();

        String displayName = crystal.getName().isEmpty() ? "Unnamed Crystal" : crystal.getName();
        meta.displayName(Component.text(displayName, faction.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("HP", (int) crystal.getHp() + " / " + (int) crystal.getMaxHp() + " ♥",
                NamedTextColor.RED));

        if (crystal.isImmune()) {
            long remainingSec = (crystal.getImmuneUntilMillis() - System.currentTimeMillis()) / 1000;
            lore.add(GuiUtil.loreLine("Immune", remainingSec + "s", NamedTextColor.AQUA));
        }

        Location home = crystal.getHome();
        if (home != null) {
            lore.add(GuiUtil.loreLine("Home",
                    (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ(),
                    NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("  Click to teleport", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("  No home set", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
