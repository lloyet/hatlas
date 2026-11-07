package org.minecraft.hatlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.quest.ActiveQuest;
import org.minecraft.hatlas.quest.GeneratedTask;
import org.minecraft.hatlas.job.JokeyriniManager;
import org.minecraft.hatlas.quest.QuestTemplate;
import org.minecraft.hatlas.quest.QuestManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobQuestListGui implements HatlasGui {

    private static final int[] QUEST_ITEM_SLOTS   = {11, 15};
    private static final int   SLOT_JOKEYRINI     = 13;

    private final UUID playerUUID;
    private final Inventory inventory;

    public JobQuestListGui(Player player) {
        this.playerUUID = player.getUniqueId();

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("My Quests", NamedTextColor.YELLOW));

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        Faction faction = factionName != null ? FactionManager.getFaction(factionName) : null;
        int factionLevel = faction != null ? faction.getLevel() : 0;

        List<ActiveQuest> quests = QuestManager.getActiveQuests(player.getUniqueId());
        for (int i = 0; i < quests.size() && i < QUEST_ITEM_SLOTS.length; i++) {
            ActiveQuest aq = quests.get(i);
            QuestTemplate qt = QuestManager.resolveQuest(aq);
            this.inventory.setItem(QUEST_ITEM_SLOTS[i], buildActiveQuestItem(aq, qt, factionLevel));
        }

        ActiveQuest jokeyriniQuest = JokeyriniManager.getActiveQuest(player.getUniqueId());
        if (jokeyriniQuest != null) {
            this.inventory.setItem(SLOT_JOKEYRINI, buildJokeyriniQuestItem(jokeyriniQuest));
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

        if (event.getRawSlot() == inventory.getSize() - 1) {
            GuiNavigator.back(player);
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildActiveQuestItem(ActiveQuest aq, QuestTemplate qt, int factionLevel) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (qt == null) {
            meta.displayName(Component.text("Unknown Quest", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            return item;
        }

        meta.displayName(Component.text("Active Quest", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        long remaining = aq.getExpiresAt() - System.currentTimeMillis();
        int expReward = qt.calculateExpReward(factionLevel, QuestManager.getBaseExpReward());
        lore.add(GuiUtil.label("Time remaining: ").append(GuiUtil.value(GuiUtil.formatTime(remaining))));
        lore.add(GuiUtil.label("Experience: ").append(GuiUtil.value(expReward + " XP")));
        lore.add(Component.empty());
        lore.add(GuiUtil.label("Tasks (" + qt.getTasks().size() + "):"));

        for (GeneratedTask task : qt.getTasks()) {
            int gathered = aq.getTaskProgress(task.getTaskId());
            int required = task.getAmount();
            boolean done = gathered >= required;

            NamedTextColor progressColor = done ? NamedTextColor.GREEN : NamedTextColor.WHITE;
            String progressStr = gathered + "/" + required;

            lore.add(Component.text("  • ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getName(), done ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" [" + GuiUtil.difficultyLabel(task.getDifficulty()) + "]",
                            GuiUtil.difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" " + progressStr, progressColor).decoration(TextDecoration.ITALIC, false)));

            String verb = GuiUtil.actionVerb(task.getActionType());
            String targetSuffix = task.getTargets().isEmpty() ? "" : ": " + String.join(", ",
                    task.getTargets().stream().map(GuiUtil::formatMaterial).toList());
            lore.add(Component.text("    " + verb + targetSuffix, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<ItemStack> rewards = qt.getItemRewards();
        if (!rewards.isEmpty()) {
            lore.add(Component.empty());
            lore.add(GuiUtil.label("Item Rewards:"));
            for (ItemStack reward : rewards) {
                lore.add(Component.text("  • " + reward.getAmount() + "x "
                        + GuiUtil.formatMaterial(reward.getType().name()),
                        NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildJokeyriniQuestItem(ActiveQuest aq) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Jokeyrini's Challenge", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

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

        lore.add(Component.empty());
        int maxDiff  = aq.getTasks().stream().mapToInt(GeneratedTask::getDifficulty).max().orElse(3);
        int keyCount = maxDiff >= 4 ? 2 : 1;
        lore.add(GuiUtil.label("Reward: ").append(GuiUtil.value(keyCount + "x ✦ Donjon Key")));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
