package org.minecraft.atlas.gui;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Faction-wide ruby-only vault. One shared holder per faction so concurrent viewers see the
 * same contents; persists to {@link Faction#getVaultContents()} on close. Item filtering
 * (only ruby gems allowed) is enforced globally by RubyRestrictionListener. Every member may
 * deposit gems, but only players allowed to spend the vault
 * ({@link FactionManager#canSpendVault}: owner or BANKER+) may take gems out; for the others
 * every click that could pull an item out of the vault is cancelled.
 */
public class CrystalVaultGui implements AtlasGui {

    /** factionName → shared holder instance currently open. */
    private static final Map<String, CrystalVaultGui> openVaults = new HashMap<>();

    private final String factionName;
    private final Set<UUID> viewers = new HashSet<>();
    private final Inventory inventory;

    private CrystalVaultGui(Faction faction) {
        this.factionName = faction.getName();

        Component title = Component.text(
                GuiUtil.truncateFactionName(faction.getName()) + " - Vault",
                faction.getColor());

        // Bukkit's chest-style createInventory(holder, size, title) requires size as a multiple
        // of 9. The default vault size is 5 — fall back to a HOPPER-type inventory (5 slots,
        // single row UI) for that case. Upgraded tiers (9/18/27/54) use a normal chest.
        int size = faction.getVaultSize();
        if (size <= 5) {
            this.inventory = Atlas.instance.getServer()
                    .createInventory(this, InventoryType.HOPPER, title);
        } else {
            this.inventory = Atlas.instance.getServer().createInventory(this, size, title);
        }

        ItemStack[] stored = faction.getVaultContents();
        ItemStack[] copy = stored.length <= this.inventory.getSize()
                ? stored
                : java.util.Arrays.copyOf(stored, this.inventory.getSize());
        this.inventory.setContents(copy);
    }

    public static void open(Player player, Faction faction) {
        CrystalVaultGui holder = openVaults.computeIfAbsent(faction.getName(),
                k -> new CrystalVaultGui(faction));
        holder.viewers.add(player.getUniqueId());
        player.openInventory(holder.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // RubyRestrictionListener handles per-item filtering; here only withdrawals are gated.
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (FactionManager.canSpendVault(FactionManager.getFaction(factionName), player.getUniqueId())) return;
        if (canWithdraw(event)) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "Only the faction owner or a banker (or higher) can take rubies out of the vault.",
                    NamedTextColor.RED));
        }
    }

    /** True when this click could move an item out of the vault (top inventory). */
    private boolean canWithdraw(InventoryClickEvent event) {
        boolean inVault = event.getClickedInventory() == inventory;
        return switch (event.getAction()) {
            // Placing from the cursor into the vault, or shift-clicking from the player
            // inventory into it, only deposits.
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, NOTHING -> false;
            case MOVE_TO_OTHER_INVENTORY -> inVault;
            // Double-click collects matching items from both inventories.
            case COLLECT_TO_CURSOR -> true;
            // Pickups, swaps, hotbar swaps, drops, clone…: deny inside the vault.
            default -> inVault;
        };
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Free interaction — RubyRestrictionListener handles per-item filtering.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        Faction faction = FactionManager.getFaction(factionName);
        if (faction != null) {
            // Snapshot back to faction state. Only copy the first vaultSize slots — extras from
            // the rounded-up inventory size are always empty (filter prevents any placement).
            ItemStack[] live = event.getInventory().getContents();
            int realSize = faction.getVaultSize();
            ItemStack[] save = new ItemStack[realSize];
            System.arraycopy(live, 0, save, 0, Math.min(realSize, live.length));
            faction.setVaultContents(save);
            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        }

        viewers.remove(uuid);
        if (viewers.isEmpty()) openVaults.remove(factionName);
    }
}
