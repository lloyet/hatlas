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
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.Item.RubyItem;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionLevelManager;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skill-point upgrade GUI for an hatlas crystal.
 *
 * <p>Paged layout (54 slots, 6 rows):
 * <pre>
 *   Row 0:                col 4 = SP info, col 8 = next-page arrow (when >1 page)
 *   Row 1..4:             one skill kind per row, tiers laid out inline starting at col 1
 *   Row 5 (slot 53):      back button (placed by finishGui)
 * </pre>
 *
 * Skills are paginated through {@link SkillKind#values()} in declaration order, 4 per page.
 * A skill with no tiers defined in factions.yml is skipped — empty rows do not appear.
 */
public class CrystalSkillListGui implements HatlasGui {

    /** Skill kinds in canonical display order. The GUI shows up to {@link #ROWS_PER_PAGE} per page. */
    private enum SkillKind { HP, CLAIMS, CHEST, PROTECTION, HOMES, VAULT, OUTPOST }

    private static final int ROWS_PER_PAGE   = 4;
    private static final int SLOT_SP_INFO    = 4;
    private static final int SLOT_NEXT_ARROW = 8;

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;
    private final int page;

    public CrystalSkillListGui(Player player, Faction faction, UUID crystalEntityUUID) {
        this(player, faction, crystalEntityUUID, 0);
    }

    public CrystalSkillListGui(Player player, Faction faction, UUID crystalEntityUUID, int page) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;

        List<SkillKind> defined = definedSkills();
        int totalPages = Math.max(1, (defined.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        // Wrap into [0, totalPages) so a click on the last page cycles back to page 1.
        this.page = ((page % totalPages) + totalPages) % totalPages;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 54,
                Component.text("Skills - " + GuiUtil.truncateFactionName(faction.getName())
                        + " [Page " + (this.page + 1) + "/" + totalPages + "]",
                        NamedTextColor.LIGHT_PURPLE));

        int sp = faction.getSkillPoints();
        HatlasCrystal protCrystal = HatlasCrystalManager.getCrystal(crystalEntityUUID);

        this.inventory.setItem(SLOT_SP_INFO, buildSpInfoItem(faction));
        if (totalPages > 1) {
            this.inventory.setItem(SLOT_NEXT_ARROW, buildNextArrowItem(this.page, totalPages));
        }

        int pageStart = this.page * ROWS_PER_PAGE;
        for (int rowOffset = 0; rowOffset < ROWS_PER_PAGE; rowOffset++) {
            int kindIdx = pageStart + rowOffset;
            if (kindIdx >= defined.size()) break;
            renderSkillRow(defined.get(kindIdx), rowOffset + 1, sp, faction, protCrystal);
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

        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }
        Faction faction = FactionManager.getFaction(fn);

        // Page-cycle arrow (slot 8 of row 0). Always advances; wraps from last to first.
        if (slot == SLOT_NEXT_ARROW) {
            List<SkillKind> defined = definedSkills();
            int totalPages = Math.max(1, (defined.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
            if (totalPages <= 1) return;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new CrystalSkillListGui(player, faction, crystalEntityUUID, page + 1).open(player);
            return;
        }

        // Skill rows occupy inv rows 1..ROWS_PER_PAGE. Each row is one skill kind whose tiers
        // are placed at cols 1, 2, 3, ... (col 0 is reserved as gutter / future use).
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > ROWS_PER_PAGE || col < 1) return;

        List<SkillKind> defined = definedSkills();
        int kindIdx = page * ROWS_PER_PAGE + (row - 1);
        if (kindIdx >= defined.size()) return;
        SkillKind kind = defined.get(kindIdx);
        int tierIdx = col - 1;

        handleSkillClick(player, faction, fn, kind, tierIdx);
    }

    private void handleSkillClick(Player player, Faction faction, String fn,
                                  SkillKind kind, int tierIdx) {
        switch (kind) {
            case HP -> {
                List<FactionLevelManager.HpTier> tiers = FactionLevelManager.getHpTiers();
                if (tierIdx >= tiers.size()) return;
                FactionLevelManager.HpTier tier = tiers.get(tierIdx);
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.HP, tierIdx);
            }
            case CLAIMS -> {
                List<FactionLevelManager.ClaimTier> tiers = FactionLevelManager.getClaimTiers();
                if (tierIdx >= tiers.size()) return;
                FactionLevelManager.ClaimTier tier = tiers.get(tierIdx);
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.CLAIMS, tierIdx);
            }
            case CHEST -> {
                List<FactionLevelManager.ChestTier> tiers = FactionLevelManager.getChestTiers();
                if (tierIdx >= tiers.size()) return;
                FactionLevelManager.ChestTier tier = tiers.get(tierIdx);
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.CHEST, tierIdx);
            }
            case PROTECTION -> {
                List<FactionLevelManager.ProtectionTier> tiers = FactionLevelManager.getProtectionTiers();
                if (tierIdx >= tiers.size()) return;
                FactionLevelManager.ProtectionTier tier = tiers.get(tierIdx);
                HatlasCrystal target = HatlasCrystalManager.getCrystal(crystalEntityUUID);
                if (target != null && target.hasPurchasedProtection(tier.durationMs())) {
                    player.sendMessage(Component.text(
                            "This protection tier is already purchased on this crystal.", NamedTextColor.YELLOW));
                    return;
                }
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.PROTECTION, tierIdx);
            }
            case HOMES -> {
                List<FactionLevelManager.HomeTier> tiers = FactionLevelManager.getHomeTiers();
                if (tierIdx >= tiers.size()) return;
                if (faction.hasPurchasedHomeTier(tierIdx)) {
                    player.sendMessage(Component.text(
                            "This homes tier is already purchased.", NamedTextColor.YELLOW));
                    return;
                }
                FactionLevelManager.HomeTier tier = tiers.get(tierIdx);
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.HOMES, tierIdx);
            }
            case VAULT -> {
                List<FactionLevelManager.VaultTier> tiers = FactionLevelManager.getVaultTiers();
                if (tierIdx >= tiers.size()) return;
                if (faction.hasPurchasedVaultTier(tierIdx)) {
                    player.sendMessage(Component.text(
                            "This vault tier is already purchased.", NamedTextColor.YELLOW));
                    return;
                }
                FactionLevelManager.VaultTier tier = tiers.get(tierIdx);
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.VAULT, tierIdx);
            }
            case OUTPOST -> {
                if (tierIdx != 0) return;
                FactionLevelManager.OutpostTier tier = FactionLevelManager.getOutpostTier();
                if (tier == null) return;
                if (faction.isOutpostUnlocked()) {
                    player.sendMessage(Component.text("Outpost skill is already unlocked!", NamedTextColor.GOLD));
                    return;
                }
                if (denyForCost(player, faction, tier.cost())) return;
                openConfirm(player, fn, CrystalSkillConfirmGui.SkillPurchaseType.OUTPOST, 0);
            }
        }
    }

    /** Returns true (and messages the player) when the faction can't afford the cost. */
    private static boolean denyForCost(Player player, Faction faction, int cost) {
        if (faction.getSkillPoints() >= cost) return false;
        player.sendMessage(Component.text(
                "Not enough skill points (need " + cost + " SP).", NamedTextColor.RED));
        return true;
    }

    private void openConfirm(Player player, String fn,
                             CrystalSkillConfirmGui.SkillPurchaseType type, int tierIndex) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        new CrystalSkillConfirmGui(player, fn, crystalEntityUUID, type, tierIndex).open(player);
    }

    /** Lays the tier items for {@code kind} along inv-row {@code row} starting at col 1. */
    private void renderSkillRow(SkillKind kind, int row, int sp, Faction faction, HatlasCrystal crystal) {
        int rowStart = row * 9 + 1;
        switch (kind) {
            case HP -> {
                List<FactionLevelManager.HpTier> tiers = FactionLevelManager.getHpTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildHpTierItem(tiers.get(i), i, sp));
            }
            case CLAIMS -> {
                List<FactionLevelManager.ClaimTier> tiers = FactionLevelManager.getClaimTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildClaimTierItem(tiers.get(i), i, sp));
            }
            case CHEST -> {
                List<FactionLevelManager.ChestTier> tiers = FactionLevelManager.getChestTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildChestTierItem(tiers.get(i), i, sp));
            }
            case PROTECTION -> {
                List<FactionLevelManager.ProtectionTier> tiers = FactionLevelManager.getProtectionTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildProtectionTierItem(tiers.get(i), i, sp, crystal));
            }
            case HOMES -> {
                List<FactionLevelManager.HomeTier> tiers = FactionLevelManager.getHomeTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildHomeTierItem(tiers.get(i), i, sp, faction));
            }
            case VAULT -> {
                List<FactionLevelManager.VaultTier> tiers = FactionLevelManager.getVaultTiers();
                for (int i = 0; i < tiers.size(); i++)
                    inventory.setItem(rowStart + i, buildVaultTierItem(tiers.get(i), i, sp, faction));
            }
            case OUTPOST -> {
                FactionLevelManager.OutpostTier tier = FactionLevelManager.getOutpostTier();
                if (tier != null) {
                    inventory.setItem(rowStart, buildOutpostItem(tier, sp, faction.isOutpostUnlocked()));
                }
            }
        }
    }

    /** Returns the skill kinds that have at least one tier defined in factions.yml, in display order. */
    private static List<SkillKind> definedSkills() {
        List<SkillKind> defined = new ArrayList<>();
        if (!FactionLevelManager.getHpTiers().isEmpty())         defined.add(SkillKind.HP);
        if (!FactionLevelManager.getClaimTiers().isEmpty())      defined.add(SkillKind.CLAIMS);
        if (!FactionLevelManager.getChestTiers().isEmpty())      defined.add(SkillKind.CHEST);
        if (!FactionLevelManager.getProtectionTiers().isEmpty()) defined.add(SkillKind.PROTECTION);
        if (!FactionLevelManager.getHomeTiers().isEmpty())       defined.add(SkillKind.HOMES);
        if (!FactionLevelManager.getVaultTiers().isEmpty())      defined.add(SkillKind.VAULT);
        if (FactionLevelManager.getOutpostTier() != null)        defined.add(SkillKind.OUTPOST);
        return defined;
    }

    private static ItemStack buildNextArrowItem(int currentPage, int totalPages) {
        int nextPage = ((currentPage + 1) % totalPages) + 1;
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Next Page →", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view page " + nextPage + "/" + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildOutpostItem(FactionLevelManager.OutpostTier tier, int availableSp, boolean unlocked) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = unlocked ? NamedTextColor.AQUA : (availableSp >= tier.cost() ? NamedTextColor.GREEN : NamedTextColor.RED);
        if (unlocked) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Outpost Skill", color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Unlocks the /faction outpost command,", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  allowing placement of an outpost crystal.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(Component.text("  ✔ Already unlocked", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.empty());
            lore.add(Component.text(availableSp >= tier.cost() ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildSpInfoItem(Faction faction) {
        int sp = faction.getSkillPoints();
        int level = faction.getLevel();
        int exp = faction.getExp();
        int nextExp = FactionLevelManager.getExpRequiredForLevel(level + 1);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Skill Points", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Available SP", String.valueOf(sp),
                sp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("Faction Level", "Lv." + level, NamedTextColor.YELLOW));
        if (level < FactionLevelManager.MAX_LEVEL) {
            lore.add(GuiUtil.loreLine("EXP", exp + " / " + nextExp, NamedTextColor.AQUA));
        } else {
            lore.add(GuiUtil.loreLine("EXP", "MAX LEVEL", NamedTextColor.GOLD));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Earn SP by leveling up your faction.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Spend SP on upgrades below.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildHpTierItem(FactionLevelManager.HpTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        Material mat;
        boolean enchanted;
        if (tier.bonus() >= 200) {
            mat = Material.ENCHANTED_GOLDEN_APPLE;
            enchanted = true;
        } else if (tier.bonus() >= 100) {
            mat = Material.GOLDEN_APPLE;
            enchanted = false;
        } else {
            mat = Material.APPLE;
            enchanted = false;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (enchanted) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("HP Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Bonus HP",   "+" + (int) tier.bonus() + " ♥", NamedTextColor.RED));
        if (tier.regen() > 0) {
            lore.add(GuiUtil.loreLine("Regen",  "+" + formatRegen(tier.regen()) + " ♥/s",
                    NamedTextColor.GREEN));
        }
        lore.add(GuiUtil.loreLine("Cost",       tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildHomeTierItem(FactionLevelManager.HomeTier tier, int index,
                                               int availableSp, Faction faction) {
        boolean owned = faction.hasPurchasedHomeTier(index);
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color =
                owned     ? NamedTextColor.AQUA  :
                canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        if (owned) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Homes Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Bonus Homes", "+" + tier.amount() + " per member", NamedTextColor.AQUA));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        if (owned) {
            lore.add(Component.text("  ✔ Already purchased", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Each tier can be purchased once.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Drops the trailing ".0" on whole numbers (e.g. 3.0 → "3", 1.5 → "1.5"). */
    private static String formatRegen(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private static ItemStack buildVaultTierItem(FactionLevelManager.VaultTier tier, int index,
                                                int availableSp, Faction faction) {
        boolean owned = faction.hasPurchasedVaultTier(index);
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color =
                owned     ? NamedTextColor.AQUA  :
                canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        // Tiers 1-2 (indexes 0-1) use the ruby gem icon; tiers 3-4 (indexes 2-3) the ruby block.
        ItemStack item = index <= 1 ? RubyItem.get("ruby") : RubyItem.get("ruby_block");
        ItemMeta meta = item.getItemMeta();
        if (owned) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Vault Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Vault Size", tier.size() + " slots",
                tier.size() >= 54 ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        if (owned) {
            lore.add(Component.text("  ✔ Already purchased", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Each tier can be purchased once.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildClaimTierItem(FactionLevelManager.ClaimTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Claim Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Bonus Claims", "+" + tier.amount() + " chunks", NamedTextColor.GREEN));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildChestTierItem(FactionLevelManager.ChestTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;
        Material mat;
        if (tier.size() > 64) mat = Material.ENDER_CHEST;
        else if (tier.size() >= 54) mat = Material.OXIDIZED_COPPER_CHEST;
        else if (tier.size() >= 27) mat = Material.COPPER_CHEST;
        else mat = Material.CHEST;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Chest Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Chest Size", tier.size() + " slots",
                tier.size() >= 54 ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildProtectionTierItem(FactionLevelManager.ProtectionTier tier, int index,
                                                     int availableSp, HatlasCrystal crystal) {
        long ms = tier.durationMs();
        long secs = ms / 1000L;
        String timeStr = secs >= 3600 ? (secs / 3600) + "h" : (secs / 60) + "m";

        boolean owned = crystal != null && crystal.hasPurchasedProtection(ms);
        boolean broken = owned && crystal.isProtectionBroken(ms);
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color =
                owned     ? NamedTextColor.AQUA  :
                canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        long mins = ms / 60_000L;
        Material mat;
        boolean enchanted = false;
        if (mins <= 30) {
            mat = Material.LEATHER_CHESTPLATE;
        } else if (mins <= 60) {
            mat = Material.IRON_CHESTPLATE;
        } else if (mins <= 120) {
            mat = Material.GOLDEN_CHESTPLATE;
        } else if (mins <= 240) {
            mat = Material.DIAMOND_CHESTPLATE;
        } else if (mins <= 480) {
            mat = Material.NETHERITE_CHESTPLATE;
        } else {
            mat = Material.NETHERITE_CHESTPLATE;
            enchanted = true;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (enchanted || owned) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Protection Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Duration", timeStr + " immunity", NamedTextColor.AQUA));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        if (owned) {
            lore.add(Component.text("  ✔ Already purchased", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            if (broken) {
                lore.add(Component.text("  Currently broken — regenerating", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("  Each tier can be purchased once.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
