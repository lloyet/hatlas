package org.minecraft.atlas.gui;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.crystal.AtlasCrystal;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.util.GuiUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CrystalChestViewGui implements AtlasGui {

    /** Key "factionName:crystalUUID:chestIndex" → shared holder instance. */
    private static final Map<String, CrystalChestViewGui> openChests = new HashMap<>();

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final int chestIndex;
    private final Set<UUID> viewers = new HashSet<>();
    private final Inventory inventory;

    private CrystalChestViewGui(Faction faction, int chestIndex, UUID crystalEntityUUID) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;
        this.chestIndex        = chestIndex;

        AtlasCrystal crystal = AtlasCrystalManager.getCrystal(crystalEntityUUID);
        int size = crystal != null && chestIndex < crystal.getPurchasedChestSizes().size()
                ? crystal.getPurchasedChestSizes().get(chestIndex) : 27;

        this.inventory = Atlas.instance.getServer().createInventory(this, size,
                Component.text(GuiUtil.truncateFactionName(faction.getName()) + " - Chest #" + (chestIndex + 1), faction.getColor()));

        if (crystal != null) {
            ItemStack[] stored = crystal.getChestContents(chestIndex);
            this.inventory.setContents(stored.length <= size ? stored : java.util.Arrays.copyOf(stored, size));
        }
    }

    /**
     * Opens the chest view for a player. Reuses an existing holder if another
     * faction member already has this chest open.
     */
    public static void open(Player player, Faction faction, int chestIndex, UUID crystalEntityUUID) {
        String key = faction.getName() + ":" + crystalEntityUUID + ":" + chestIndex;
        CrystalChestViewGui holder = openChests.computeIfAbsent(key,
                k -> new CrystalChestViewGui(faction, chestIndex, crystalEntityUUID));
        holder.viewers.add(player.getUniqueId());
        player.openInventory(holder.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Free interaction — do NOT cancel. Allow all item movement.
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Free interaction — do NOT cancel drags either.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Save chest contents to the crystal
        AtlasCrystal crystal = AtlasCrystalManager.getCrystal(crystalEntityUUID);
        if (crystal != null) {
            crystal.setChestContents(chestIndex, event.getInventory().getContents().clone());
            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        }

        viewers.remove(uuid);
        if (viewers.isEmpty()) {
            String key = factionName + ":" + crystalEntityUUID + ":" + chestIndex;
            openChests.remove(key);
        }
    }
}
