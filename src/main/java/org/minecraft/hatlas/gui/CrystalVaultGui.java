package org.minecraft.hatlas.gui;

import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.Storage;

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
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.util.GuiUtil;

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
 *
 * <p><b>Live sync.</b> While the GUI is open its inventory is the real vault: code that
 * reads or changes the vault without the GUI ({@link org.minecraft.hatlas.faction.FactionVault}:
 * shop / auction payments, crystal drops; the storage snapshot) first pulls the live
 * contents ({@link #pullLive}) and pushes its change back ({@link #pushLive}). Without
 * this, closing the GUI wrote back the contents it showed and undid a payment made
 * meanwhile (ruby duplication).
 */
public class CrystalVaultGui implements HatlasGui {

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
            this.inventory = Hatlas.instance.getServer()
                    .createInventory(this, InventoryType.HOPPER, title);
        } else {
            this.inventory = Hatlas.instance.getServer().createInventory(this, size, title);
        }

        ItemStack[] stored = faction.getVaultContents();
        ItemStack[] copy = stored.length <= this.inventory.getSize()
                ? stored
                : java.util.Arrays.copyOf(stored, this.inventory.getSize());
        this.inventory.setContents(copy);
    }

    /** Copies the live contents of this faction's open vault GUI into the faction (no-op if closed). */
    public static void pullLive(Faction faction) {
        CrystalVaultGui holder = openVaults.get(faction.getName());
        if (holder != null) holder.storeInto(faction);
    }

    /** Shows the faction's current vault contents in its open vault GUI (no-op if closed). */
    public static void pushLive(Faction faction) {
        CrystalVaultGui holder = openVaults.get(faction.getName());
        if (holder == null) return;
        ItemStack[] stored = faction.getVaultContents();
        ItemStack[] view = new ItemStack[holder.inventory.getSize()];
        for (int i = 0; i < Math.min(stored.length, view.length); i++) {
            view[i] = stored[i] == null ? null : stored[i].clone();
        }
        holder.inventory.setContents(view);
    }

    /** Pulls every open vault GUI into its faction (before a storage snapshot). */
    public static void pullAllLive() {
        for (CrystalVaultGui holder : openVaults.values()) {
            Faction faction = FactionManager.getFaction(holder.factionName);
            if (faction != null) holder.storeInto(faction);
        }
    }

    /**
     * Writes this GUI's contents to the faction. Only the first vaultSize slots are kept
     * (extras from the rounded-up inventory size are always empty). Items are cloned so
     * the faction never holds mirrors of the live inventory.
     */
    private void storeInto(Faction faction) {
        ItemStack[] live = inventory.getContents();
        int realSize = faction.getVaultSize();
        ItemStack[] save = new ItemStack[realSize];
        for (int i = 0; i < Math.min(realSize, live.length); i++) {
            save[i] = live[i] == null ? null : live[i].clone();
        }
        faction.setVaultContents(save);
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
        // Dropping straight out of a vault slot would put faction gems on the ground untracked.
        if (event.getClickedInventory() == inventory && isDrop(event.getAction())) {
            event.setCancelled(true);
            return;
        }
        if (!FactionManager.canSpendVault(FactionManager.getFaction(factionName), player.getUniqueId())
                && canWithdraw(event)) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "Only the faction owner or a banker (or higher) can take rubies out of the vault.",
                    NamedTextColor.RED));
            return;
        }
        trackMove(player);
    }

    private static boolean isDrop(org.bukkit.event.inventory.InventoryAction action) {
        return switch (action) {
            case DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    private int vaultGems() {
        int total = 0;
        for (ItemStack s : inventory.getContents()) {
            if (s != null && org.minecraft.hatlas.Item.RubyItem.isRubyGem(s)) total += s.getAmount();
        }
        return total;
    }

    /**
     * Ruby ledger: compares the vault and the player's gems before the click and one tick
     * later (after the server applied it). Gems the vault gained and the player lost are a
     * deposit, the opposite a withdrawal; anything else (a cancelled click, a throw) is ignored.
     */
    private void trackMove(Player player) {
        int vaultBefore = vaultGems();
        int heldBefore = org.minecraft.hatlas.economy.RubyLedger.gemsHeld(player);
        org.bukkit.Bukkit.getScheduler().runTask(Hatlas.instance, () -> {
            Faction faction = FactionManager.getFaction(factionName);
            if (faction == null || !player.isOnline()) return;
            int dv = vaultGems() - vaultBefore;
            int dp = org.minecraft.hatlas.economy.RubyLedger.gemsHeld(player) - heldBefore;
            org.minecraft.hatlas.economy.Party vault = org.minecraft.hatlas.economy.Party.faction(faction);
            org.minecraft.hatlas.economy.Party member = org.minecraft.hatlas.economy.Party.player(player);
            if (dv > 0 && dp < 0) {
                org.minecraft.hatlas.economy.RubyLedger.record(org.minecraft.hatlas.economy.LedgerKind.VAULT_DEPOSIT,
                        Math.min(dv, -dp), member, vault, player, null, null);
            } else if (dv < 0 && dp > 0) {
                org.minecraft.hatlas.economy.RubyLedger.record(org.minecraft.hatlas.economy.LedgerKind.VAULT_WITHDRAW,
                        Math.min(-dv, dp), vault, member, player, null, null);
            }
        });
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private boolean vaultHasSimilar(ItemStack cursor) {
        if (isEmpty(cursor)) return false;
        for (ItemStack s : inventory.getContents()) {
            if (!isEmpty(s) && s.isSimilar(cursor)) return true;
        }
        return false;
    }

    /** True when this click could move an item out of the vault (top inventory). */
    private boolean canWithdraw(InventoryClickEvent event) {
        boolean inVault = event.getClickedInventory() == inventory;
        return switch (event.getAction()) {
            // Placing from the cursor into the vault, or shift-clicking from the player
            // inventory into it, only deposits.
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, NOTHING -> false;
            case MOVE_TO_OTHER_INVENTORY -> inVault;
            // Number key onto an EMPTY vault slot only moves the hotbar item in (deposit).
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> inVault && !isEmpty(event.getCurrentItem());
            // Double-click collects matching items from both inventories: a withdrawal only
            // if the vault holds such an item.
            case COLLECT_TO_CURSOR -> vaultHasSimilar(event.getCursor());
            // Pickups, swaps, hotbar swaps, drops, clone…: deny inside the vault.
            default -> inVault;
        };
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Free interaction — RubyRestrictionListener handles per-item filtering.
        if (event.getWhoClicked() instanceof Player player) trackMove(player);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        Faction faction = FactionManager.getFaction(factionName);
        if (faction != null) {
            storeInto(faction);
            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        }

        viewers.remove(uuid);
        if (viewers.isEmpty()) openVaults.remove(factionName);
    }
}
