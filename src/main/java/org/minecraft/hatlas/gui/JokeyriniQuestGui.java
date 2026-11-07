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
import org.minecraft.hatlas.quest.ActiveQuest;
import org.minecraft.hatlas.quest.GeneratedTask;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.job.JokeyriniManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JokeyriniQuestGui implements HatlasGui {

    private static final int SLOT_QUEST = 13;

    private final UUID playerUUID;
    private final Inventory inventory;

    public JokeyriniQuestGui(Player player) {
        this.playerUUID = player.getUniqueId();
        this.inventory  = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("✦ Jokeyrini's Challenge", NamedTextColor.DARK_PURPLE));

        int jobLevel = 1;
        var data = JobManager.getJobData(player.getUniqueId());
        if (data != null) jobLevel = data.getLevel();

        ActiveQuest active = JokeyriniManager.getActiveQuest(player.getUniqueId());
        if (active != null) {
            this.inventory.setItem(SLOT_QUEST, buildActiveItem(active));
        } else {
            List<GeneratedTask> offer = JokeyriniManager.getDailyOffer(player.getUniqueId(), jobLevel);
            if (!offer.isEmpty()) {
                boolean isSpecial = JokeyriniManager.isDailyOfferSpecial(player.getUniqueId());
                this.inventory.setItem(SLOT_QUEST, buildOfferItem(offer, isSpecial));
            } else {
                this.inventory.setItem(SLOT_QUEST, buildUnavailableItem());
            }
        }

        GuiUtil.fillGray(this.inventory);
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
        if (event.getRawSlot() != SLOT_QUEST) return;

        // Only accept if an offer is pending (not already active, not already completed today)
        if (JokeyriniManager.getActiveQuest(player.getUniqueId()) != null) return;

        int jobLevel = 1;
        var data = JobManager.getJobData(player.getUniqueId());
        if (data != null) jobLevel = data.getLevel();

        List<GeneratedTask> offer = JokeyriniManager.getDailyOffer(player.getUniqueId(), jobLevel);
        if (offer.isEmpty()) return;

        if (JokeyriniManager.acceptDailyOffer(player.getUniqueId(), jobLevel)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.2f);
            player.sendMessage(Component.text("You accepted Jokeyrini's challenge!", NamedTextColor.LIGHT_PURPLE));
            new JokeyriniQuestGui(player).open(player);
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildOfferItem(List<GeneratedTask> tasks, boolean isSpecial) {
        Material icon = isSpecial ? Material.BEACON : Material.NETHER_STAR;
        NamedTextColor titleColor = isSpecial ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE;
        String title = isSpecial ? "★ Legendary Challenge" : "Jokeyrini's Challenge";

        ItemStack item = new ItemStack(icon);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(title, titleColor).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (isSpecial) {
            lore.add(Component.text("  ✦ Special quest — only today!", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Tasks (" + tasks.size() + "):"));

        for (GeneratedTask task : tasks) {
            lore.add(Component.text("  • ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getName(),
                            GuiUtil.difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" [" + GuiUtil.difficultyLabel(task.getDifficulty()) + "]",
                            GuiUtil.difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" ×" + task.getAmount(), NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)));
            String verb = GuiUtil.actionVerb(task.getActionType());
            String targetSuffix = task.getTargets().isEmpty() ? "" : ": " + String.join(", ",
                    task.getTargets().stream().map(GuiUtil::formatMaterial).toList());
            lore.add(Component.text("    " + verb + targetSuffix, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        long totalTime = tasks.stream().mapToLong(GeneratedTask::getTimeLimitMs).sum();
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Time Limit: ").append(GuiUtil.value(GuiUtil.formatTime(totalTime))));

        lore.add(Component.empty());
        lore.add(GuiUtil.label("Reward:"));
        int maxDiff  = tasks.stream().mapToInt(GeneratedTask::getDifficulty).max().orElse(3);
        int keyCount = maxDiff >= 5 ? 3 : maxDiff >= 4 ? 2 : 1;
        lore.add(Component.text("  • " + keyCount + "x ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✦ Donjon Key", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)));

        lore.add(Component.empty());
        lore.add(Component.text("  ▶ Click to accept!", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildActiveItem(ActiveQuest aq) {
        boolean isSpecial = aq.getTasks().stream().anyMatch(t -> t.getDifficulty() >= 5);
        Material icon = isSpecial ? Material.BEACON : Material.PAPER;
        NamedTextColor titleColor = isSpecial ? NamedTextColor.GOLD : NamedTextColor.GOLD;
        String title = isSpecial ? "★ Legendary Challenge (Active)" : "Active Challenge";

        ItemStack item = new ItemStack(icon);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(title, titleColor).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        long remaining = aq.getExpiresAt() - System.currentTimeMillis();
        lore.add(GuiUtil.label("Time remaining: ").append(GuiUtil.value(GuiUtil.formatTime(remaining))));
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Tasks (" + aq.getTasks().size() + "):"));

        for (GeneratedTask task : aq.getTasks()) {
            int gathered = aq.getTaskProgress(task.getTaskId());
            int required = task.getAmount();
            boolean done = gathered >= required;
            lore.add(Component.text("  • ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getName(),
                            done ? NamedTextColor.GREEN : GuiUtil.difficultyColor(task.getDifficulty()))
                            .decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" [" + GuiUtil.difficultyLabel(task.getDifficulty()) + "]",
                            GuiUtil.difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" " + gathered + "/" + required,
                            done ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)));
            String verb = GuiUtil.actionVerb(task.getActionType());
            String targetSuffix = task.getTargets().isEmpty() ? "" : ": " + String.join(", ",
                    task.getTargets().stream().map(GuiUtil::formatMaterial).toList());
            lore.add(Component.text("    " + verb + targetSuffix, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUnavailableItem() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Come back tomorrow...", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("  No challenge available right now.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
}
