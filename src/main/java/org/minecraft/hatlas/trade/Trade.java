package org.minecraft.hatlas.trade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.gui.TradeGui;

import java.util.ArrayList;
import java.util.List;

public class Trade {

    // -------------------------------------------------------------------------
    // Slot layout (54-slot inventory, 6 rows × 9 cols)
    //
    //  Cols:  0   1   2   3  |  4  |  5   6   7   8
    //  Row 0: L   L   L   L     G     R   R   R   R
    //  Row 1: L   L   L   L     G     R   R   R   R
    //  Row 2: L   L   L   L    CNCL   R   R   R   R
    //  Row 3: L   L   L   L    ACCPT  R   R   R   R
    //  Row 4: L   L   L   L     G     R   R   R   R
    //  Row 5: L   L   L   L     G     R   R   R   R
    //
    //  L = left (editable by the owner of this view)
    //  R = right (mirror of the other player's left, read-only)
    //  G = glass pane separator
    //  CNCL = cancel button (barrier) at slot 22
    //  ACCPT = accept toggle at slot 31
    // -------------------------------------------------------------------------

    public static final int[] LEFT_SLOTS  = { 0,1,2,3, 9,10,11,12, 18,19,20,21, 27,28,29,30, 36,37,38,39, 45,46,47,48 };
    public static final int[] RIGHT_SLOTS = { 5,6,7,8, 14,15,16,17, 23,24,25,26, 32,33,34,35, 41,42,43,44, 50,51,52,53 };
    public static final int[] GLASS_SLOTS = { 4, 13, 40, 49 };
    public static final int   CANCEL_SLOT = 22;
    public static final int   ACCEPT_SLOT = 31;

    private static final int COUNTDOWN_TICKS = 60; // 3 s × 20 ticks/s

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Player initiator;
    private final Player target;
    private final Inventory initiatorInv;
    private final Inventory targetInv;

    private boolean initiatorAccepted = false;
    private boolean targetAccepted    = false;
    private BukkitTask countdownTask  = null;
    private boolean finished          = false;

    // Saved XP – restored after trade completes or is cancelled
    private final int   savedInitiatorLevel;
    private final float savedInitiatorExp;
    private final int   savedTargetLevel;
    private final float savedTargetExp;

    // -------------------------------------------------------------------------
    // Constructor – builds and opens both inventories immediately
    // -------------------------------------------------------------------------

    public Trade(Player initiator, Player target) {
        this.initiator = initiator;
        this.target    = target;

        this.savedInitiatorLevel = initiator.getLevel();
        this.savedInitiatorExp   = initiator.getExp();
        this.savedTargetLevel    = target.getLevel();
        this.savedTargetExp      = target.getExp();

        TradeGui initiatorHolder = new TradeGui(this, true);
        TradeGui targetHolder    = new TradeGui(this, false);

        this.initiatorInv = Bukkit.createInventory(initiatorHolder, 54,
                Component.text("Trade with " + target.getName()));
        this.targetInv = Bukkit.createInventory(targetHolder, 54,
                Component.text("Trade with " + initiator.getName()));

        initiatorHolder.setInventory(this.initiatorInv);
        targetHolder.setInventory(this.targetInv);

        fillDecorations(initiatorInv);
        fillDecorations(targetInv);

        initiator.openInventory(initiatorInv);
        target.openInventory(targetInv);
    }

    // -------------------------------------------------------------------------
    // Decoration helpers
    // -------------------------------------------------------------------------

