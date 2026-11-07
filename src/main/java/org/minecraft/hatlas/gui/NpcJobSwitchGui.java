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
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.job.Job;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Confirmation screen shown when a player interacts with a job NPC.
 * {@code switching=true}  → player already has a different job; warn about reset.
 * {@code switching=false} → player has no job yet; just confirms initial selection.
 */
public class NpcJobSwitchGui implements HatlasGui {

    private final UUID    playerUUID;
    private final Job     npcJob;
    private final boolean switching;
    private final Inventory inventory;

    public NpcJobSwitchGui(Player player, Job npcJob, boolean switching) {
        this.playerUUID = player.getUniqueId();
        this.npcJob     = npcJob;
        this.switching  = switching;

        Component title = switching
                ? Component.text("Switch to " + npcJob.getDisplayName() + "?", NamedTextColor.RED)
                : Component.text("Join " + npcJob.getDisplayName() + "?", NamedTextColor.GREEN);

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27, title);

        String confirmLabel = switching ? "✔ Switch" : "✔ Join";
        ItemStack green = GuiUtil.labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text(confirmLabel, NamedTextColor.GREEN));
        ItemStack red   = GuiUtil.labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));

        for (int slot : GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4,  GuiUtil.emptyPane());
        this.inventory.setItem(22, GuiUtil.emptyPane());
        this.inventory.setItem(GuiUtil.SLOT_CONFIRM_INFO, buildInfoItem(npcJob, switching));
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
            // Require a faction to select or switch a job
            if (FactionManager.getPlayerFaction(player.getUniqueId()) == null) {
                player.sendMessage(Component.text(
                        "You must be in a faction before choosing a job!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            if (switching) {
                JobManager.removeJob(player.getUniqueId());
                JobManager.startJobResetCooldown(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                player.sendMessage(Component.text("Your job has been reset. ", NamedTextColor.YELLOW)
                        .append(Component.text("You cannot select a new job for 24 hours.", NamedTextColor.RED)));
                player.closeInventory();
            } else {
                JobManager.setJob(player.getUniqueId(), npcJob);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(Component.text("You joined ", NamedTextColor.GREEN)
                        .append(Component.text(npcJob.getDisplayName(), npcJob.getColor()))
                        .append(Component.text("!", NamedTextColor.GREEN)));
                new JobMainGui(player).open(player);
            }

        } else if (GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.closeInventory();
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildInfoItem(Job npcJob, boolean switching) {
        ItemStack item = new ItemStack(npcJob.getIcon());
        ItemMeta meta  = item.getItemMeta();

        Component displayName = switching
                ? Component.text("⚠ Switch to " + npcJob.getDisplayName() + "?", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text("Join " + npcJob.getDisplayName() + "?", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Job: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(npcJob.getDisplayName(), npcJob.getColor())
                        .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());

        if (switching) {
            lore.add(Component.text("  Switching will permanently reset", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  your level, XP, and all quests.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("  This action cannot be undone.", NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  You will not be able to select a", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  new job for 24 hours.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Click Confirm to start your journey", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  as a " + npcJob.getDisplayName() + ".", npcJob.getColor())
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
