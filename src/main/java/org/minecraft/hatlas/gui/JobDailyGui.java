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
import org.minecraft.hatlas.quest.GeneratedTask;
import org.minecraft.hatlas.quest.QuestTemplate;
import org.minecraft.hatlas.quest.QuestManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobDailyGui implements HatlasGui {

    private static final int[] DAILY_ITEM_SLOTS = {10, 13, 16};

    private final UUID playerUUID;
    private final Inventory inventory;

    public JobDailyGui(Player player) {
        this.playerUUID = player.getUniqueId();
        this.inventory  = Hatlas.instance.getServer().createInventory(this, 27, Component.text("Daily Quests", NamedTextColor.GOLD));

        List<QuestTemplate> offered = QuestManager.getDailyOfferedQuests(player.getUniqueId());
        boolean locked = QuestManager.isDailyLocked(player.getUniqueId());

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        Faction faction = factionName != null ? FactionManager.getFaction(factionName) : null;
        int factionLevel = faction != null ? faction.getLevel() : 0;

        for (int i = 0; i < offered.size() && i < DAILY_ITEM_SLOTS.length; i++) {
            QuestTemplate qt = offered.get(i);
            boolean selected = QuestManager.isDailyQuestSelected(player.getUniqueId(), qt.getId());
            if (!selected) {
                this.inventory.setItem(DAILY_ITEM_SLOTS[i], buildDailyQuestItem(qt, locked, factionLevel));
            }
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

        if (QuestManager.isDailyLocked(player.getUniqueId())) return;

        List<QuestTemplate> offered = QuestManager.getDailyOfferedQuests(player.getUniqueId());
        for (int i = 0; i < offered.size() && i < DAILY_ITEM_SLOTS.length; i++) {
            if (DAILY_ITEM_SLOTS[i] != slot) continue;
            QuestTemplate qt = offered.get(i);

            if (QuestManager.isDailyQuestSelected(player.getUniqueId(), qt.getId())) return;

            if (QuestManager.selectDailyQuest(player.getUniqueId(), qt.getId())) {
                int taskCount = qt.getTasks().size();
                player.sendMessage(
                    Component.text("Quest accepted! ", NamedTextColor.GREEN)
                        .append(Component.text("(" + taskCount + " task" + (taskCount > 1 ? "s" : "") + ")",
                                NamedTextColor.YELLOW))
                );
            }
            new JobDailyGui(player).open(player);
            return;
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildDailyQuestItem(QuestTemplate qt, boolean locked, int factionLevel) {
        Material mat = locked ? Material.GRAY_DYE : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor nameColor = locked ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW;
        meta.displayName(Component.text("Daily Quest", nameColor).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.label("Tasks (" + qt.getTasks().size() + "):"));

        for (GeneratedTask task : qt.getTasks()) {
            lore.add(Component.text("  • ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" [" + GuiUtil.difficultyLabel(task.getDifficulty()) + "]",
                            GuiUtil.difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" ×" + task.getAmount(), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)));

            String verb = GuiUtil.actionVerb(task.getActionType());
            String targetSuffix = task.getTargets().isEmpty() ? "" : ": " + String.join(", ",
                    task.getTargets().stream().map(GuiUtil::formatMaterial).toList());
            lore.add(Component.text("    " + verb + targetSuffix, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        int expReward = qt.calculateExpReward(factionLevel, QuestManager.getBaseExpReward());
        lore.add(GuiUtil.label("Experience: ").append(GuiUtil.value(expReward + " XP")));
        lore.add(GuiUtil.label("Time Limit: ").append(GuiUtil.value(GuiUtil.formatTime(qt.getTimeLimitMs()))));

        List<ItemStack> rewards = qt.getItemRewards();
        if (!rewards.isEmpty()) {
            lore.add(GuiUtil.label("Item Rewards:"));
            for (ItemStack reward : rewards) {
                lore.add(Component.text("  • " + reward.getAmount() + "x "
                        + GuiUtil.formatMaterial(reward.getType().name()),
                        NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        if (locked) {
            lore.add(Component.text("  Selection locked for today.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Click to select.", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
