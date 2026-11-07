package org.minecraft.atlas.faction;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Item.RubyItem;
import org.minecraft.atlas.gui.CrystalVaultGui;

/**
 * Static helpers for the per-faction ruby vault: counting gems, and dropping some/all gems
 * at a world location while mutating the faction's vault contents.
 *
 * <p>Used by the crystal-damage flow to scatter rubies on protection-break and faction-disband,
 * and by the main GUI / {@code /faction balance} command to read the gem total.
 *
 * <p>Every helper first pulls the contents of an open vault GUI and pushes its change back
 * to it ({@link CrystalVaultGui#pullLive}/{@link CrystalVaultGui#pushLive}), so a payment
 * made while a member has the vault open is not undone when that GUI closes.
 */
public final class FactionVault {

    private FactionVault() {}

    /** Total ruby-gem count across all vault slots. */
    public static int countGems(Faction faction) {
        CrystalVaultGui.pullLive(faction);
        int total = 0;
        for (ItemStack s : faction.getVaultContents()) {
            if (s != null && RubyItem.isRubyGem(s)) total += s.getAmount();
        }
        return total;
    }

    /**
     * Drops up to {@code requested} ruby gems from {@code faction}'s vault at {@code loc},
     * decrementing the matching slots. Items are spawned via {@code dropItemNaturally} so
     * they survive any crystal explosion that already happened before this call. Non-gem
     * vault slots are skipped. Returns the number of gems actually dropped.
     */
    public static int drop(Faction faction, Location loc, int requested) {
        if (loc == null || loc.getWorld() == null || requested <= 0) return 0;
        CrystalVaultGui.pullLive(faction);
        ItemStack[] vault = faction.getVaultContents();
        int dropped = 0;
        int remaining = requested;
        for (int i = 0; i < vault.length && remaining > 0; i++) {
            ItemStack stack = vault[i];
            if (stack == null || !RubyItem.isRubyGem(stack)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            ItemStack drop = stack.clone();
            drop.setAmount(take);
            loc.getWorld().dropItemNaturally(loc, drop);
            int left = stack.getAmount() - take;
            if (left <= 0) vault[i] = null;
            else stack.setAmount(left);
            remaining -= take;
            dropped += take;
        }
        CrystalVaultGui.pushLive(faction);
        return dropped;
    }

    /** Drops every ruby gem in the vault at {@code loc} and clears those slots. */
    public static int dropAll(Faction faction, Location loc) {
        return drop(faction, loc, Integer.MAX_VALUE);
    }

    /**
     * Removes exactly {@code count} ruby gems from the vault. Returns true on full success;
     * returns false WITHOUT modifying the vault if the balance is insufficient.
     */
    public static boolean withdraw(Faction faction, int count) {
        if (count <= 0) return true;
        if (countGems(faction) < count) return false;
        ItemStack[] vault = faction.getVaultContents();
        int remaining = count;
        for (int i = 0; i < vault.length && remaining > 0; i++) {
            ItemStack s = vault[i];
            if (!RubyItem.isRubyGem(s)) continue;
            int take = Math.min(s.getAmount(), remaining);
            int left = s.getAmount() - take;
            if (left <= 0) vault[i] = null;
            else s.setAmount(left);
            remaining -= take;
        }
        CrystalVaultGui.pushLive(faction);
        return true;
    }

    /**
     * Deposits {@code count} ruby gems into the faction's vault (filling existing stacks
     * first, then empty slots). Returns the number of gems that did NOT fit (0 on full success).
     */
    public static int deposit(Faction faction, int count) {
        if (count <= 0) return 0;
        CrystalVaultGui.pullLive(faction);
        ItemStack[] vault = faction.getVaultContents();
        ItemStack template = RubyItem.get("ruby");
        int maxStack = template.getMaxStackSize();
        int remaining = count;

        // Pass 1: top up existing ruby stacks.
        for (int i = 0; i < vault.length && remaining > 0; i++) {
            ItemStack s = vault[i];
            if (!RubyItem.isRubyGem(s)) continue;
            int room = maxStack - s.getAmount();
            if (room <= 0) continue;
            int give = Math.min(room, remaining);
            s.setAmount(s.getAmount() + give);
            remaining -= give;
        }
        // Pass 2: fill empty slots with fresh ruby stacks.
        for (int i = 0; i < vault.length && remaining > 0; i++) {
            if (vault[i] != null && vault[i].getType() != org.bukkit.Material.AIR) continue;
            int give = Math.min(maxStack, remaining);
            ItemStack fresh = template.clone();
            fresh.setAmount(give);
            vault[i] = fresh;
            remaining -= give;
        }
        CrystalVaultGui.pushLive(faction);
        return remaining;
    }

    /**
     * Computes how many gems should drop on protection-break:
     * {@code clamp( max(total × percent, floor), 0, total)}.
     * If the vault has fewer gems than the floor, drops all of them.
     */
    public static int computeProtectionBreakDrop(Faction faction) {
        int total = countGems(faction);
        if (total <= 0) return 0;
        double percent = FactionLevelManager.getVaultDropPercent();
        int floor = FactionLevelManager.getVaultDropMin();
        int byPercent = (int) Math.ceil(total * percent);
        int want = Math.max(byPercent, floor);
        return Math.min(want, total);
    }
}