    private static void fillDecorations(Inventory inv) {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot : GLASS_SLOTS) inv.setItem(slot, glass.clone());
        inv.setItem(CANCEL_SLOT, makeItem(Material.BARRIER,
                Component.text("Cancel Trade", NamedTextColor.RED)));
        inv.setItem(ACCEPT_SLOT, acceptButton(false));
    }

    private static ItemStack acceptButton(boolean accepted) {
        return makeItem(
                accepted ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                accepted
                        ? Component.text("✔ Accepted  —  click to un-accept", NamedTextColor.GREEN)
                        : Component.text("Click to Accept", NamedTextColor.RED)
        );
    }

    private static ItemStack makeItem(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Slot helpers
    // -------------------------------------------------------------------------

    public static boolean isLeftSlot(int slot) {
        for (int s : LEFT_SLOTS)  if (s == slot) return true;
        return false;
    }

    public static boolean isRightSlot(int slot) {
        for (int s : RIGHT_SLOTS) if (s == slot) return true;
        return false;
    }

    public static boolean isGlassSlot(int slot) {
        for (int s : GLASS_SLOTS) if (s == slot) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Item sync — mirrors each player's left side into the other's right side
    // -------------------------------------------------------------------------

    public void sync() {
        for (int leftSlot : LEFT_SLOTS) {
            int rightSlot = leftSlot + 5; // cols 0-3 → cols 5-8
            targetInv.setItem(rightSlot,    initiatorInv.getItem(leftSlot));
            initiatorInv.setItem(rightSlot, targetInv.getItem(leftSlot));
        }
    }

    // -------------------------------------------------------------------------
    // Accept toggle
    // -------------------------------------------------------------------------

    public void toggleAccept(boolean isInitiator) {
        if (isInitiator) initiatorAccepted = !initiatorAccepted;
        else             targetAccepted    = !targetAccepted;

        updateAcceptButtons();

        if (initiatorAccepted && targetAccepted) {
            startCountdown();
        } else if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
            restoreXp();
        }
    }

    private void updateAcceptButtons() {
        initiatorInv.setItem(ACCEPT_SLOT, acceptButton(initiatorAccepted));
        targetInv.setItem(ACCEPT_SLOT,    acceptButton(targetAccepted));
    }

    /** Called whenever a player modifies their left side — resets both acceptances. */
    public void onItemChanged() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
            restoreXp();
        }
        initiatorAccepted = false;
        targetAccepted    = false;
        updateAcceptButtons();
    }

    // -------------------------------------------------------------------------
    // Countdown (runs every tick; updates XP bar, sounds every second)
    // -------------------------------------------------------------------------

    private void startCountdown() {
        int[] ticks = { COUNTDOWN_TICKS };

        countdownTask = Bukkit.getScheduler().runTaskTimer(Hatlas.instance, () -> {
            if (finished) { countdownTask.cancel(); return; }

            if (ticks[0] <= 0) {
                countdownTask.cancel();
                countdownTask = null;
                executeTrade();
                return;
            }

            float progress = (float) ticks[0] / COUNTDOWN_TICKS;
            int seconds    = (int) Math.ceil(ticks[0] / 20.0);

            setXp(initiator, seconds, progress);
            setXp(target,    seconds, progress);

            // Play a tick sound once per second (and on first frame)
            if (ticks[0] == COUNTDOWN_TICKS || ticks[0] % 20 == 0) {
                initiator.playSound(initiator.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                target.playSound(target.getLocation(),       Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }

            ticks[0]--;
        }, 0L, 1L);
    }

    private void setXp(Player player, int level, float progress) {
        player.setLevel(level);
        player.setExp(Math.clamp(progress, 0f, 1f));
    }

    private void restoreXp() {
        initiator.setLevel(savedInitiatorLevel);
        initiator.setExp(savedInitiatorExp);
        target.setLevel(savedTargetLevel);
        target.setExp(savedTargetExp);
    }

    // -------------------------------------------------------------------------
    // Execute trade
    // -------------------------------------------------------------------------

    private void executeTrade() {
        if (finished) return;
        finished = true;

        List<ItemStack> initiatorOffer = collectLeftItems(initiatorInv);
        List<ItemStack> targetOffer    = collectLeftItems(targetInv);

        closeInventories();
        restoreXp();

        giveItems(initiator, targetOffer);
        giveItems(target,    initiatorOffer);

        Component msg = Component.text("Trade completed successfully!", NamedTextColor.GREEN);
        initiator.sendMessage(msg);
        target.sendMessage(msg);
        initiator.playSound(initiator.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        target.playSound(target.getLocation(),       Sound.BLOCK_BELL_USE, 1f, 1f);

        TradeManager.removeTrade(this);
    }

    // -------------------------------------------------------------------------
    // Cancel trade
    // -------------------------------------------------------------------------

    public void cancel(Player cancelledBy) {
        if (finished) return;
        finished = true;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        returnItems(initiator, initiatorInv);
        returnItems(target,    targetInv);
        closeInventories();
        restoreXp();

        Component msg = cancelledBy != null
                ? Component.text(cancelledBy.getName() + " cancelled the trade.", NamedTextColor.RED)
                : Component.text("Trade was cancelled.", NamedTextColor.RED);
        initiator.sendMessage(msg);
        target.sendMessage(msg);
        // Lower pitch bell = cancel sound
        initiator.playSound(initiator.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.5f);
        target.playSound(target.getLocation(),       Sound.BLOCK_BELL_USE, 1f, 0.5f);

        TradeManager.removeTrade(this);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static List<ItemStack> collectLeftItems(Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : LEFT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        return items;
    }

    private static void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private static void returnItems(Player player, Inventory inv) {
        for (int slot : LEFT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone()).values()
                        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                inv.setItem(slot, null);
            }
        }
        // Return cursor item if any
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.getType().isAir()) {
            player.getInventory().addItem(cursor.clone()).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.setItemOnCursor(null);
        }
    }

    /** Closes both inventories. Since finished=true before this is called,
     *  InventoryCloseEvent will see the finished flag and not re-cancel. */
    private void closeInventories() {
        initiator.closeInventory();
        target.closeInventory();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Player getInitiator()          { return initiator; }
    public Player getTarget()             { return target; }
    public Inventory getInitiatorInv()    { return initiatorInv; }
    public Inventory getTargetInv()       { return targetInv; }
    public boolean isInitiatorAccepted()  { return initiatorAccepted; }
    public boolean isTargetAccepted()     { return targetAccepted; }
    public boolean isFinished()           { return finished; }
}