package org.minecraft.hatlas.donjon;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @param bossDrops Per-rarity boss drop tables. A rarity with no entry uses an empty loot table.
 */
public record DonjonTypeConfig(String displayName,
                               List<String> mobTypes, List<String> bossTypes, int minWaves, int maxWaves,
                               int minMobsPerWave, int maxMobsPerWave, double baseHpMultiplier, double maxHpMultiplier,
                               double baseAttackMultiplier, double maxAttackMultiplier, double bossHpMultiplier,
                               double bossAttackMultiplier, double bossSpeedMultiplier, int bossCountMin,
                               int bossCountMax, long minExpReward, long maxExpReward,
                               Map<DonjonRarity, BossDropConfig> bossDrops) {

    public static DonjonTypeConfig load(ConfigurationSection s) {
        String displayName = s.getString("display_name", "Unknown");
        List<String> mobTypes = s.getStringList("mob_types");
        List<String> bossTypes = s.getStringList("boss_types");
        int minWaves = s.getInt("min_waves", 3);
        int maxWaves = s.getInt("max_waves", 10);
        int minMobs = s.getInt("min_mobs_per_wave", 5);
        int maxMobs = s.getInt("max_mobs_per_wave", 50);
        double baseHp = s.getDouble("base_hp_multiplier", 1.0);
        double maxHp = s.getDouble("max_hp_multiplier", 5.0);
        double baseAtk = s.getDouble("base_attack_multiplier", 1.0);
        double maxAtk = s.getDouble("max_attack_multiplier", 3.0);
        double bossHp = s.getDouble("boss_hp_multiplier", 10.0);
        double bossAtk = s.getDouble("boss_attack_multiplier", 2.0);
        double bossSpd = s.getDouble("boss_speed_multiplier", 0.5);
        int bossMin = s.getInt("boss_count_min", 1);
        int bossMax = s.getInt("boss_count_max", 3);
        long minExp = s.getLong("min_exp_reward", 500L);
        long maxExp = s.getLong("max_exp_reward", 10000L);

        Map<DonjonRarity, BossDropConfig> bossDrops = new EnumMap<>(DonjonRarity.class);
        ConfigurationSection dropsSec = s.getConfigurationSection("boss_drops");
        if (dropsSec != null) {
            for (DonjonRarity rarity : DonjonRarity.values()) {
                ConfigurationSection raritySec = dropsSec.getConfigurationSection(rarity.name().toLowerCase());
                if (raritySec != null) {
                    bossDrops.put(rarity, BossDropConfig.load(raritySec));
                }
            }
        }

        return new DonjonTypeConfig(displayName,
                mobTypes, bossTypes, minWaves, maxWaves, minMobs, maxMobs,
                baseHp, maxHp, baseAtk, maxAtk, bossHp, bossAtk, bossSpd,
                bossMin, bossMax, minExp, maxExp, bossDrops);
    }
}
