package org.minecraft.atlas.donjon;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
import org.minecraft.atlas.storage.model.BlockPos;
import org.minecraft.atlas.storage.model.DonjonsData;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.util.TitleUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DonjonManager {

    // -------------------------------------------------------------------------
    // In-memory state
    // -------------------------------------------------------------------------

    /** All registered donjons, keyed by their unique ID. */
    private static final Map<String, Donjon> donjons = new LinkedHashMap<>();

    /** Maps a spawned mob UUID to the donjon ID it belongs to. */
    private static final Map<UUID, String> entityToDonjonId = new HashMap<>();

    /**
     * Tracks which donjon IDs each player has visited at least one chunk of.
     */
    private static final Map<UUID, Set<String>> playerVisitedDonjons = new HashMap<>();

    /** Type-specific configuration, keyed by the config section name (e.g. "trial", "desert"). */
    private static final Map<String, DonjonTypeConfig> typeConfigMap = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Global settings (loaded from config)
    // -------------------------------------------------------------------------

    private static long activationIntervalMs  = 6L  * 3_600_000L;
    private static long idleTimeoutMs         = 24L * 3_600_000L;
    private static long lastActivationTime    = 0L;
    private static int  maxActiveDonjonons    = 2;

    /** {minLevel, maxLevel, weight} per difficulty range */
    private static int[][] difficultyRanges = {
            {0,  9,  66}, {10, 49, 14}, {50, 74, 10},
            {75, 89,  5}, {90, 94,  3}, {95, 99,  2}
    };

    /** Rarity weights indexed by DonjonRarity.ordinal() */
    private static int[] rarityWeights = {66, 14, 10, 5, 3, 2};

    /** {minLevel, maxLevel, weight} difficulty tiers for ominous key generation. */
    private static int[][] ominousDifficultyTiers = {{50, 60, 60}, {61, 75, 25}, {76, 80, 10}, {81, 99, 5}};

    /** Rarity weights for ominous keys — indexed EPIC=0, LEGENDARY=1, MYSTIC=2, GODDESS=3. */
    private static int[] ominousRarityWeights = {60, 25, 10, 5};

    private static final DonjonRarity[] OMINOUS_RARITIES =
            {DonjonRarity.EPIC, DonjonRarity.LEGENDARY, DonjonRarity.MYSTIC, DonjonRarity.GODDESS};

    // -------------------------------------------------------------------------
    // PDC keys (initialised in loadConfig, after Atlas.instance is set)
    // -------------------------------------------------------------------------

    public static NamespacedKey keyDonjonId;
    public static NamespacedKey keyDonjonWave;
    public static NamespacedKey keyIsBoss;
    public static NamespacedKey keyTotemDisplay;
    public static NamespacedKey keyOminousLevel;
    public static NamespacedKey keyOminousRarity;
    /** Marker placed on every admin-created donjon key to distinguish it from vanilla items. */
    public static NamespacedKey keyDonjonMarker;
    /** Stores the original config mob-type string on each wave-tracked entity (for respawn on non-player death). */
    public static NamespacedKey keyMobType;

    // -------------------------------------------------------------------------
    // Config loading
    // -------------------------------------------------------------------------

    public static void loadConfig(FileConfiguration config) {
        keyDonjonId    = new NamespacedKey(Atlas.instance, "donjon_id");
        keyDonjonWave  = new NamespacedKey(Atlas.instance, "donjon_wave");
        keyIsBoss      = new NamespacedKey(Atlas.instance, "donjon_is_boss");
        keyTotemDisplay  = new NamespacedKey(Atlas.instance, "donjon_totem_display");
        keyOminousLevel  = new NamespacedKey(Atlas.instance, "donjon_ominous_level");
        keyOminousRarity = new NamespacedKey(Atlas.instance, "donjon_ominous_rarity");
        keyDonjonMarker  = new NamespacedKey(Atlas.instance, "donjon_key_marker");
        keyMobType       = new NamespacedKey(Atlas.instance, "donjon_mob_type");

        activationIntervalMs = config.getLong("activation_interval_hours", 6) * 3_600_000L;
        idleTimeoutMs        = config.getLong("idle_timeout_hours", 24)        * 3_600_000L;
        maxActiveDonjonons   = config.getInt("max_active_donjons", 2);

        // Difficulty ranges
        List<?> diffList = config.getList("difficulty");
        if (diffList != null && !diffList.isEmpty()) {
            List<int[]> ranges = new ArrayList<>();
            for (Object obj : diffList) {
                if (obj instanceof Map<?, ?> m) {
                    ranges.add(new int[]{
                            toInt(m.get("min")),
                            toInt(m.get("max")),
                            toInt(m.get("weight"))
                    });
                }
            }
            if (!ranges.isEmpty()) difficultyRanges = ranges.toArray(new int[0][]);
        }

        // Rarity weights
        ConfigurationSection raritySec = config.getConfigurationSection("rarity");
        if (raritySec != null) {
            rarityWeights = new int[]{
                    raritySec.getInt("common",    66),
                    raritySec.getInt("rare",      14),
                    raritySec.getInt("epic",      10),
                    raritySec.getInt("legendary",  5),
                    raritySec.getInt("mystic",     3),
                    raritySec.getInt("goddess",    2)
            };
        }

        // Type configs — read all sections present in the YAML, no enum required
        typeConfigMap.clear();
        ConfigurationSection typesSec = config.getConfigurationSection("types");
        if (typesSec != null) {
            for (String typeKey : typesSec.getKeys(false)) {
                ConfigurationSection typeSec = typesSec.getConfigurationSection(typeKey);
                if (typeSec != null) {
                    typeConfigMap.put(typeKey, DonjonTypeConfig.load(typeSec));
                }
            }
        }

        // Ominous key generation weights
        ConfigurationSection ominousSec = config.getConfigurationSection("ominous_key");
        if (ominousSec != null) {
            List<?> tierList = ominousSec.getList("difficulty_tiers");
            if (tierList != null && !tierList.isEmpty()) {
                List<int[]> tiers = new ArrayList<>();
                for (Object obj : tierList) {
                    if (obj instanceof Map<?, ?> m) {
                        tiers.add(new int[]{toInt(m.get("min")), toInt(m.get("max")), toInt(m.get("weight"))});
                    }
                }
                if (!tiers.isEmpty()) ominousDifficultyTiers = tiers.toArray(new int[0][]);
            }
            ConfigurationSection rwSec = ominousSec.getConfigurationSection("rarity_weights");
            if (rwSec != null) {
                ominousRarityWeights = new int[]{
                        rwSec.getInt("epic",      60),
                        rwSec.getInt("legendary", 25),
                        rwSec.getInt("mystic",    10),
                        rwSec.getInt("goddess",    5)
                };
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Stored donjons that could not be rebuilt (world not loaded, type removed from
     * donjons.yml). Kept verbatim and written back so they are not lost from storage.
     */
    private static final List<DonjonsData.DonjonRow> unresolvedDonjons = new ArrayList<>();

    /** Donjons, visits and the activation timer, for storage. Main thread only. */
    public static DonjonsData snapshot() {
        List<DonjonsData.DonjonRow> rows = new ArrayList<>();
        for (Donjon d : donjons.values()) {
            String world = d.getWorld().getName();
            Map<Integer, BlockPos> spawns = new TreeMap<>();
            d.getSpawnPointsById().forEach((id, p) ->
                    spawns.put(id, new BlockPos(world, p.getBlockX(), p.getBlockY(), p.getBlockZ())));
            Location nt = d.getNametagLocation();
            Location vl = d.getVaultLocation();
            rows.add(new DonjonsData.DonjonRow(d.getId(), d.getType(), d.getName(), d.getLevel(),
                    d.getRarity().name(), d.getStatus().name(), d.getActivationTime(), d.isTimeoutWarned(), world,
                    nt == null ? null : new BlockPos(world, nt.getBlockX(), nt.getBlockY(), nt.getBlockZ()),
                    vl == null ? null : new BlockPos(world, vl.getBlockX(), vl.getBlockY(), vl.getBlockZ()),
                    LocationData.of(d.getTeleportSpawn()), d.getTextDisplayUUID(),
                    Map.copyOf(spawns), List.copyOf(d.getProtectedChunkKeys())));
        }
        for (DonjonsData.DonjonRow raw : unresolvedDonjons) {
            if (!donjons.containsKey(raw.id())) rows.add(raw);
        }
        Map<UUID, Set<String>> visits = new HashMap<>();
        playerVisitedDonjons.forEach((u, ids) -> {
            if (!ids.isEmpty()) visits.put(u, Set.copyOf(ids));
        });
        return new DonjonsData(List.copyOf(rows), Map.copyOf(visits), lastActivationTime);
    }

    /** Rebuilds donjons from storage. Call after {@link #loadConfig} (needs the type configs). */
    public static void restore(DonjonsData data) {
        donjons.clear();
        playerVisitedDonjons.clear();
        unresolvedDonjons.clear();
        lastActivationTime = data.lastActivation();
        data.visits().forEach((u, ids) -> {
            if (!ids.isEmpty()) playerVisitedDonjons.put(u, new HashSet<>(ids));
        });

        for (DonjonsData.DonjonRow row : data.donjons()) {
            World world = Bukkit.getWorld(row.world());
            if (world == null || row.type() == null || !typeConfigMap.containsKey(row.type())) {
                unresolvedDonjons.add(row);
                Atlas.instance.getLogger().warning("Donjon '" + row.id() + "' kept in storage but not loaded "
                        + "(world '" + row.world() + "' or type '" + row.type() + "' unavailable).");
                continue;
            }
            DonjonRarity rarity = parseEnum(DonjonRarity.class, row.rarity(), DonjonRarity.COMMON);
            Donjon donjon = new Donjon(row.id(), row.name(), row.type(), world, row.level(), rarity);
            donjon.setStatus(parseEnum(DonjonStatus.class, row.status(), DonjonStatus.IDLE));
            donjon.setActivationTime(row.activationTime());
            donjon.setTimeoutWarned(row.timeoutWarned());
            if (row.nametag() != null) {
                donjon.setNametagLocation(new Location(world, row.nametag().x(), row.nametag().y(), row.nametag().z()));
            }
            if (row.vault() != null) {
                donjon.setVaultLocation(new Location(world, row.vault().x(), row.vault().y(), row.vault().z()));
            }
            if (row.teleportSpawn() != null) {
                LocationData t = row.teleportSpawn();
                donjon.setTeleportSpawn(new Location(world, t.x(), t.y(), t.z(), t.yaw(), t.pitch()));
            }
            row.spawnPoints().forEach((id, p) ->
                    donjon.putSpawnPoint(id, new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5)));
            donjon.setTextDisplayUUID(row.textDisplay());
            donjon.getProtectedChunkKeys().addAll(row.protectedChunks());
            donjons.put(row.id(), donjon);
        }
    }

    // -------------------------------------------------------------------------
    // Scheduler
    // -------------------------------------------------------------------------

    public static void schedule(Plugin plugin) {
        // Every 2 s: player presence check
        Bukkit.getScheduler().runTaskTimer(plugin, DonjonManager::tick, 40L, 40L);
        // Every 60 s: activation timer, timeout checks
        Bukkit.getScheduler().runTaskTimer(plugin, DonjonManager::minuteTick, 1200L, 1200L);
    }

    private static void tick() {
        for (Donjon d : donjons.values()) {
            if (d.getStatus() == DonjonStatus.ACTIVE && d.isInProgress()) {
                checkPlayerPresence(d);
            }
        }
    }

    private static void minuteTick() {
        long now = System.currentTimeMillis();

        // Activation timer
        if (!donjons.isEmpty() && (now - lastActivationTime) >= activationIntervalMs) {
            activateRandomDonjon();
            lastActivationTime = now;
            Storage.save(DataDomain.DONJONS);
        }

        // Timeout checks
        for (Donjon d : new ArrayList<>(donjons.values())) {
            if (d.getStatus() != DonjonStatus.ACTIVE || d.getActivationTime() == 0) continue;

            long elapsed = now - d.getActivationTime();
            long warnThreshold = idleTimeoutMs - 3_600_000L; // warn 1 h before timeout

            if (!d.isTimeoutWarned() && elapsed >= warnThreshold) {
                d.setTimeoutWarned(true);
                broadcastGlobal(
                        Component.text("⚠ Donjon ", NamedTextColor.YELLOW)
                                .append(Component.text(d.getName(), d.getRarity().getColor()))
                                .append(Component.text(" will become idle in 1 hour!", NamedTextColor.YELLOW)),
                        "⚠ " + d.getName() + " idle soon!",
                        NamedTextColor.YELLOW);
            }

            if (elapsed >= idleTimeoutMs) {
                setIdle(d, "timed out");
            }
        }
    }

    private static void checkPlayerPresence(Donjon donjon) {
        World world = donjon.getWorld();
        Set<UUID> inside = new HashSet<>();

        for (Player p : Atlas.instance.getServer().getOnlinePlayers()) {
            if (p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon)) {
                inside.add(p.getUniqueId());
            }
        }

        donjon.getPlayersInside().clear();
        donjon.getPlayersInside().addAll(inside);

        if (inside.isEmpty()) {
            resetDonjon(donjon);
        }
    }

    // -------------------------------------------------------------------------
    // Donjon creation / deletion
    // -------------------------------------------------------------------------

    public static void deleteDonjon(String id) {
        Donjon donjon = donjons.remove(id);
        if (donjon == null) return;

        // Remove text display
        if (donjon.getTextDisplayUUID() != null) {
            Entity e = Bukkit.getEntity(donjon.getTextDisplayUUID());
            if (e != null) e.remove();
        }

        // Kill all tracked mobs
        clearEntities(donjon);

        // Remove this donjon from every player's visited list
        playerVisitedDonjons.values().forEach(visited -> visited.remove(id));
        playerVisitedDonjons.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------

    /**
     * Activates a donjon via the scheduler — assigns a fresh random level and rarity first.
     */
    public static void activateDonjon(Donjon donjon) {
        donjon.setLevel(randomLevel());
        donjon.setRarity(randomRarity());
        doActivateDonjon(donjon);
    }

    /**
     * Force-activates a donjon via an enchanted Ominous Trial Key.
     * Assigns a high level (50–99) and LEGENDARY/MYSTIC/GODDESS rarity before activating.
     */
    public static void forceActivateDonjon(Donjon donjon) {
        donjon.setLevel(ThreadLocalRandom.current().nextInt(50, 100));
        DonjonRarity[] forcedRarities = {DonjonRarity.LEGENDARY, DonjonRarity.MYSTIC, DonjonRarity.GODDESS};
        donjon.setRarity(forcedRarities[ThreadLocalRandom.current().nextInt(forcedRarities.length)]);
        doActivateDonjon(donjon);
    }

    /** Activates a donjon with an explicit level and rarity (admin command or ominous key lore). */
    public static void activateDonjonWithParams(Donjon donjon, int level, DonjonRarity rarity) {
        donjon.setLevel(level);
        donjon.setRarity(rarity);
        doActivateDonjon(donjon);
    }

    /**
     * Creates an Ominous Trial Key using weighted random selection for difficulty tier
     * and rarity, as configured under {@code ominous_key} in donjons.yml.
     */
    public static ItemStack createOminousKey() {
        return buildOminousKey(rollOminousLevel(), rollOminousRarity());
    }

    /** Rolls a difficulty level from the configured ominous key tiers. */
    public static int rollOminousLevel() {
        int total = 0;
        for (int[] t : ominousDifficultyTiers) total += t[2];
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cum = 0;
        for (int[] t : ominousDifficultyTiers) {
            cum += t[2];
            if (roll < cum) return ThreadLocalRandom.current().nextInt(t[0], t[1] + 1);
        }
        return 50;
    }

    /** Rolls a rarity from the configured ominous key rarity weights (EPIC and above). */
    public static DonjonRarity rollOminousRarity() {
        int total = 0;
        for (int w : ominousRarityWeights) total += w;
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cum = 0;
        for (int i = 0; i < ominousRarityWeights.length; i++) {
            cum += ominousRarityWeights[i];
            if (roll < cum) return OMINOUS_RARITIES[i];
        }
        return DonjonRarity.EPIC;
    }

    /** Builds an Ominous Trial Key for the given level and rarity, writing both into lore and PDC. */
    public static ItemStack buildOminousKey(int level, DonjonRarity rarity) {
        ItemStack item = new ItemStack(Material.OMINOUS_TRIAL_KEY);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Donjon Ominous Key", NamedTextColor.LIGHT_PURPLE)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
            meta.lore(List.of(
                Component.empty(),
                Component.text("  Difficulty: ", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                        .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)),
                Component.text("  Rarity: ", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                        .append(Component.text(rarity.getDisplayName(), rarity.getColor())
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)),
                Component.empty(),
                Component.text("  Right-click the Vault in any donjon to", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.text("  force-activate it and begin your trial.", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyDonjonMarker,  PersistentDataType.BYTE,    (byte) 1);
            meta.getPersistentDataContainer().set(keyOminousLevel,  PersistentDataType.INTEGER, level);
            meta.getPersistentDataContainer().set(keyOminousRarity, PersistentDataType.STRING,  rarity.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Creates a Trial Key usable to start an already-ACTIVE donjon. */
    public static ItemStack createTrialKey() {
        ItemStack item = new ItemStack(Material.TRIAL_KEY);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ Donjon Trial Key", NamedTextColor.GOLD)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
            meta.lore(List.of(
                Component.empty(),
                Component.text("  Right-click the Vault in an ACTIVE donjon", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.text("  to begin your faction's trial.", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.empty()
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyDonjonMarker, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Internal activation: sets ACTIVE state, updates nametag, and broadcasts. */
    private static void doActivateDonjon(Donjon donjon) {
        donjon.setStatus(DonjonStatus.ACTIVE);
        donjon.setActivationTime(System.currentTimeMillis());
        donjon.setTimeoutWarned(false);
        spawnOrUpdateNametag(donjon);

        broadcastGlobal(
                Component.text("⚡ The donjon ", NamedTextColor.GOLD)
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text(" [Lv." + donjon.getLevel() + " — " + donjon.getRarity().getDisplayName() + "]", NamedTextColor.YELLOW))
                        .append(Component.text(" is now ACTIVE!", NamedTextColor.GOLD)),
                "⚡ " + donjon.getName() + " ACTIVE!",
                NamedTextColor.GOLD);
    }

    // -------------------------------------------------------------------------
    // Donjon start (player triggers)
    // -------------------------------------------------------------------------

    public static boolean startDonjon(Donjon donjon, Player starter, String factionName) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return false;

        donjon.setWaves(generateWaves(donjon, cfg));
        donjon.setCurrentWaveIndex(0);
        donjon.setInProgress(true);
        donjon.setStartingFaction(factionName);
        spawnOrUpdateNametag(donjon);
        donjon.getBossDamageMap().clear();

        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor factionColor = faction != null ? faction.getColor() : NamedTextColor.WHITE;

        broadcastGlobal(
                Component.text("⚔ Faction ", NamedTextColor.GOLD)
                        .append(Component.text("[" + factionName + "]", factionColor))
                        .append(Component.text(" has started the donjon ", NamedTextColor.GOLD))
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text("!", NamedTextColor.GOLD)),
                "⚔ " + donjon.getName() + " started!",
                NamedTextColor.GOLD);

        soundToDonjonPlayers(donjon, Sound.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, 1.0f, 1.0f);
        startWave(donjon, 0);
        return true;
    }

    // -------------------------------------------------------------------------
    // Wave management
    // -------------------------------------------------------------------------

    private static List<DonjonWave> generateWaves(Donjon donjon, DonjonTypeConfig cfg) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double levelFactor = donjon.getLevel() / 99.0;

        int numWaves = cfg.minWaves() + (int) Math.round(levelFactor * (cfg.maxWaves() - cfg.minWaves()));
        int baseMobCount = cfg.minMobsPerWave()
                + (int) Math.round(levelFactor * (cfg.maxMobsPerWave() - cfg.minMobsPerWave()));

        List<DonjonWave> waves = new ArrayList<>();
        List<String> mobTypes = cfg.mobTypes();
        List<String> bossTypes = cfg.bossTypes();

        for (int i = 1; i <= numWaves; i++) {
            boolean isBoss = (i == numWaves);
            List<String> mobsForWave = new ArrayList<>();

            if (isBoss) {
                int bossCount = cfg.bossCountMin()
                        + (cfg.bossCountMax() > cfg.bossCountMin()
                        ? rand.nextInt(cfg.bossCountMax() - cfg.bossCountMin() + 1) : 0);
                if (!bossTypes.isEmpty()) {
                    for (int j = 0; j < bossCount; j++) {
                        mobsForWave.add(bossTypes.get(rand.nextInt(bossTypes.size())));
                    }
                }
            } else {
                double variation = 0.8 + rand.nextDouble() * 0.4;
                int count = Math.max(cfg.minMobsPerWave(), (int)(baseMobCount * variation));
                if (!mobTypes.isEmpty()) {
                    for (int j = 0; j < count; j++) {
                        mobsForWave.add(mobTypes.get(rand.nextInt(mobTypes.size())));
                    }
                }
            }

            waves.add(new DonjonWave(i, isBoss, mobsForWave));
        }

        return waves;
    }

    static void startWave(Donjon donjon, int waveIndex) {
        if (waveIndex >= donjon.getWaves().size()) {
            completeDonjon(donjon);

            return;
        }

        DonjonWave wave = donjon.getWaves().get(waveIndex);
        donjon.setCurrentWaveIndex(waveIndex);

        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return;

        double lf = donjon.getLevel() / 99.0;
        double hpMult = cfg.baseHpMultiplier() + lf * (cfg.maxHpMultiplier() - cfg.baseHpMultiplier());
        double atkMult = cfg.baseAttackMultiplier() + lf * (cfg.maxAttackMultiplier() - cfg.baseAttackMultiplier());

        if (wave.isBossWave()) {
            hpMult  *= cfg.bossHpMultiplier();
            atkMult *= cfg.bossAttackMultiplier();
        }

        double finalHpMult = hpMult;
        double finalAtkMult = atkMult;
        double bossSpeedMult = cfg.bossSpeedMultiplier();
        String donjonId = donjon.getId();

        List<Location> spawnPoints = donjon.getSpawnPoints();
        List<String> mobsToSpawn = wave.getMobTypesToSpawn();
        int spawnCount = spawnPoints.size();

        for (int i = 0; i < mobsToSpawn.size(); i++) {
            Location spawnLoc = spawnCount > 0
                    ? spawnPoints.get(i % spawnCount).clone()
                    : randomSpawnLocation(donjon);
            if (spawnLoc == null) continue;
            List<UUID> uuids = spawnMobEntity(mobsToSpawn.get(i), spawnLoc, finalHpMult, finalAtkMult,
                    wave.isBossWave(), wave.isBossWave() ? bossSpeedMult : 1.0,
                    donjonId, waveIndex);
            for (UUID uuid : uuids) {
                wave.addSpawnedEntity(uuid);
                entityToDonjonId.put(uuid, donjonId);
            }
        }
        wave.setStarted(true);

        if (wave.isBossWave()) {
            alertDonjonPlayers(donjon, "☠ BOSS WAVE — Wave " + wave.getWaveNumber() + "!", NamedTextColor.DARK_RED);
            soundToDonjonPlayers(donjon, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        } else {
            alertDonjonPlayers(donjon, "⚡ Wave " + wave.getWaveNumber() + " begins!", NamedTextColor.YELLOW);
            randomSoundToDonjonPlayers(donjon, WAVE_SPAWN_SOUNDS, 0.8f, 1.0f);
        }
    }

    // -------------------------------------------------------------------------
    // Entity death / damage tracking
    // -------------------------------------------------------------------------

    public static void onEntityDeath(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        String donjonId = entityToDonjonId.remove(uuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null) return;

        wave.removeSpawnedEntity(uuid);

        // Show kill counter subtitle to players inside the donjon
        int total = wave.getMobTypesToSpawn().size();
        int remaining = wave.getSpawnedEntities().size();
        int killed = total - remaining;
        if (wave.isBossWave()) {
            alertDonjonPlayersSubtitle(donjon, "☠ " + killed + " / " + total, NamedTextColor.DARK_RED);
        } else {
            alertDonjonPlayersSubtitle(donjon, "⚔ " + killed + " / " + total, NamedTextColor.YELLOW);
        }

        // When 5 or fewer mobs remain in the wave, make them glow so players can locate them
        if (remaining > 0 && remaining <= 5) {
            for (UUID remainingUuid : wave.getSpawnedEntities()) {
                Entity e = Bukkit.getEntity(remainingUuid);
                if (e instanceof LivingEntity le && !le.isDead()) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
                }
            }
        }

        // Drop boss loot when a boss entity dies
        if (isBossEntity(entity)) {
            spawnBossDrops(entity.getLocation(), donjon);
        }

        // If baby zombie dies, also kill its vehicle (chicken in a jockey)
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            UUID vehicleUUID = vehicle.getUniqueId();
            donjon.getAuxiliaryEntities().remove(vehicleUUID);
            entityToDonjonId.remove(vehicleUUID);
            wave.removeSpawnedEntity(vehicleUUID);
            vehicle.remove();
        }

        if (wave.isCompleted()) {
            int next = donjon.getCurrentWaveIndex() + 1;
            if (next >= donjon.getWaves().size()) {
                completeDonjon(donjon);
            } else {
                alertDonjonPlayers(donjon,
                        "✔ Wave " + wave.getWaveNumber() + " cleared! Next wave in 5 s...", NamedTextColor.GREEN);
                Atlas.instance.getServer().getScheduler().runTaskLater(Atlas.instance, () -> {
                    if (donjon.isInProgress()) startWave(donjon, next);
                }, 100L);
            }
        }
    }

    public static void onBossDamage(LivingEntity boss, double damage, String factionName) {
        UUID uuid = boss.getUniqueId();
        String donjonId = entityToDonjonId.get(uuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null || !wave.isBossWave()) return;

        donjon.getBossDamageMap().merge(factionName, damage, Double::sum);
        updateBossNametag(boss, damage);
    }

    // -------------------------------------------------------------------------
    // Donjon completion / reset / idle
    // -------------------------------------------------------------------------

    private static void completeDonjon(Donjon donjon) {
        Map<String, Double> dmgMap = donjon.getBossDamageMap();
        double totalDmg = dmgMap.values().stream().mapToDouble(Double::doubleValue).sum();

        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        long baseExp = (cfg != null) ? cfg.minExpReward() : 500L;
        long maxExp  = (cfg != null) ? cfg.maxExpReward() : 10000L;
        long totalExp = (long)((baseExp + (donjon.getLevel() / 99.0) * (maxExp - baseExp))
                * donjon.getRarity().getExpMultiplier());

        // Find leading faction
        String mainFaction = donjon.getStartingFaction();
        if (totalDmg > 0) {
            mainFaction = dmgMap.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(mainFaction);
        }

        // Award exp proportional to boss damage
        if (totalDmg > 0) {
            for (Map.Entry<String, Double> e : dmgMap.entrySet()) {
                int share = (int)(totalExp * (e.getValue() / totalDmg));
                if (share > 0) FactionManager.addExpToFaction(e.getKey(), share);
            }
        }

        String mainDisplay = mainFaction != null ? mainFaction : "Unknown";
        Faction mainF = mainFaction != null ? FactionManager.getFaction(mainFaction) : null;
        NamedTextColor mainColor = mainF != null ? mainF.getColor() : NamedTextColor.WHITE;

        broadcastGlobal(
                Component.text("★ The donjon ", NamedTextColor.GOLD)
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text(" was cleared by faction ", NamedTextColor.GOLD))
                        .append(Component.text("[" + mainDisplay + "]", mainColor))
                        .append(Component.text("! " + totalExp + " exp distributed.", NamedTextColor.GOLD)),
                "★ " + donjon.getName() + " cleared!",
                NamedTextColor.GOLD);

        // Play wither death sound for all online players as a server-wide event
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
        }

        Location dropLoc = donjonAnchor(donjon);
        World dropWorld = dropLoc != null ? dropLoc.getWorld() : null;

        // Drop an enchanted Ominous Trial Key when EPIC+ donjon is completed
        if (donjon.getRarity().ordinal() >= DonjonRarity.EPIC.ordinal()) {
            double dropChance = ominousKeyDropChance(donjon.getRarity());
            if (dropWorld != null && ThreadLocalRandom.current().nextDouble() < dropChance) {
                dropWorld.dropItemNaturally(dropLoc, createOminousKey());
            }
        }

        if (dropWorld != null) {
            int level = donjon.getLevel();
            DonjonRarity rarity = donjon.getRarity();
            int scaledAmount = 1 + (int) (level / 99.0 * (rarity.ordinal() + 1));

            // Always drop creeper eggs — amount scales with level and rarity
            dropWorld.dropItemNaturally(dropLoc,
                    ElectricalCreeperManager.createCreeperEgg(scaledAmount));

            // Very low chance to drop an electrical creeper egg
            if (ThreadLocalRandom.current().nextDouble() < ElectricalCreeperManager.electricalDropChance(rarity)) {
                dropWorld.dropItemNaturally(dropLoc, ElectricalCreeperManager.createElectricalCreeperEgg());
            }

            // Always drop TNT — same scaling formula as creeper eggs
            dropWorld.dropItemNaturally(dropLoc, new ItemStack(Material.TNT, scaledAmount));

            // Drop wither skeleton skulls for EPIC+ donjons — same scaling formula
            if (rarity.ordinal() >= DonjonRarity.EPIC.ordinal()) {
                dropWorld.dropItemNaturally(dropLoc, new ItemStack(Material.WITHER_SKELETON_SKULL, scaledAmount));
            }
        }

        setIdle(donjon, null);
    }

    /** Drop-chance for the enchanted Ominous Trial Key: (1 – normalizedWeight) + 10%, clamped to [0, 1]. */
    private static double ominousKeyDropChance(DonjonRarity rarity) {
        int totalWeight = 0;
        for (int w : rarityWeights) totalWeight += w;
        double normalizedWeight = (double) rarityWeights[rarity.ordinal()] / totalWeight;

        return Math.min(1.0, (1.0 - normalizedWeight) + 0.10);
    }

    static void resetDonjon(Donjon donjon) {
        donjon.setInProgress(false);
        donjon.setCurrentWaveIndex(0);
        donjon.setStartingFaction(null);
        donjon.getBossDamageMap().clear();
        donjon.getPlayersInside().clear();
        clearEntities(donjon);
        donjon.getWaves().clear();
        spawnOrUpdateNametag(donjon);
    }

    public static void setIdle(Donjon donjon, String reason) {
        donjon.setStatus(DonjonStatus.IDLE);
        donjon.setInProgress(false);
        donjon.setActivationTime(0);
        donjon.setTimeoutWarned(false);
        donjon.setStartingFaction(null);
        donjon.getBossDamageMap().clear();
        clearEntities(donjon);
        donjon.getWaves().clear();
        donjon.setCurrentWaveIndex(0);
        spawnOrUpdateNametag(donjon);

        if ("timed out".equals(reason)) {
            broadcastGlobal(
                    Component.text("⌛ Donjon ", NamedTextColor.GRAY)
                            .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                            .append(Component.text(" has become idle (timed out).", NamedTextColor.GRAY)),
                    "⌛ " + donjon.getName() + " idle!",
                    NamedTextColor.GRAY);
        }
    }

    private static void clearEntities(Donjon donjon) {
        for (DonjonWave wave : donjon.getWaves()) {
            for (UUID uuid : new HashSet<>(wave.getSpawnedEntities())) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
                entityToDonjonId.remove(uuid);
            }
            wave.getSpawnedEntities().clear();
        }
        for (UUID uuid : new HashSet<>(donjon.getAuxiliaryEntities())) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            entityToDonjonId.remove(uuid);
        }
        donjon.getAuxiliaryEntities().clear();

        // Sweep loaded chunks for any surviving tagged mobs not in tracking sets (e.g. slime splits)
        World world = donjon.getWorld();
        if (world != null) {
            Set<Long> protectedKeys = donjon.getProtectedChunkKeys();
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!protectedKeys.contains(Chunk.getChunkKey(chunk.getX(), chunk.getZ()))) continue;
                for (Entity e : chunk.getEntities()) {
                    if (e instanceof LivingEntity le
                            && le.getPersistentDataContainer().has(keyDonjonId, PersistentDataType.STRING)) {
                        le.remove();
                        entityToDonjonId.remove(le.getUniqueId());
                    }
                }
            }
        }
    }

    /**
     * Registers a freshly-spawned split child (slime, magma cube, etc.) into the current wave
     * so it counts toward wave completion and is cleaned up on reset.
     */
    public static void registerSplitChild(LivingEntity child, Donjon donjon, int waveIndex) {
        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null) return;

        String donjonId = donjon.getId();
        tagEntity(child, donjonId, waveIndex, false);
        child.getPersistentDataContainer().set(keyMobType, PersistentDataType.STRING, child.getType().name());
        child.customName(Component.text(prettyMobName(child.getType().name()), NamedTextColor.YELLOW));
        child.setCustomNameVisible(true);

        wave.addSpawnedEntity(child.getUniqueId());
        entityToDonjonId.put(child.getUniqueId(), donjonId);
    }

    /**
     * Called when a wave mob dies from a non-player cause (fall, drowning, self-explosion, etc.).
     * Removes the dead entity from tracking and schedules a replacement spawn so the wave
     * does not advance — only player kills count towards wave completion.
     */
    public static void replaceWaveMob(LivingEntity deadEntity) {
        UUID uuid = deadEntity.getUniqueId();
        String donjonId = entityToDonjonId.remove(uuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null) return;

        wave.removeSpawnedEntity(uuid);

        // Clean up any vehicle (e.g. chicken in a chicken jockey)
        Entity vehicle = deadEntity.getVehicle();
        if (vehicle != null) {
            donjon.getAuxiliaryEntities().remove(vehicle.getUniqueId());
            entityToDonjonId.remove(vehicle.getUniqueId());
            vehicle.remove();
        }

        String mobType = deadEntity.getPersistentDataContainer().get(keyMobType, PersistentDataType.STRING);
        if (mobType == null) mobType = deadEntity.getType().name();

        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return;

        double lf = donjon.getLevel() / 99.0;
        double hpMult  = cfg.baseHpMultiplier() + lf * (cfg.maxHpMultiplier() - cfg.baseHpMultiplier());
        double atkMult = cfg.baseAttackMultiplier() + lf * (cfg.maxAttackMultiplier() - cfg.baseAttackMultiplier());
        if (wave.isBossWave()) {
            hpMult  *= cfg.bossHpMultiplier();
            atkMult *= cfg.bossAttackMultiplier();
        }

        final String finalMobType  = mobType;
        final double finalHpMult   = hpMult;
        final double finalAtkMult  = atkMult;
        final boolean isBoss       = wave.isBossWave();
        final double speedMult     = isBoss ? cfg.bossSpeedMultiplier() : 1.0;
        final int waveIndex        = donjon.getCurrentWaveIndex();

        Atlas.instance.getServer().getScheduler().runTaskLater(Atlas.instance, () -> {
            if (!donjon.isInProgress()) return;
            if (donjon.getCurrentWave() != wave) return;
            Location spawnLoc = randomSpawnLocation(donjon);
            if (spawnLoc == null) return;
            List<UUID> newUuids = spawnMobEntity(finalMobType, spawnLoc, finalHpMult, finalAtkMult,
                    isBoss, speedMult, donjonId, waveIndex);
            for (UUID newUuid : newUuids) {
                wave.addSpawnedEntity(newUuid);
                entityToDonjonId.put(newUuid, donjonId);
            }
        }, 2L);
    }

    /**
     * Called when a tracked wave mob transforms into another entity (e.g. zombie → drowned).
     * Swaps the old entity's tracking entries for the new entity and copies all donjon PDC tags.
     */
    public static void transferWaveTracking(LivingEntity oldEntity, LivingEntity newEntity) {
        UUID oldUuid = oldEntity.getUniqueId();
        String donjonId = entityToDonjonId.remove(oldUuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        DonjonWave wave = donjon != null ? donjon.getCurrentWave() : null;
        if (wave != null) {
            wave.removeSpawnedEntity(oldUuid);
            wave.addSpawnedEntity(newEntity.getUniqueId());
        }

        entityToDonjonId.put(newEntity.getUniqueId(), donjonId);

        // Copy donjon PDC tags to the new entity
        int waveIndex = oldEntity.getPersistentDataContainer()
                .getOrDefault(keyDonjonWave, PersistentDataType.INTEGER, 0);
        boolean isBoss = oldEntity.getPersistentDataContainer().has(keyIsBoss, PersistentDataType.BYTE);
        tagEntity(newEntity, donjonId, waveIndex, isBoss);

        String mobType = oldEntity.getPersistentDataContainer().get(keyMobType, PersistentDataType.STRING);
        if (mobType != null) {
            newEntity.getPersistentDataContainer().set(keyMobType, PersistentDataType.STRING, mobType);
        }

        // Update the custom name to reflect the new entity type
        if (isBoss) {
            newEntity.customName(buildBossNametag(newEntity, newEntity.getType().name()));
        } else {
            newEntity.customName(Component.text(prettyMobName(newEntity.getType().name()), NamedTextColor.YELLOW));
        }
        newEntity.setCustomNameVisible(true);
    }

    // -------------------------------------------------------------------------
    // Nametag
    // -------------------------------------------------------------------------

    public static void spawnOrUpdateNametag(Donjon donjon) {
        // Prefer vault block, then existing nametag, then teleport spawn — bail if none set
        Location base = donjonAnchor(donjon);
        if (base == null) return;
        Location displayLoc = base.clone().add(0.5, 3.5, 0.5);

        TextDisplay display = null;
        UUID existingUUID = donjon.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }

        if (display == null) {
            if (!displayLoc.isChunkLoaded()) return;
            final String id = donjon.getId();
            display = displayLoc.getWorld().spawn(displayLoc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
                td.getPersistentDataContainer().set(keyTotemDisplay, PersistentDataType.STRING, id);
            });
            donjon.setTextDisplayUUID(display.getUniqueId());
        } else {
            // Teleport to updated position if the vault/nametag location changed
            Location current = display.getLocation();
            if (current.getBlockX() != displayLoc.getBlockX()
                    || current.getBlockY() != displayLoc.getBlockY()
                    || current.getBlockZ() != displayLoc.getBlockZ()) {
                display.teleport(displayLoc);
            }
        }

        display.text(buildNametag(donjon));
    }

    public static void restoreNametag(TextDisplay display) {
        String id = display.getPersistentDataContainer().get(keyTotemDisplay, PersistentDataType.STRING);
        if (id == null) return;

        Donjon donjon = donjons.get(id);
        if (donjon == null) {
            // Orphaned display from a deleted donjon
            display.remove();
            return;
        }

        donjon.setTextDisplayUUID(display.getUniqueId());
        display.text(buildNametag(donjon));
    }

    private static Component buildNametag(Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        String typeName = cfg != null ? cfg.displayName() : donjon.getType();

        Component statusLine;
        if (donjon.isInProgress()) {
            statusLine = Component.text("⚔ IN PROGRESS ⚔", NamedTextColor.YELLOW);
        } else if (donjon.getStatus() == DonjonStatus.ACTIVE) {
            statusLine = Component.text("◆ ACTIVE ◆", NamedTextColor.GREEN);
        } else {
            statusLine = Component.text("◇ IDLE ◇", NamedTextColor.DARK_GRAY);
        }

        boolean idle = donjon.getStatus() == DonjonStatus.IDLE && !donjon.isInProgress();

        Component nameLine = Component.text("[" + typeName + "] ", NamedTextColor.GRAY)
                .append(Component.text(donjon.getName(),
                        idle ? NamedTextColor.DARK_GRAY : donjon.getRarity().getColor()));

        if (idle) {
            return nameLine
                    .append(Component.newline())
                    .append(statusLine);
        }

        return nameLine
                .append(Component.newline())
                .append(Component.text("Lv." + donjon.getLevel() + " ✦ ", NamedTextColor.YELLOW))
                .append(Component.text(donjon.getRarity().getDisplayName(), donjon.getRarity().getColor()))
                .append(Component.newline())
                .append(statusLine);
    }

    // -------------------------------------------------------------------------
    // Mob spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns one logical "mob entry" (which may produce multiple entities for special
     * types like CHICKEN_JOCKEY).  Returns the UUIDs that should be tracked in the wave.
     */
    private static List<UUID> spawnMobEntity(String mobTypeName, Location loc, double hpMult, double atkMult, boolean isBoss, double speedMult, String donjonId, int waveIndex) {
        World world = loc.getWorld();
        if (world == null) return List.of();

        if ("CHICKEN_JOCKEY".equalsIgnoreCase(mobTypeName)) {
            return spawnChickenJockey(loc, hpMult, atkMult, isBoss, speedMult, donjonId, waveIndex);
        }

        LivingEntity entity = spawnSingleMob(mobTypeName, loc);
        if (entity == null) return List.of();

        applyMultipliers(entity, hpMult, atkMult, speedMult);
        tagEntity(entity, donjonId, waveIndex, isBoss);
        entity.getPersistentDataContainer().set(keyMobType, PersistentDataType.STRING, mobTypeName.toUpperCase());
        if (isBoss) {
            entity.customName(buildBossNametag(entity, mobTypeName));
        } else {
            entity.customName(Component.text(prettyMobName(mobTypeName), NamedTextColor.YELLOW));
        }
        entity.setCustomNameVisible(true);
        return List.of(entity.getUniqueId());
    }

    private static LivingEntity spawnSingleMob(String name, Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        return switch (name.toUpperCase()) {
            case "ZOMBIE" -> w.spawn(loc, Zombie.class);
            case "BABY_ZOMBIE" -> w.spawn(loc, Zombie.class, Ageable::setBaby);
            case "HUSK" -> w.spawn(loc, Husk.class);
            case "DROWNED" -> w.spawn(loc, Drowned.class);
            case "GUARDIAN" -> w.spawn(loc, Guardian.class);
            case "ELDER_GUARDIAN" -> w.spawn(loc, ElderGuardian.class);
            case "SKELETON" -> w.spawn(loc, Skeleton.class);
            case "STRAY" -> w.spawn(loc, Stray.class);
            case "BOGGED" -> w.spawn(loc, Bogged.class);
            case "WITHER_SKELETON" -> w.spawn(loc, WitherSkeleton.class);
            case "SPIDER" -> w.spawn(loc, Spider.class);
            case "CAVE_SPIDER" -> w.spawn(loc, CaveSpider.class);
            case "SLIME" -> w.spawn(loc, Slime.class, s -> s.setSize(2));
            case "MAGMA_CUBE" -> w.spawn(loc, MagmaCube.class, s -> s.setSize(2));
            case "SILVERFISH" -> w.spawn(loc, Silverfish.class);
            case "ENDERMITE" -> w.spawn(loc, Endermite.class);
            case "ENDERMAN" -> w.spawn(loc, Enderman.class);
            case "PHANTOM" -> w.spawn(loc, Phantom.class);
            case "SHULKER" -> w.spawn(loc, Shulker.class);
            case "BLAZE" -> w.spawn(loc, Blaze.class);
            case "GHAST" -> w.spawn(loc, Ghast.class);
            case "PIGLIN" -> w.spawn(loc, Piglin.class);
            case "PIGLIN_BRUTE" -> w.spawn(loc, PiglinBrute.class);
            case "WITCH" -> w.spawn(loc, Witch.class);
            case "PILLAGER" -> w.spawn(loc, Pillager.class);
            case "VINDICATOR" -> w.spawn(loc, Vindicator.class);
            case "CREEPER" -> w.spawn(loc, Creeper.class);
            case "BREEZE" -> w.spawn(loc, Breeze.class);
            default -> null;
        };
    }

    private static List<UUID> spawnChickenJockey(Location loc, double hpMult, double atkMult,
                                                   boolean isBoss, double speedMult,
                                                   String donjonId, int waveIndex) {
        World w = loc.getWorld();
        if (w == null) return List.of();

        Chicken chicken = w.spawn(loc, Chicken.class);
        Zombie baby = w.spawn(loc, Zombie.class, Ageable::setBaby);
        chicken.addPassenger(baby);

        applyMultipliers(baby, hpMult, atkMult, speedMult);
        tagEntity(baby, donjonId, waveIndex, isBoss);
        baby.getPersistentDataContainer().set(keyMobType, PersistentDataType.STRING, "CHICKEN_JOCKEY");
        baby.customName(Component.text(prettyMobName("CHICKEN_JOCKEY"),
                isBoss ? NamedTextColor.DARK_RED : NamedTextColor.YELLOW));
        baby.setCustomNameVisible(true);
        // Tag chicken for reference but don't include in wave count; track as auxiliary
        entityToDonjonId.put(chicken.getUniqueId(), donjonId);

        Donjon d = donjons.get(donjonId);
        if (d != null) d.getAuxiliaryEntities().add(chicken.getUniqueId());

        return List.of(baby.getUniqueId()); // only zombie counts for wave completion
    }

    private static void applyMultipliers(LivingEntity e, double hpMult, double atkMult, double speedMult) {
        AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(maxHp.getBaseValue() * hpMult);
            e.setHealth(maxHp.getValue());
        }
        AttributeInstance atk = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * atkMult);
        }
        if (speedMult != 1.0) {
            AttributeInstance speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * speedMult);
            }
        }
    }

    private static void tagEntity(LivingEntity e, String donjonId, int waveIndex, boolean isBoss) {
        e.getPersistentDataContainer().set(keyDonjonId,   PersistentDataType.STRING,  donjonId);
        e.getPersistentDataContainer().set(keyDonjonWave, PersistentDataType.INTEGER, waveIndex);
        if (isBoss) {
            e.getPersistentDataContainer().set(keyIsBoss, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // -------------------------------------------------------------------------
    // Boss drops
    // -------------------------------------------------------------------------

    private static void spawnBossDrops(Location loc, Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return;

        BossDropConfig dropCfg = cfg.bossDrops().get(donjon.getRarity());
        if (dropCfg == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        for (ItemStack item : dropCfg.rollDrops()) {
            world.dropItemNaturally(loc, item);
        }
    }

    /**
     * Creates a donjon instance at {@code origin} without placing any NBT structure.
     * The protected area is initialised to the single chunk under the player.
     * Spawn points and the nametag location must be set afterwards via admin commands.
     *
     * @return the created {@link Donjon}, or {@code null} if the chunk is already inside another donjon.
     */
    public static Donjon generateDonjon(String type, String name, Location origin) {
        DonjonTypeConfig cfg = typeConfigMap.get(type);
        if (cfg == null) return null;

        int cx = origin.getBlockX() >> 4;
        int cz = origin.getBlockZ() >> 4;
        long centerKey = Chunk.getChunkKey(cx, cz);

        // Reject if the center chunk is already inside another donjon
        for (Donjon existing : donjons.values()) {
            if (existing.getWorld().equals(origin.getWorld())
                    && existing.getProtectedChunkKeys().contains(centerKey)) {
                return null;
            }
        }

        String id    = UUID.randomUUID().toString().substring(0, 8);
        int level    = randomLevel();
        DonjonRarity rarity = randomRarity();

        Donjon donjon = new Donjon(id, name, type, origin.getWorld(), level, rarity);
        donjon.setStatus(DonjonStatus.IDLE);

        donjons.put(id, donjon);

        Bukkit.getScheduler().runTaskLater(Atlas.instance, () -> {
            Storage.save(DataDomain.DONJONS);
        }, 20L);

        return donjon;
    }

    /**
     * Adds the chunk at ({@code chunkX}, {@code chunkZ}) in {@code world} to the
     * protected area of the specified donjon. Returns false if the donjon does not
     * exist or belongs to a different world.
     */
    public static boolean addProtectedChunk(String donjonId, World world, int chunkX, int chunkZ) {
        Donjon d = donjons.get(donjonId);
        if (d == null || !d.getWorld().equals(world)) return false;
        d.getProtectedChunkKeys().add(Chunk.getChunkKey(chunkX, chunkZ));
        return true;
    }

    /**
     * Removes the chunk at ({@code chunkX}, {@code chunkZ}) in {@code world} from the
     * protected area of the specified donjon. Returns false if the donjon does not
     * exist, belongs to a different world, or did not claim that chunk.
     */
    public static boolean removeProtectedChunk(String donjonId, World world, int chunkX, int chunkZ) {
        Donjon d = donjons.get(donjonId);
        if (d == null || !d.getWorld().equals(world)) return false;
        return d.getProtectedChunkKeys().remove(Chunk.getChunkKey(chunkX, chunkZ));
    }

    /**
     * Clears every claimed chunk for the specified donjon. Returns the number of
     * chunks that were unclaimed, or -1 if no donjon with that id exists.
     */
    public static int clearProtectedChunks(String donjonId) {
        Donjon d = donjons.get(donjonId);
        if (d == null) return -1;
        int count = d.getProtectedChunkKeys().size();
        d.getProtectedChunkKeys().clear();
        return count;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns the donjon whose vault block is at the given location, or null if none matches.
     * Used by the listener to find which donjon a player is triggering.
     */
    public static Donjon getDonjonAtVault(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (Donjon d : donjons.values()) {
            Location vl = d.getVaultLocation();
            if (vl == null) continue;
            if (!vl.getWorld().equals(loc.getWorld())) continue;
            if (vl.getBlockX() == loc.getBlockX()
                    && vl.getBlockY() == loc.getBlockY()
                    && vl.getBlockZ() == loc.getBlockZ()) return d;
        }
        return null;
    }

    /** Returns the set of type keys currently loaded from donjons.yml (e.g. "trial", "desert"). */
    public static Set<String> getAvailableTypes() {
        return Collections.unmodifiableSet(typeConfigMap.keySet());
    }

    /** Returns the display name for a type key, or the key itself if the type is not loaded. */
    public static String getTypeDisplayName(String typeKey) {
        DonjonTypeConfig cfg = typeConfigMap.get(typeKey);
        return cfg != null ? cfg.displayName() : typeKey;
    }

    public static boolean isBossEntity(LivingEntity e) {
        return e.getPersistentDataContainer().has(keyIsBoss, PersistentDataType.BYTE);
    }

    public static boolean isDonjonEntity(LivingEntity e) {
        return e.getPersistentDataContainer().has(keyDonjonId, PersistentDataType.STRING);
    }

    public static String getDonjonIdForEntity(UUID uuid) {
        return entityToDonjonId.get(uuid);
    }

    public static Donjon getDonjon(String id) {
        return donjons.get(id);
    }

    public static Map<String, Donjon> getDonjons() {
        return Collections.unmodifiableMap(donjons);
    }

    public static boolean isInDonjon(Location loc, Donjon donjon) {
        if (!loc.getWorld().equals(donjon.getWorld())) return false;
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);

        return donjon.getProtectedChunkKeys().contains(key);
    }

    /** Returns true if the given chunk is within any donjon's protected area. */
    public static void recordPlayerVisit(UUID playerUUID, String donjonId) {
        playerVisitedDonjons.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(donjonId);
    }

    public static boolean hasPlayerVisitedDonjon(UUID playerUUID, String donjonId) {
        Set<String> visited = playerVisitedDonjons.get(playerUUID);
        return visited != null && visited.contains(donjonId);
    }

    public static boolean isChunkInDonjon(String worldName, int chunkX, int chunkZ) {
        long key = Chunk.getChunkKey(chunkX, chunkZ);

        for (Donjon d : donjons.values()) {
            if (d.getWorld().getName().equals(worldName)
                    && d.getProtectedChunkKeys().contains(key)) {
                return true;
            }
        }

        return false;
    }

    public static Donjon getDonjonAtChunk(World world, long chunkKey) {
        for (Donjon d : donjons.values()) {
            if (d.getWorld().equals(world) && d.getProtectedChunkKeys().contains(chunkKey)) {
                return d;
            }
        }

        return null;
    }

    /** Returns the number of seconds until the next scheduled donjon activation (0 if overdue). */
    public static long getSecondsUntilNextActivation() {
        long remaining = (lastActivationTime + activationIntervalMs) - System.currentTimeMillis();
        return Math.max(0, remaining) / 1000L;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void activateRandomDonjon() {
        long activeCount = donjons.values().stream()
                .filter(d -> d.getStatus() == DonjonStatus.ACTIVE)
                .count();
        if (activeCount >= maxActiveDonjonons) return;

        List<Donjon> idle = donjons.values().stream()
                .filter(d -> d.getStatus() == DonjonStatus.IDLE)
                .toList();
        if (idle.isEmpty()) return;

        Donjon selected = idle.get(ThreadLocalRandom.current().nextInt(idle.size()));
        activateDonjon(selected);
    }

    private static int randomLevel() {
        int totalWeight = 0;
        for (int[] range : difficultyRanges) totalWeight += range[2];
        int roll = ThreadLocalRandom.current().nextInt(Math.max(totalWeight, 1));
        int cumulative = 0;
        for (int[] range : difficultyRanges) {
            cumulative += range[2];
            if (roll < cumulative) {
                return ThreadLocalRandom.current().nextInt(range[0], range[1] + 1);
            }
        }
        return 0;
    }

    private static DonjonRarity randomRarity() {
        int total = 0;
        for (int w : rarityWeights) total += w;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(total, 1));
        int cumulative = 0;
        DonjonRarity[] rarities = DonjonRarity.values();
        for (int i = 0; i < rarityWeights.length; i++) {
            cumulative += rarityWeights[i];
            if (roll < cumulative) return rarities[i];
        }
        return DonjonRarity.COMMON;
    }


    private static Location randomSpawnLocation(Donjon donjon) {
        List<Location> spawnPoints = donjon.getSpawnPoints();
        if (!spawnPoints.isEmpty()) {
            return spawnPoints.get(ThreadLocalRandom.current().nextInt(spawnPoints.size())).clone();
        }
        // Fallback when no wave spawns are configured: jitter around the donjon's anchor.
        // /donjon activate refuses to start without spawn points, so this should only be hit
        // by edge-case admin tooling.
        Location anchor = donjonAnchor(donjon);
        if (anchor == null) return null;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int range = 8;
        return anchor.clone().add(rand.nextInt(-range, range + 1), 0, rand.nextInt(-range, range + 1));
    }

    /** Returns a representative position for the donjon, or null if none of the
     *  positional fields ({@code vault}, {@code nametag}, {@code teleport_spawn}) are set. */
    private static Location donjonAnchor(Donjon donjon) {
        if (donjon.getVaultLocation() != null)   return donjon.getVaultLocation();
        if (donjon.getNametagLocation() != null) return donjon.getNametagLocation();
        if (donjon.getTeleportSpawn() != null)   return donjon.getTeleportSpawn();
        return null;
    }

    private static void broadcastGlobal(Component chatMsg, String shortTitle, NamedTextColor color) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : players) p.sendMessage(chatMsg);
        TitleUtil.broadcastAlertBold(players, shortTitle, color);
    }

    private static void alertDonjonPlayers(Donjon donjon, String text, NamedTextColor color) {
        World world = donjon.getWorld();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .collect(Collectors.toList());
        TitleUtil.broadcastAlertBold(players, text, color);
    }

    // -------------------------------------------------------------------------
    // Sound helpers
    // -------------------------------------------------------------------------

    /** 4 trial-spawner spawn sounds played at random when a wave begins. */
    private static final List<Sound> WAVE_SPAWN_SOUNDS = List.of(
            Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB,
            Sound.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER,
            Sound.BLOCK_TRIAL_SPAWNER_CLOSE_SHUTTER,
            Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS
    );

    private static void soundToDonjonPlayers(Donjon donjon, Sound sound, float volume, float pitch) {
        World world = donjon.getWorld();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .forEach(p -> p.playSound(p.getLocation(), sound, SoundCategory.MASTER, volume, pitch));
    }

    private static void randomSoundToDonjonPlayers(Donjon donjon, List<Sound> sounds, float volume, float pitch) {
        if (sounds.isEmpty()) return;
        soundToDonjonPlayers(donjon,
                sounds.get(ThreadLocalRandom.current().nextInt(sounds.size())), volume, pitch);
    }

    // -------------------------------------------------------------------------
    // Boss nametag helpers
    // -------------------------------------------------------------------------

    private static Component buildBossNametag(LivingEntity boss, String mobTypeName) {
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = maxHpAttr != null ? (int) maxHpAttr.getValue() : 0;
        int hp    = (int) Math.ceil(boss.getHealth());
        return Component.text(prettyMobName(mobTypeName) + " ", NamedTextColor.DARK_RED)
                .append(Component.text("❤ " + hp + "/" + maxHp, NamedTextColor.RED));
    }

    /** Updates the boss nametag to reflect HP remaining after {@code damage} is subtracted. */
    public static void updateBossNametag(LivingEntity boss, double damage) {
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = maxHpAttr != null ? (int) maxHpAttr.getValue() : 0;
        int hp    = (int) Math.max(0, Math.ceil(boss.getHealth() - damage));
        String mobName = prettyMobName(boss.getType().name());
        boss.customName(Component.text(mobName + " ", NamedTextColor.DARK_RED)
                .append(Component.text("❤ " + hp + "/" + maxHp, NamedTextColor.RED)));
    }

    // -------------------------------------------------------------------------
    // Donjon-player subtitle helper
    // -------------------------------------------------------------------------

    private static void alertDonjonPlayersSubtitle(Donjon donjon, String text, NamedTextColor color) {
        World world = donjon.getWorld();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .collect(Collectors.toList());
        TitleUtil.broadcastSubtitle(players, text, color);
    }

    /** Converts a config mob name like "CAVE_SPIDER" to "Cave Spider". */
    private static String prettyMobName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> clazz, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(clazz, value); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
