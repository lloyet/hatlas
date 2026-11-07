package org.minecraft.hatlas.Item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.Tool;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.minecraft.hatlas.Hatlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom amethyst gear: tools, weapons, armor, horse barding. Migrated 1:1 from
 * the legacy ruby gear set — ruby_sword → amethyst_sword, etc. Crafted from
 * vanilla amethyst shards. Item models live under the hatlas resource pack
 * (hatlas:item/amethyst_*); armor pieces reference equipment asset hatlas:amethyst.
 *
 * <p>Stats: every piece starts from its vanilla base (netherite, or trident for the spear)
 * and gets {@code config.yml > amethyst.stat_bonus_percent} (default 15 %) more attack damage,
 * armor, armor toughness, knockback resistance, mining speed (correct-tool rules only) and
 * durability. Attack speed and the default mining speed are kept. Items already crafted keep the stats they were made with.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AmethystItem {

    public static final String NAMESPACE = "hatlas";
    public static final String EQUIPMENT_ASSET = "amethyst";

    private static final Map<String, ItemStack> REGISTRY = new LinkedHashMap<>();

    /** Default bonus over the vanilla base, in percent. */
    public static final double DEFAULT_STAT_BONUS_PERCENT = 15.0;
    /** Stat multiplier (1 + bonus/100), read from config.yml in {@link #init()}. */
    private static double statMultiplier = 1.0 + DEFAULT_STAT_BONUS_PERCENT / 100.0;

    /** Player base attack damage (fist), added by vanilla to the item's modifier. */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

    private AmethystItem() {
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    public static void init() {
        double bonus = Hatlas.instance.getConfig()
                .getDouble("amethyst.stat_bonus_percent", DEFAULT_STAT_BONUS_PERCENT);
        statMultiplier = Math.max(0.0, 1.0 + bonus / 100.0);
        REGISTRY.clear();
        register("amethyst_sword", buildSword());
        register("amethyst_pickaxe", buildTool(Material.NETHERITE_PICKAXE, "amethyst_pickaxe", "Amethyst Pickaxe"));
        register("amethyst_axe", buildTool(Material.NETHERITE_AXE, "amethyst_axe", "Amethyst Axe"));
        register("amethyst_shovel", buildTool(Material.NETHERITE_SHOVEL, "amethyst_shovel", "Amethyst Shovel"));
        register("amethyst_hoe", buildTool(Material.NETHERITE_HOE, "amethyst_hoe", "Amethyst Hoe"));
        register("amethyst_spear", buildSpear());
        register("amethyst_helmet", buildArmor(Material.NETHERITE_HELMET, EquipmentSlot.HEAD, "amethyst_helmet", "Amethyst Helmet"));
        register("amethyst_chestplate", buildArmor(Material.NETHERITE_CHESTPLATE, EquipmentSlot.CHEST, "amethyst_chestplate", "Amethyst Chestplate"));
        register("amethyst_leggings", buildArmor(Material.NETHERITE_LEGGINGS, EquipmentSlot.LEGS, "amethyst_leggings", "Amethyst Leggings"));
        register("amethyst_boots", buildArmor(Material.NETHERITE_BOOTS, EquipmentSlot.FEET, "amethyst_boots", "Amethyst Boots"));
        register("amethyst_rod", buildRod());
        register("amethyst_horse_armor", buildHorseArmor());
    }

    public static void registerRecipes() {
        RecipeChoice shard = new RecipeChoice.MaterialChoice(Material.AMETHYST_SHARD);
        RecipeChoice stick = new RecipeChoice.MaterialChoice(Material.STICK);

        // Tools / weapons
        shaped("amethyst_sword", get("amethyst_sword"), new String[]{"A", "A", "S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_pickaxe", get("amethyst_pickaxe"), new String[]{"AAA", " S ", " S "},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_axe", get("amethyst_axe"), new String[]{"AA", "AS", " S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_shovel", get("amethyst_shovel"), new String[]{"A", "S", "S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_hoe", get("amethyst_hoe"), new String[]{"AA", " S", " S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_spear", get("amethyst_spear"), new String[]{"A", "A", "A"},
                Map.of('A', shard));

        // Armor
        shaped("amethyst_helmet", get("amethyst_helmet"), new String[]{"AAA", "A A"}, Map.of('A', shard));
        shaped("amethyst_chestplate", get("amethyst_chestplate"), new String[]{"A A", "AAA", "AAA"}, Map.of('A', shard));
        shaped("amethyst_leggings", get("amethyst_leggings"), new String[]{"AAA", "A A", "A A"}, Map.of('A', shard));
        shaped("amethyst_boots", get("amethyst_boots"), new String[]{"A A", "A A"}, Map.of('A', shard));
    }

    public static ItemStack get(String id) {
        ItemStack template = REGISTRY.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown amethyst item: " + id);
        return template.clone();
    }

    public static List<String> ids() {
        return List.copyOf(REGISTRY.keySet());
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static ItemStack buildSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        applyCommon(item, "amethyst_sword", "Amethyst Sword", "Cuts cleanly through the toughest foes.");
        return item;
    }

    private static ItemStack buildTool(Material base, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, null);
        return item;
    }

    private static ItemStack buildSpear() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        applyCommon(item, "amethyst_spear", "Amethyst Spear", "A balanced thrusting weapon.");
        return item;
    }

    private static ItemStack buildArmor(Material base, EquipmentSlot slot, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, null);
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(slot)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET))
                        .build());
        return item;
    }

    private static ItemStack buildRod() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        applyCommon(item, "amethyst_rod", "Amethyst Rod", "A focused conduit of amethyst energy.");
        return item;
    }

    private static ItemStack buildHorseArmor() {
        ItemStack item = new ItemStack(Material.NETHERITE_HORSE_ARMOR);
        applyCommon(item, "amethyst_horse_armor", "Amethyst Horse Armor", "Plated barding for your steed.");
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(EquipmentSlot.BODY)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET))
                        .build());
        return item;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void applyCommon(ItemStack item, String modelId, String displayName, String loreLine) {
        boostStats(item);
        item.setData(DataComponentTypes.ITEM_NAME,
                Component.text(displayName, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(NAMESPACE, modelId));
        if (loreLine != null) {
            item.setData(DataComponentTypes.LORE,
                    ItemLore.lore()
                            .addLine(Component.text(loreLine, NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false))
                            .build());
        }
    }

    /**
     * Scales the base item's default stats by {@link #statMultiplier}. Modifier keys are kept
     * so the tooltip still shows the vanilla "8 Attack Damage" style lines.
     */
    private static void boostStats(ItemStack item) {
        if (statMultiplier == 1.0) return;
        ItemAttributeModifiers attrs = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attrs != null && !attrs.modifiers().isEmpty()) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
            for (ItemAttributeModifiers.Entry e : attrs.modifiers()) {
                AttributeModifier m = e.modifier();
                double amount = scaledAmount(e.attribute(), m);
                builder.addModifier(e.attribute(),
                        new AttributeModifier(m.getKey(), amount, m.getOperation(), e.getGroup()),
                        e.getGroup(), e.display());
            }
            item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        }

        Tool tool = item.getData(DataComponentTypes.TOOL);
        if (tool != null) {
            Tool.Builder builder = Tool.tool()
                    // Default speed (blocks the tool is not made for) stays vanilla.
                    .defaultMiningSpeed(tool.defaultMiningSpeed())
                    .damagePerBlock(tool.damagePerBlock())
                    .canDestroyBlocksInCreative(tool.canDestroyBlocksInCreative());
            for (Tool.Rule rule : tool.rules()) {
                Float speed = scaledSpeed(rule.speed());
                builder.addRule(Tool.rule(rule.blocks(), speed, rule.correctForDrops()));
            }
            item.setData(DataComponentTypes.TOOL, builder.build());
        }

        Integer maxDamage = item.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage != null) {
            item.setData(DataComponentTypes.MAX_DAMAGE, (int) Math.round(maxDamage * statMultiplier));
        }
    }

    /**
     * Scales a tool rule speed. Huge speeds (the sword's instant-mine rule uses
     * Float.MAX_VALUE) are kept as is: scaling them overflows to Infinity, which Minecraft
     * refuses to save, so the item would vanish on logout.
     */
    private static Float scaledSpeed(Float speed) {
        if (speed == null) return null;
        double scaled = speed * statMultiplier;
        return (Double.isFinite(scaled) && scaled <= Float.MAX_VALUE) ? (float) scaled : speed;
    }

        private static double scaledAmount(Attribute attribute, AttributeModifier m) {
        if (m.getOperation() != AttributeModifier.Operation.ADD_NUMBER) return m.getAmount();
        if (attribute == Attribute.ATTACK_DAMAGE) {
            // The modifier is added to the fist's 1 damage: scale the total, not the modifier.
            return (PLAYER_BASE_ATTACK_DAMAGE + m.getAmount()) * statMultiplier - PLAYER_BASE_ATTACK_DAMAGE;
        }
        if (attribute == Attribute.ARMOR || attribute == Attribute.ARMOR_TOUGHNESS
                || attribute == Attribute.KNOCKBACK_RESISTANCE) {
            return m.getAmount() * statMultiplier;
        }
        return m.getAmount(); // attack speed and anything else: unchanged
    }

    private static void register(String id, ItemStack item) {
        REGISTRY.put(id, item);
    }

    private static void shaped(String key, ItemStack result, String[] shape, Map<Character, RecipeChoice> map) {
        NamespacedKey nk = new NamespacedKey(Hatlas.instance, key);
        if (Bukkit.getRecipe(nk) != null) Bukkit.removeRecipe(nk);
        ShapedRecipe recipe = new ShapedRecipe(nk, result);
        recipe.shape(shape);
        map.forEach(recipe::setIngredient);
        Bukkit.addRecipe(recipe);
    }
}
