package org.minecraft.hatlas.donjon;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines the loot table for a donjon boss, scoped to a specific rarity tier.
 *
 * <p>When the boss dies, {@link #rollDrops()} is called to produce a list of
 * {@link ItemStack}s. The number of items is drawn uniformly from
 * [{@code minItems}, {@code maxItems}].  Each item slot is filled by randomly
 * selecting an entry from the configured list using the {@code chance} field as
 * a relative weight (higher = more likely to be chosen).
 */
public class BossDropConfig {

    public record EnchantmentEntry(Enchantment enchantment, int level) {}
    public record Entry(Material material, int amount, double chance, List<EnchantmentEntry> enchantments, boolean poweredCreeperEgg, int raiderLevel) {}

    private final int minItems;
    private final int maxItems;
    private final List<Entry> entries;

    public BossDropConfig(int minItems, int maxItems, List<Entry> entries) {
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.entries  = List.copyOf(entries);
    }

    /** Rolls the loot table and returns the resulting item stacks. */
    public List<ItemStack> rollDrops() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int n = minItems + (maxItems > minItems ? rand.nextInt(maxItems - minItems + 1) : 0);

        List<ItemStack> drops = new ArrayList<>();
        if (entries.isEmpty() || n <= 0) return drops;

        double totalWeight = entries.stream().mapToDouble(Entry::chance).sum();
        if (totalWeight <= 0) return drops;

        for (int i = 0; i < n; i++) {
            double roll = rand.nextDouble() * totalWeight;
            double cumulative = 0;
            for (Entry entry : entries) {
                cumulative += entry.chance();
                if (roll < cumulative) {
                    if (entry.poweredCreeperEgg()) {
                        int count = Math.max(1, entry.amount());
                        for (int j = 0; j < count; j++) {
                            drops.add(ElectricalCreeperManager.createElectricalCreeperEgg());
                        }
                    } else if (entry.raiderLevel() > 0) {
                        int count = Math.max(1, entry.amount());
                        for (int j = 0; j < count; j++) {
                            ItemStack raiderItem = new ItemStack(entry.material());
                            RaiderPickaxe.applyToItem(raiderItem, entry.raiderLevel());
                            drops.add(raiderItem);
                        }
                    } else {
                        ItemStack stack = new ItemStack(entry.material(), entry.amount());
                        for (EnchantmentEntry enc : entry.enchantments()) {
                            stack.addUnsafeEnchantment(enc.enchantment(), enc.level());
                        }
                        drops.add(stack);
                    }
                    break;
                }
            }
        }
        return drops;
    }

    /** Parses a {@code BossDropConfig} from a YAML {@link ConfigurationSection}. */
    public static BossDropConfig load(ConfigurationSection sec) {
        int min = sec.getInt("min_items", 1);
        int max = sec.getInt("max_items", 3);
        List<Entry> entries = new ArrayList<>();

        for (Map<?, ?> row : sec.getMapList("items")) {
            Object matRaw = row.get("material");
            String matName = (matRaw != null ? matRaw.toString() : "AIR").toUpperCase();
            int    amount  = row.get("amount")  instanceof Number nm ? nm.intValue()    : 1;
            double chance  = row.get("chance")  instanceof Number nc ? nc.doubleValue() : 0.5;

            // Parse optional enchantments list; detect powered creeper egg and raider pickaxe pseudo-enchantments
            boolean isPoweredEgg = false;
            int raiderLevel = 0;
            List<EnchantmentEntry> enchantments = new ArrayList<>();
            if (row.get("enchantments") instanceof List<?> encList) {
                for (Object encObj : encList) {
                    if (!(encObj instanceof Map<?, ?> encMap)) continue;
                    Object encName = encMap.get("enchantment");
                    int encLevel = encMap.get("level") instanceof Number nl ? nl.intValue() : 1;
                    if (encName == null) continue;
                    String encStr = encName.toString().toLowerCase();
                    if ("powered".equals(encStr) && "CREEPER_SPAWN_EGG".equals(matName) && encLevel >= 1) {
                        isPoweredEgg = true;
                        continue; // not a real Minecraft enchantment — handled as special flag
                    }
                    if ("raider".equals(encStr) && encLevel >= 1) {
                        raiderLevel = encLevel;
                        continue; // custom flag — handled as Raider enchantment on any item
                    }
                    Enchantment enc = RegistryAccess.registryAccess()
                            .getRegistry(RegistryKey.ENCHANTMENT)
                            .get(NamespacedKey.minecraft(encStr));
                    if (enc != null) enchantments.add(new EnchantmentEntry(enc, encLevel));
                }
            }

            try {
                entries.add(new Entry(Material.valueOf(matName), amount, chance, List.copyOf(enchantments), isPoweredEgg, raiderLevel));
            } catch (IllegalArgumentException ignored) {
                // Skip entries with invalid material names
            }
        }
        return new BossDropConfig(min, max, entries);
    }
}
