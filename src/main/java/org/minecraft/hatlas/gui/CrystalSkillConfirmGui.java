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
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.faction.FactionLevelManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CrystalSkillConfirmGui implements HatlasGui {

    public enum SkillPurchaseType { HP, CLAIMS, CHEST, PROTECTION, OUTPOST, HOMES, VAULT }

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final SkillPurchaseType type;
    private final int tierIndex;
    private final Inventory inventory;

    public CrystalSkillConfirmGui(Player player, String factionName, UUID crystalEntityUUID,
                                  SkillPurchaseType type, int tierIndex) {
        this.factionName = factionName;
        this.crystalEntityUUID = crystalEntityUUID;
        this.type = type;
        this.tierIndex = tierIndex;

        this.inventory = Hatlas.instance.getServer().createInventory(this, 27,
                Component.text("Confirm Upgrade? - " + GuiUtil.truncateFactionName(factionName), NamedTextColor.GOLD));

        ItemStack green = GuiUtil.labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Confirm", NamedTextColor.GREEN));
        ItemStack red = GuiUtil.labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));
        ItemStack gray = GuiUtil.emptyPane();

        for (int slot : GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4, gray);
        this.inventory.setItem(22, gray);
        this.inventory.setItem(GuiUtil.SLOT_CONFIRM_INFO, buildConfirmInfoItem(type, tierIndex));
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
            boolean success = switch (type) {
                case HP -> {
                    List<FactionLevelManager.HpTier> tiers = FactionLevelManager.getHpTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseHpUpgrade(factionName, crystalEntityUUID, tiers.get(tierIndex));
                }
                case CLAIMS -> {
                    List<FactionLevelManager.ClaimTier> tiers = FactionLevelManager.getClaimTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseClaimUpgrade(factionName, crystalEntityUUID, tiers.get(tierIndex));
                }
                case CHEST -> {
                    List<FactionLevelManager.ChestTier> tiers = FactionLevelManager.getChestTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseChestUpgrade(factionName, crystalEntityUUID, tiers.get(tierIndex));
                }
                case PROTECTION -> {
                    List<FactionLevelManager.ProtectionTier> tiers = FactionLevelManager.getProtectionTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseProtectionUpgrade(factionName, crystalEntityUUID, tiers.get(tierIndex));
                }
                case OUTPOST -> {
                    FactionLevelManager.OutpostTier tier = FactionLevelManager.getOutpostTier();
                    if (tier == null) yield false;
                    yield HatlasCrystalManager.purchaseOutpostUpgrade(factionName, crystalEntityUUID, tier);
                }
                case HOMES -> {
                    List<FactionLevelManager.HomeTier> tiers = FactionLevelManager.getHomeTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseHomeUpgrade(factionName, crystalEntityUUID,
                            tiers.get(tierIndex), tierIndex);
                }
                case VAULT -> {
                    List<FactionLevelManager.VaultTier> tiers = FactionLevelManager.getVaultTiers();
                    if (tierIndex >= tiers.size()) yield false;
                    yield HatlasCrystalManager.purchaseVaultUpgrade(factionName, crystalEntityUUID,
                            tiers.get(tierIndex), tierIndex);
                }
            };

            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(Component.text("Upgrade purchased!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "Purchase failed — not enough skill points or crystal not found.", NamedTextColor.RED));
            }
            player.closeInventory();

        } else if (GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            GuiNavigator.back(player);
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildConfirmInfoItem(SkillPurchaseType type, int tierIndex) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta  = item.getItemMeta();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        switch (type) {
            case HP -> {
                List<FactionLevelManager.HpTier> tiers = FactionLevelManager.getHpTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.HpTier tier = tiers.get(tierIndex);
                    meta.displayName(Component.text("Purchase HP Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Bonus HP", "+" + (int) tier.bonus() + " ♥", NamedTextColor.RED));
                    if (tier.regen() > 0) {
                        lore.add(GuiUtil.loreLine("Regen", "+" + formatRegen(tier.regen()) + " ♥/s",
                                NamedTextColor.GREEN));
                    }
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case CLAIMS -> {
                List<FactionLevelManager.ClaimTier> tiers = FactionLevelManager.getClaimTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.ClaimTier tier = tiers.get(tierIndex);
                    meta.displayName(Component.text("Purchase Claim Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Bonus Claims", "+" + tier.amount() + " chunks", NamedTextColor.GREEN));
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case CHEST -> {
                List<FactionLevelManager.ChestTier> tiers = FactionLevelManager.getChestTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.ChestTier tier = tiers.get(tierIndex);
                    meta.displayName(Component.text("Purchase Chest Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Chest Size", tier.size() + " slots", NamedTextColor.YELLOW));
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case PROTECTION -> {
                List<FactionLevelManager.ProtectionTier> tiers = FactionLevelManager.getProtectionTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.ProtectionTier tier = tiers.get(tierIndex);
                    long secs = tier.durationMs() / 1000L;
                    String timeStr = secs >= 3600 ? (secs / 3600) + "h" : (secs / 60) + "m";
                    meta.displayName(Component.text("Purchase Protection Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Duration", timeStr + " immunity", NamedTextColor.AQUA));
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case OUTPOST -> {
                FactionLevelManager.OutpostTier tier = FactionLevelManager.getOutpostTier();
                meta.displayName(Component.text("Purchase Outpost Skill?", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(GuiUtil.loreLine("Unlocks", "Outpost crystal placement", NamedTextColor.AQUA));
                if (tier != null)
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
            }
            case HOMES -> {
                List<FactionLevelManager.HomeTier> tiers = FactionLevelManager.getHomeTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.HomeTier tier = tiers.get(tierIndex);
                    meta.displayName(Component.text("Purchase Homes Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Bonus Homes",
                            "+" + tier.amount() + " per member", NamedTextColor.AQUA));
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case VAULT -> {
                List<FactionLevelManager.VaultTier> tiers = FactionLevelManager.getVaultTiers();
                if (tierIndex < tiers.size()) {
                    FactionLevelManager.VaultTier tier = tiers.get(tierIndex);
                    meta.displayName(Component.text("Purchase Vault Upgrade?", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(GuiUtil.loreLine("Vault Size", tier.size() + " slots", NamedTextColor.YELLOW));
                    lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
                }
            }
            default -> meta.displayName(Component.text("Purchase Upgrade?", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("  This action is permanent.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Drops the trailing ".0" on whole numbers (e.g. 3.0 → "3", 1.5 → "1.5"). */
    private static String formatRegen(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
