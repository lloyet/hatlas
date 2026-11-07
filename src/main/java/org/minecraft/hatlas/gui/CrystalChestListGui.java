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
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrystalChestListGui implements HatlasGui {

    /** Links each displayed slot to the crystal + local chest index it represents. */
    private record ChestEntry(UUID crystalUUID, int chestIndex, String crystalLabel) {}

    private final String factionName;
    private final UUID mainCrystalUUID;
    private final Inventory inventory;
    private final Map<Integer, ChestEntry> slotToChest = new HashMap<>();

    public CrystalChestListGui(Player player, Faction faction, UUID mainCrystalUUID) {
        this.factionName = faction.getName();
        this.mainCrystalUUID = mainCrystalUUID;

        // Collect every chest from every named crystal in the faction
        List<ChestEntry> allChests = new ArrayList<>();
        for (HatlasCrystal c : HatlasCrystalManager.getFactionCrystals(factionName)) {
            String label = c.getName().isEmpty() ? "Unnamed Crystal" : c.getName();
            for (int i = 0; i < c.getPurchasedChestSizes().size(); i++) {
                allChests.add(new ChestEntry(c.getEntityUUID(), i, label));
            }
        }

        int rows = allChests.isEmpty() ? 1 : Math.min(6, (int) Math.ceil((allChests.size() + 1) / 9.0) + 1);
        int invSize = rows * 9;

        this.inventory = Hatlas.instance.getServer().createInventory(this, invSize,
                Component.text("Chests - " + GuiUtil.truncateFactionName(faction.getName()), faction.getColor()));

        for (int i = 0; i < allChests.size() && i < inventory.getSize() - 1; i++) {
            ChestEntry entry = allChests.get(i);
            HatlasCrystal crystal = HatlasCrystalManager.getCrystal(entry.crystalUUID());
            this.inventory.setItem(i, buildChestItem(i + 1, entry, crystal));
            slotToChest.put(i, entry);
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

        // Back button
        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
            return;
        }

        ChestEntry entry = slotToChest.get(slot);
        if (entry == null) return;

        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }

        Faction faction = FactionManager.getFaction(fn);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        CrystalChestViewGui.open(player, faction, entry.chestIndex(), entry.crystalUUID());
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildChestItem(int number, ChestEntry entry, HatlasCrystal crystal) {
        ItemStack[] contents = crystal != null ? crystal.getChestContents(entry.chestIndex()) : new ItemStack[27];
        int chestSize = crystal != null && entry.chestIndex() < crystal.getPurchasedChestSizes().size()
                ? crystal.getPurchasedChestSizes().get(entry.chestIndex()) : 27;
        int itemCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) itemCount++;
        }

        Material mat;
        if (chestSize > 64) mat = Material.ENDER_CHEST;
        else if (chestSize >= 54) mat = Material.OXIDIZED_COPPER_CHEST;
        else if (chestSize >= 27) mat = Material.COPPER_CHEST;
        else mat = Material.CHEST;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Chest #" + number, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Crystal", entry.crystalLabel(), NamedTextColor.AQUA));
        lore.add(GuiUtil.loreLine("Slots", itemCount + " / " + chestSize, NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to open", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }
}
