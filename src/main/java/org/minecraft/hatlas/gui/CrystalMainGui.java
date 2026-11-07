package org.minecraft.hatlas.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionLevelManager;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.faction.FactionRole;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.quest.QuestManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CrystalMainGui implements HatlasGui {

    private static final int SLOT_CRYSTAL_LIST = 10; // row 1, col 1
    private static final int SLOT_FACTION_INFO = 13; // row 1, col 4 (center)
    private static final int SLOT_COLOR_INFO   = 16; // row 1, col 7
    private static final int SLOT_CHEST_BTN    = 28; // row 3, col 1
    private static final int SLOT_UPGRADES_BTN = 30; // row 3, col 3
    private static final int SLOT_VAULT_BTN    = 32; // row 3, col 5
    private static final int SLOT_QUESTS_BTN   = 34; // row 3, col 7

    private final UUID playerUUID;
    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    public CrystalMainGui(Player player, Faction faction, HatlasCrystal crystal) {
        this.playerUUID        = player.getUniqueId();
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystal.getEntity().getUniqueId();

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Crystal Menu - " + GuiUtil.truncateFactionName(faction.getName()), faction.getColor()));
        this.inventory.setItem(SLOT_CRYSTAL_LIST, buildCrystalListButton(faction));
        this.inventory.setItem(SLOT_FACTION_INFO, buildFactionInfoItem(faction, crystal));
        this.inventory.setItem(SLOT_COLOR_INFO,   buildColorItem(faction));
        this.inventory.setItem(SLOT_CHEST_BTN,    buildChestButton(faction));
        this.inventory.setItem(SLOT_UPGRADES_BTN, buildUpgradesButton(faction));
        this.inventory.setItem(SLOT_VAULT_BTN,    buildVaultButton(faction));
        this.inventory.setItem(SLOT_QUESTS_BTN,   buildQuestsButton(player));
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
        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }
        Faction faction = FactionManager.getFaction(fn);

        if (slot == SLOT_FACTION_INFO) {
            HatlasCrystal crystal = HatlasCrystalManager.getCrystal(crystalEntityUUID);
            if (crystal == null) {
                player.sendMessage(Component.text("Crystal not found.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            // Shift-click keeps the rename flow accessible from this same item.
            if (event.isShiftClick()) {
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                player.closeInventory();
                openRenameDialog(player, factionName, crystalEntityUUID, null);
                return;
            }
            // Plain click opens the "Set as Main" confirm — gated to owner/leader, and only
            // meaningful when the crystal isn't already the main of the faction.
            boolean isOwner  = faction.getOwner().equals(player.getUniqueId());
            boolean isLeader = faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
            if (!isOwner && !isLeader) {
                player.sendMessage(Component.text(
                        "Only the faction owner or a leader can set the main crystal.",
                        NamedTextColor.RED));
                return;
            }
            if (!crystal.isOutpost()) {
                player.sendMessage(Component.text(
                        "This crystal is already the main crystal of the faction.",
                        NamedTextColor.YELLOW));
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalHomeConfirmGui(player, factionName, crystalEntityUUID).open(player);
            return;
        }

        if (slot == SLOT_CRYSTAL_LIST) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalListGui(player, faction, crystalEntityUUID).open(player);
            return;
        }

        if (slot == SLOT_CHEST_BTN) {
            int available = HatlasCrystalManager.getFactionCrystals(faction.getName())
                    .stream().mapToInt(c -> c.getPurchasedChestSizes().size()).sum();
            if (available == 0) {
                player.sendMessage(Component.text(
                        "Your faction has no chests yet. Purchase a chest upgrade via the Skills menu.",
                        NamedTextColor.YELLOW));
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalChestListGui(player, faction, crystalEntityUUID).open(player);
            return;
        }

        if (slot == SLOT_COLOR_INFO) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalColorGui(player, faction, crystalEntityUUID).open(player);
            return;
        }

        if (slot == SLOT_UPGRADES_BTN) {
            HatlasCrystal crystal = HatlasCrystalManager.getCrystal(crystalEntityUUID);
            if (crystal == null) {
                player.sendMessage(Component.text("Crystal not found in this faction.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalSkillListGui(player, faction, crystalEntityUUID).open(player);
        }

        if (slot == SLOT_VAULT_BTN) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            CrystalVaultGui.open(player, faction);
            return;
        }

        if (slot == SLOT_QUESTS_BTN) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new JobMainGui(player).open(player);
        }

        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────

    static void openRenameDialog(Player player, String factionName, UUID crystalEntityUUID, String errorMsg) {
        List<DialogBody> body = new ArrayList<>();
        if (errorMsg != null) {
            body.add(DialogBody.plainMessage(Component.text(errorMsg, NamedTextColor.RED)));
        }
        body.add(DialogBody.plainMessage(
                Component.text("Enter a new name (single word, no spaces).", NamedTextColor.GRAY)));

        DialogBase base = DialogBase.builder(Component.text("Rename Hatlas Crystal", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(List.of(
                        DialogInput.text("name", Component.text("Crystal Name"))
                                .maxLength(32)
                                .initial("")
                                .labelVisible(true)
                                .build()
                ))
                .build();

        ActionButton submitButton = ActionButton.builder(Component.text("Confirm", NamedTextColor.GREEN))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> {
                    String name = response.getText("name");
                    if (name == null || name.isBlank()) {
                        openRenameDialog(player, factionName, crystalEntityUUID, "Name cannot be empty.");
                        return;
                    }
                    if (name.contains(" ")) {
                        openRenameDialog(player, factionName, crystalEntityUUID, "Name cannot contain spaces.");
                        return;
                    }
                    HatlasCrystal crystal = HatlasCrystalManager.getCrystal(crystalEntityUUID);
                    if (crystal == null) {
                        player.sendMessage(Component.text("Crystal no longer exists.", NamedTextColor.RED));
                        return;
                    }
                    Collection<HatlasCrystal> fCrystals = HatlasCrystalManager.getFactionCrystals(factionName);
                    boolean takenByOther = fCrystals.stream().anyMatch(c ->
                            name.equals(c.getName()) && !c.getEntity().getUniqueId().equals(crystalEntityUUID));
                    if (takenByOther) {
                        openRenameDialog(player, factionName, crystalEntityUUID, "'" + name + "' is already taken.");
                        return;
                    }
                    crystal.setName(name.trim());
                    crystal.getEntity().getPersistentDataContainer()
                            .set(HatlasCrystalManager.getKeyName(), PersistentDataType.STRING, name.trim());
                    crystal.updateNametag();
                    player.sendMessage(Component.text("Crystal renamed to '" + name + "'!", NamedTextColor.GREEN));
                    FactionManager.broadcastToFaction(factionName,
                            Component.text("Hatlas Crystal renamed to '" + name + "' by "
                                    + player.getName() + "!", NamedTextColor.GOLD),
                            player.getUniqueId());
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();

        Dialog dialog = Dialog.create(factory ->
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(List.of(submitButton), null, 1))
        );

        player.showDialog(dialog);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildCrystalListButton(Faction faction) {
        Collection<HatlasCrystal> crystals = HatlasCrystalManager.getFactionCrystals(faction.getName());

        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Hatlas Crystals", faction.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Count", String.valueOf(crystals.size()), NamedTextColor.YELLOW));
        if (!crystals.isEmpty()) {
            lore.add(Component.empty());
            for (HatlasCrystal c : crystals) {
                String label = c.getName().isEmpty() ? "Unnamed" : c.getName();
                lore.add(Component.text("  • " + label, faction.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(" [" + (int) c.getHp() + "/" + (int) c.getMaxHp() + " ♥]",
                                NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view crystals", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildFactionInfoItem(Faction faction, HatlasCrystal crystal) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta  = item.getItemMeta();

        String displayName = crystal.getName().isEmpty() ? faction.getName() : crystal.getName();
        meta.displayName(Component.text(displayName, faction.getColor())
                .decoration(TextDecoration.ITALIC, false));

        int level   = faction.getLevel();
        int exp     = faction.getExp();
        int nextExp = FactionLevelManager.getExpRequiredForLevel(level + 1);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Faction", faction.getName(), faction.getColor()));
        lore.add(GuiUtil.loreLine("Level",   "Lv." + level, NamedTextColor.YELLOW));
        if (level < FactionLevelManager.MAX_LEVEL) {
            lore.add(GuiUtil.loreLine("EXP", exp + " / " + nextExp, NamedTextColor.GREEN));
        } else {
            lore.add(GuiUtil.loreLine("EXP", "MAX LEVEL", NamedTextColor.GOLD));
        }
        lore.add(GuiUtil.loreLine("Skill Points", String.valueOf(faction.getSkillPoints()), NamedTextColor.LIGHT_PURPLE));
        lore.add(GuiUtil.loreLine("Role", crystal.isOutpost() ? "Outpost" : "Main",
                crystal.isOutpost() ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        lore.add(Component.empty());
        if (crystal.isOutpost()) {
            lore.add(Component.text("  Click to set as main crystal", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Already the main crystal", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("  Shift-click to rename", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildChestButton(Faction faction) {
        // Count total chests across all crystals of this faction
        int available = HatlasCrystalManager.getFactionCrystals(faction.getName())
                .stream().mapToInt(c -> c.getPurchasedChestSizes().size()).sum();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Faction Chests", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Available", String.valueOf(available),
                available > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text(available > 0
                        ? "  Click to open chests"
                        : "  Reach an upgrade level to unlock",
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildColorItem(Faction faction) {
        ItemStack item = new ItemStack(GuiUtil.colorToTerracotta(faction.getColor()));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Faction Color", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  " + GuiUtil.colorDisplayName(faction.getColor()), faction.getColor())
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to change color", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUpgradesButton(Faction faction) {
        int sp = faction.getSkillPoints();
        NamedTextColor nameColor = sp > 0 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE;

        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Skills", nameColor)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Skill Points", String.valueOf(sp),
                sp > 0 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to spend skill points on upgrades", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildVaultButton(Faction faction) {
        ItemStack item = org.minecraft.hatlas.Item.RubyItem.get("ruby");
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Faction Vault", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        int gems = org.minecraft.hatlas.faction.FactionVault.countGems(faction);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Size", faction.getVaultSize() + " slots", NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Ruby Balance", gems + (gems == 1 ? " gem" : " gems"),
                gems > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("  Stores ruby gems only.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Click to open", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildQuestsButton(Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Quests", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (JobManager.hasJob(player.getUniqueId())) {
            var data = JobManager.getJobData(player.getUniqueId());
            int active = QuestManager.getActiveQuests(player.getUniqueId()).size();
            lore.add(GuiUtil.loreLine("Job",    data.getJob().getDisplayName(), data.getJob().getColor()));
            lore.add(GuiUtil.loreLine("Active", active + " / 2", NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("  No job selected", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Visit a job NPC to get started", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("  Click to view quests", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
