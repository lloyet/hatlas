package org.minecraft.atlas.crystal;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.ItemBytes;
import org.minecraft.atlas.storage.Storage;
import org.minecraft.atlas.storage.model.ChunkPos;
import org.minecraft.atlas.storage.model.CrystalsData;
import org.minecraft.atlas.storage.model.LocationData;

import java.util.*;

public class AtlasCrystalManager {

    // ── PDC keys ──────────────────────────────────────────────────────────────
    private static NamespacedKey KEY_FACTION;
    private static NamespacedKey KEY_HP;
    private static NamespacedKey KEY_MAX_HP;
    private static NamespacedKey KEY_NAME;
    private static NamespacedKey KEY_HOME;           // legacy migration only
    private static NamespacedKey KEY_IMMUNE_UNTIL;
    private static NamespacedKey KEY_NAMETAG_DISPLAY;
    private static NamespacedKey KEY_HP_BONUS;
    private static NamespacedKey KEY_REGEN_BONUS;
    private static NamespacedKey KEY_CLAIM_CAPACITY;
    private static NamespacedKey KEY_SPENT_SP;
    private static NamespacedKey KEY_PROTECTION_MS;          // legacy (single-sum)
    private static NamespacedKey KEY_PROTECTIONS;            // new: long[] of owned durations
    private static NamespacedKey KEY_BROKEN_DURATIONS;       // new: long[] of broken durations
    private static NamespacedKey KEY_BROKEN_AT_MS;           // new: long[] parallel to BROKEN_DURATIONS

    public static NamespacedKey getKeyFaction() {
        if (KEY_FACTION == null) KEY_FACTION = new NamespacedKey(Atlas.instance, "atlas_crystal_faction");
        return KEY_FACTION;
    }
    private static NamespacedKey getKeyHp() {
        if (KEY_HP == null) KEY_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_hp");
        return KEY_HP;
    }
    private static NamespacedKey getKeyMaxHp() {
        if (KEY_MAX_HP == null) KEY_MAX_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_max_hp");
        return KEY_MAX_HP;
    }
    public static NamespacedKey getKeyName() {
        if (KEY_NAME == null) KEY_NAME = new NamespacedKey(Atlas.instance, "atlas_crystal_name");
        return KEY_NAME;
    }
    private static NamespacedKey getKeyHome() {
        if (KEY_HOME == null) KEY_HOME = new NamespacedKey(Atlas.instance, "atlas_crystal_home");
        return KEY_HOME;
    }
    private static NamespacedKey getKeyImmuneUntil() {
        if (KEY_IMMUNE_UNTIL == null) KEY_IMMUNE_UNTIL = new NamespacedKey(Atlas.instance, "atlas_crystal_immune_until");
        return KEY_IMMUNE_UNTIL;
    }
    private static NamespacedKey getKeyNametagDisplay() {
        if (KEY_NAMETAG_DISPLAY == null) KEY_NAMETAG_DISPLAY = new NamespacedKey(Atlas.instance, "atlas_crystal_nametag_display");
        return KEY_NAMETAG_DISPLAY;
    }
    private static NamespacedKey getKeyHpBonus() {
        if (KEY_HP_BONUS == null) KEY_HP_BONUS = new NamespacedKey(Atlas.instance, "atlas_crystal_hp_bonus");
        return KEY_HP_BONUS;
    }
    private static NamespacedKey getKeyRegenBonus() {
        if (KEY_REGEN_BONUS == null) KEY_REGEN_BONUS = new NamespacedKey(Atlas.instance, "atlas_crystal_regen_bonus");
        return KEY_REGEN_BONUS;
    }
    private static NamespacedKey getKeyClaimCapacity() {
        if (KEY_CLAIM_CAPACITY == null) KEY_CLAIM_CAPACITY = new NamespacedKey(Atlas.instance, "atlas_crystal_claim_cap");
        return KEY_CLAIM_CAPACITY;
    }
    private static NamespacedKey getKeySpentSp() {
        if (KEY_SPENT_SP == null) KEY_SPENT_SP = new NamespacedKey(Atlas.instance, "atlas_crystal_spent_sp");
        return KEY_SPENT_SP;
    }
    private static NamespacedKey getKeyProtectionMs() {
        if (KEY_PROTECTION_MS == null) KEY_PROTECTION_MS = new NamespacedKey(Atlas.instance, "atlas_crystal_protection_ms");
        return KEY_PROTECTION_MS;
    }
    private static NamespacedKey getKeyProtections() {
        if (KEY_PROTECTIONS == null) KEY_PROTECTIONS = new NamespacedKey(Atlas.instance, "atlas_crystal_protections");
        return KEY_PROTECTIONS;
    }
    private static NamespacedKey getKeyBrokenDurations() {
        if (KEY_BROKEN_DURATIONS == null) KEY_BROKEN_DURATIONS = new NamespacedKey(Atlas.instance, "atlas_crystal_broken_durations");
        return KEY_BROKEN_DURATIONS;
    }
    private static NamespacedKey getKeyBrokenAtMs() {
        if (KEY_BROKEN_AT_MS == null) KEY_BROKEN_AT_MS = new NamespacedKey(Atlas.instance, "atlas_crystal_broken_at_ms");
        return KEY_BROKEN_AT_MS;
    }

    // ── Config ────────────────────────────────────────────────────────────────
    public static double regenPerSecond         = 1.5;
    public static long   immunityBaseMs         = 18_000_000L;
    public static int    immunityMultiplierBase = 2;
    public static long   regenTimeoutMs         = 60_000L;

    public static void loadConfig(FileConfiguration config) {
        regenPerSecond         = config.getDouble("crystal.regen_per_second", 1.5);
        immunityBaseMs         = config.getLong("crystal.immunity_base_seconds", 18000L) * 1000L;
        immunityMultiplierBase = config.getInt("crystal.immunity_multiplier", 2);
        regenTimeoutMs         = config.getLong("crystal.regen_timeout_seconds", 20L) * 1000L;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    /** entityUUID → AtlasCrystal */
    private static final Map<UUID, AtlasCrystal> crystals = new HashMap<>();

    /**
     * factionName → (crystalEntityUUID → AtlasCrystal).
     * LinkedHashMap preserves insertion order.
     */
    private static final Map<String, Map<UUID, AtlasCrystal>> factionCrystals = new HashMap<>();

    // ── Register ──────────────────────────────────────────────────────────────

    /** Registers a newly spawned atlas crystal with base HP and max HP. */
    public static AtlasCrystal register(EnderCrystal entity, String factionName) {
        double base = AtlasCrystal.BASE_MAX_HP;
        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, base, base);
        entity.setCustomNameVisible(false);
        entity.getPersistentDataContainer().set(getKeyFaction(), PersistentDataType.STRING, factionName);
        entity.getPersistentDataContainer().set(getKeyHp(), PersistentDataType.DOUBLE, base);
        entity.getPersistentDataContainer().set(getKeyMaxHp(), PersistentDataType.DOUBLE, base);
        crystals.put(entity.getUniqueId(), crystal);
        crystal.updateNametag();
        return crystal;
    }

    /** Finalizes the crystal's name after the player provides it. Auto-claims center chunk. */
    public static void assignName(AtlasCrystal crystal, String name) {
        crystal.setName(name);
        factionCrystals
                .computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(crystal.getEntityUUID(), crystal);

        var entity = crystal.getEntity();
        if (entity != null) {
            entity.getPersistentDataContainer()
                    .set(getKeyName(), PersistentDataType.STRING, name);

            // Auto-claim the center chunk for this crystal
            Location loc = entity.getLocation();
            String worldName = loc.getWorld().getName();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            String chunkKey = worldName + ":" + cx + ":" + cz;
            if (!crystal.getClaimedChunks().contains(chunkKey)) {
                crystal.getClaimedChunks().add(chunkKey);
                FactionClaimManager.claimChunk(crystal.getFactionName(), worldName, cx, cz);
            }
        }

        crystal.updateNametag();
    }

    /** Persists a crystal's home location (set on the crystal in-memory) to the data file. */
    public static void saveHome(AtlasCrystal crystal) {
        Location home = crystal.getHome();
        if (home == null || home.getWorld() == null) return;
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
    }

    // ── Persistence — scalar state in PDC ─────────────────────────────────────

    /**
     * Persists the full mutable state of a crystal to PDC.
     * Call after any significant state change (damage, upgrade applied).
     */
    public static void persistCrystalState(AtlasCrystal crystal) {
        if (crystal.getEntity() == null) return;
        var pdc = crystal.getEntity().getPersistentDataContainer();
        pdc.set(getKeyHp(),           PersistentDataType.DOUBLE,  crystal.getHp());
        pdc.set(getKeyMaxHp(),        PersistentDataType.DOUBLE,  crystal.getMaxHp());
        pdc.set(getKeyImmuneUntil(),  PersistentDataType.LONG,    crystal.getImmuneUntilMillis());
        pdc.set(getKeyHpBonus(),      PersistentDataType.DOUBLE,  crystal.getHpBonus());
        pdc.set(getKeyRegenBonus(),   PersistentDataType.DOUBLE,  crystal.getRegenBonus());
        pdc.set(getKeyClaimCapacity(),PersistentDataType.INTEGER, crystal.getClaimCapacity());
        pdc.set(getKeySpentSp(),      PersistentDataType.INTEGER, crystal.getSpentSkillPoints());

        // Protection list: long[] of owned durations
        long[] owned = crystal.getPurchasedProtections().stream().mapToLong(Long::longValue).toArray();
        pdc.set(getKeyProtections(), PersistentDataType.LONG_ARRAY, owned);

        // Broken protections: parallel long[] of durations and broken-at timestamps
        Map<Long, Long> broken = crystal.getBrokenProtections();
        long[] brokenDur = new long[broken.size()];
        long[] brokenAt  = new long[broken.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : broken.entrySet()) {
            brokenDur[i] = e.getKey();
            brokenAt[i]  = e.getValue();
            i++;
        }
        pdc.set(getKeyBrokenDurations(), PersistentDataType.LONG_ARRAY, brokenDur);
        pdc.set(getKeyBrokenAtMs(),      PersistentDataType.LONG_ARRAY, brokenAt);
    }

    // ── Persistence — storage snapshot / restore ──────────────────────────────

    /** Immutable copy of every crystal (loaded or stub), for storage. Main thread only. */
    public static CrystalsData snapshot() {
        List<CrystalsData.CrystalRow> rows = new ArrayList<>();
        for (AtlasCrystal c : crystals.values()) {
            List<ChunkPos> claims = new ArrayList<>();
            for (String key : c.getClaimedChunks()) {
                ChunkPos p = ChunkPos.fromRuntimeKey(key);
                if (p != null) claims.add(p);
            }
            Map<Integer, CrystalsData.Chest> chests = new TreeMap<>();
            List<Integer> sizes = c.getPurchasedChestSizes();
            for (int i = 0; i < sizes.size(); i++) chests.put(i, new CrystalsData.Chest(i, sizes.get(i), null));
            for (Map.Entry<Integer, ItemStack[]> e : c.getChestContentsMap().entrySet()) {
                int idx = e.getKey();
                Integer size = idx < sizes.size() ? sizes.get(idx) : null;
                chests.put(idx, new CrystalsData.Chest(idx, size, ItemBytes.fromArray(e.getValue())));
            }
            Map<Long, CrystalsData.Protection> prot = new TreeMap<>();
            for (long d : c.getPurchasedProtections()) prot.put(d, new CrystalsData.Protection(d, true, null));
            for (Map.Entry<Long, Long> e : c.getBrokenProtections().entrySet()) {
                CrystalsData.Protection p = prot.get(e.getKey());
                prot.put(e.getKey(), new CrystalsData.Protection(e.getKey(), p != null && p.purchased(), e.getValue()));
            }
            rows.add(new CrystalsData.CrystalRow(c.getEntityUUID(), c.getFactionName(), c.getName(),
                    c.getHp(), c.getMaxHp(), c.isOutpost(), LocationData.of(c.getHome()),
                    c.getHpBonus(), c.getRegenBonus(), c.getClaimCapacity(), c.getSpentSkillPoints(),
                    c.getImmuneUntilMillis(),
                    List.copyOf(claims), List.copyOf(chests.values()), List.copyOf(prot.values())));
        }
        return new CrystalsData(List.copyOf(rows));
    }

    /**
     * Builds a stub for every stored crystal so the GUI, /f home and claims work before the
     * chunks load, and rebuilds the FactionClaimManager cache. Crystals whose faction no
     * longer exists (a disband lost by a crash) are dropped. Call after FactionManager.restore.
     *
     * @return number of orphan crystals dropped (the caller saves if > 0)
     */
    public static int restoreData(CrystalsData data) {
        crystals.clear();
        factionCrystals.clear();
        List<String> orphans = new ArrayList<>();
        for (CrystalsData.CrystalRow row : data.crystals()) {
            String factionName = row.faction();
            if (factionName == null) continue;
            if (FactionManager.getFaction(factionName) == null) {
                orphans.add(row.uuid() + " (" + factionName + ")");
                continue;
            }
            AtlasCrystal stub = new AtlasCrystal(row.uuid(), factionName, row.hp(), row.maxHp());
            stub.setName(row.name() == null ? "" : row.name());
            if (row.outpost()) stub.setOutpost(true);
            if (row.home() != null) {
                Location home = row.home().toLocation();
                if (home != null) stub.setHome(home);
            }
            if (row.hpBonus() != null) stub.restoreHpBonus(row.hpBonus());
            if (row.regenBonus() != null) stub.restoreRegenBonus(row.regenBonus());
            if (row.claimCapacity() != null) stub.restoreClaimCapacity(row.claimCapacity());
            if (row.spentSkillPoints() != null) stub.restoreSpentSkillPoints(row.spentSkillPoints());
            if (row.immuneUntil() != null) stub.setImmuneUntilMillis(row.immuneUntil());

            // Claims: also populate the FactionClaimManager runtime cache.
            for (ChunkPos p : row.claims()) {
                stub.getClaimedChunks().add(p.runtimeKey());
                FactionClaimManager.claimChunk(factionName, p.world(), p.x(), p.z());
            }
            for (CrystalsData.Chest ch : row.chests()) {
                if (ch.size() != null) stub.getPurchasedChestSizes().add(ch.size());
            }
            for (CrystalsData.Chest ch : row.chests()) {
                if (ch.contents() == null) continue;
                int size = ch.size() != null ? ch.size() : 27;
                ItemStack[] saved = ItemBytes.toArray(ch.contents());
                ItemStack[] contents = new ItemStack[Math.max(size, saved.length)];
                System.arraycopy(saved, 0, contents, 0, saved.length);
                stub.setChestContents(ch.index(), contents);
            }
            for (CrystalsData.Protection p : row.protections()) {
                if (p.purchased()) stub.addPurchasedProtection(p.durationMs());
                if (p.brokenAt() != null && p.brokenAt() > 0) stub.breakProtection(p.durationMs(), p.brokenAt());
            }
            crystals.put(row.uuid(), stub);
            factionCrystals.computeIfAbsent(factionName, k -> new LinkedHashMap<>()).put(row.uuid(), stub);
        }
        if (!orphans.isEmpty()) {
            Atlas.instance.getLogger().warning("Dropped " + orphans.size()
                    + " orphan crystal(s) whose faction no longer exists: " + String.join(", ", orphans));
        }
        return orphans.size();
    }

    // ── Restore (called when entity chunk loads) ──────────────────────────────

    /**
     * Restores an atlas crystal entity unless it is already registered as a loaded crystal.
     * Orphan entities (unknown faction) are removed. No-op for non-atlas ender crystals.
     */
    public static void restoreIfNeeded(EnderCrystal entity) {
        if (!entity.getPersistentDataContainer().has(getKeyFaction(), PersistentDataType.STRING)) return;
        AtlasCrystal existing = crystals.get(entity.getUniqueId());
        if (existing == null || !existing.isLoaded()) restore(entity);
    }

    /**
     * Restores the atlas crystals whose entities were already loaded when the plugin enabled
     * (force-loaded chunks): their EntitiesLoadEvent fired before the listener was registered.
     * Call once in onEnable, after the crystal data is restored.
     *
     * @return number of crystal entities checked
     */
    public static int restoreLoadedEntities() {
        int checked = 0;
        for (World world : Bukkit.getWorlds()) {
            for (EnderCrystal entity : world.getEntitiesByClass(EnderCrystal.class)) {
                restoreIfNeeded(entity);
                checked++;
            }
        }
        return checked;
    }

    /** Restores an atlas crystal from PDC when its chunk is loaded. */
    public static AtlasCrystal restore(EnderCrystal entity) {
        UUID uuid = entity.getUniqueId();
        String factionName = entity.getPersistentDataContainer().get(getKeyFaction(), PersistentDataType.STRING);
        if (factionName == null) return null;

        // Orphan entity: its faction was disbanded but the entity survived (e.g. crash before
        // the world saved). Remove it and its nametag instead of letting it reclaim chunks.
        if (FactionManager.getFaction(factionName) == null) {
            String displayUUIDStr = entity.getPersistentDataContainer().get(getKeyNametagDisplay(), PersistentDataType.STRING);
            if (displayUUIDStr != null) {
                try {
                    Entity display = Bukkit.getEntity(UUID.fromString(displayUUIDStr));
                    if (display != null) display.remove();
                } catch (IllegalArgumentException ignored) {}
            }
            Atlas.instance.getLogger().warning("Removed orphan crystal " + uuid + " of unknown faction '"
                    + factionName + "' at " + entity.getLocation().toVector());
            entity.remove();
            return null;
        }

        Double savedHp    = entity.getPersistentDataContainer().get(getKeyHp(), PersistentDataType.DOUBLE);
        Double savedMaxHp = entity.getPersistentDataContainer().get(getKeyMaxHp(), PersistentDataType.DOUBLE);
        double hp    = savedHp    != null ? savedHp    : AtlasCrystal.BASE_MAX_HP;
        double maxHp = savedMaxHp != null ? savedMaxHp : AtlasCrystal.BASE_MAX_HP;

        String savedName = entity.getPersistentDataContainer().get(getKeyName(), PersistentDataType.STRING);
        if (savedName == null) savedName = "";

        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, hp, maxHp);
        crystal.setName(savedName);
        entity.setCustomNameVisible(false);

        // A stub was created at startup from storage: copy its bookkeeping data forward.
        // Upgrade stats from storage are applied first; the entity PDC (below) overrides them
        // when present. Without a stub the crystal has no stored data (PDC only).
        AtlasCrystal stub = crystals.get(uuid);
        if (stub != null && stub != crystal) {
            if (stub.getHome() != null) crystal.setHome(stub.getHome());
            if (stub.isOutpost()) crystal.setOutpost(true);
            crystal.getClaimedChunks().addAll(stub.getClaimedChunks());
            crystal.getPurchasedChestSizes().addAll(stub.getPurchasedChestSizes());
            for (Map.Entry<Integer, ItemStack[]> e : stub.getChestContentsMap().entrySet()) {
                crystal.setChestContents(e.getKey(), e.getValue());
            }
            for (Long d : stub.getPurchasedProtections()) crystal.addPurchasedProtection(d);
            for (Map.Entry<Long, Long> e : stub.getBrokenProtections().entrySet()) {
                crystal.breakProtection(e.getKey(), e.getValue());
            }
            crystal.restoreHpBonus(stub.getHpBonus());
            crystal.restoreRegenBonus(stub.getRegenBonus());
            crystal.restoreClaimCapacity(stub.getClaimCapacity());
            crystal.restoreSpentSkillPoints(stub.getSpentSkillPoints());
            crystal.setImmuneUntilMillis(stub.getImmuneUntilMillis());
        }

        // Migration from very old data: home stored on entity PDC.
        if (crystal.getHome() == null) {
            String legacyHome = entity.getPersistentDataContainer().get(getKeyHome(), PersistentDataType.STRING);
            if (legacyHome != null) {
                Location home = decodeHome(legacyHome);
                if (home != null) crystal.setHome(home);
            }
        }

        // Sync FactionClaimManager runtime cache (idempotent).
        for (String chunkKey : crystal.getClaimedChunks()) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                try {
                    FactionClaimManager.claimChunk(factionName, parts[0],
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Restore scalar PDC fields (maxHp already loaded correctly, use restore methods)
        var pdc = entity.getPersistentDataContainer();
        Double hpBonus        = pdc.get(getKeyHpBonus(),      PersistentDataType.DOUBLE);
        Double regenBonus     = pdc.get(getKeyRegenBonus(),   PersistentDataType.DOUBLE);
        Integer savedClaimCap = pdc.get(getKeyClaimCapacity(),PersistentDataType.INTEGER);
        Integer savedSpentSp  = pdc.get(getKeySpentSp(),      PersistentDataType.INTEGER);
        Long savedImmune      = pdc.get(getKeyImmuneUntil(),  PersistentDataType.LONG);

        if (hpBonus           != null) crystal.restoreHpBonus(hpBonus);
        if (regenBonus        != null) crystal.restoreRegenBonus(regenBonus);
        if (savedClaimCap     != null) crystal.restoreClaimCapacity(savedClaimCap);
        if (savedSpentSp      != null) crystal.restoreSpentSkillPoints(savedSpentSp);
        if (savedImmune       != null) crystal.setImmuneUntilMillis(savedImmune);

        // Protections: prefer the new long-array PDC; fall back to legacy single-sum (one entry).
        long[] pdcProtections = pdc.get(getKeyProtections(), PersistentDataType.LONG_ARRAY);
        if (pdcProtections != null && pdcProtections.length > 0) {
            for (long d : pdcProtections) crystal.addPurchasedProtection(d);
        } else {
            Long legacyProtMs = pdc.get(getKeyProtectionMs(), PersistentDataType.LONG);
            if (legacyProtMs != null && legacyProtMs > 0 && crystal.getPurchasedProtections().isEmpty()) {
                // One-time migration: treat the old summed value as a single bonus tier.
                crystal.addPurchasedProtection(legacyProtMs);
            }
        }
        long[] brokenDur = pdc.get(getKeyBrokenDurations(), PersistentDataType.LONG_ARRAY);
        long[] brokenAt  = pdc.get(getKeyBrokenAtMs(),      PersistentDataType.LONG_ARRAY);
        if (brokenDur != null && brokenAt != null && brokenDur.length == brokenAt.length) {
            for (int idx = 0; idx < brokenDur.length; idx++) {
                crystal.breakProtection(brokenDur[idx], brokenAt[idx]);
            }
        }

        // Restore nametag display UUID
        String displayUUIDStr = entity.getPersistentDataContainer().get(getKeyNametagDisplay(), PersistentDataType.STRING);
        if (displayUUIDStr != null) {
            try { crystal.setTextDisplayUUID(UUID.fromString(displayUUIDStr)); }
            catch (IllegalArgumentException ignored) {}
        }

        crystals.put(uuid, crystal);
        // Always replace any existing stub in factionCrystals so the live entity-backed
        // crystal supersedes it.
        factionCrystals.computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                .put(uuid, crystal);
        crystal.updateNametag();
        return crystal;
    }

    // ── Skill purchases ───────────────────────────────────────────────────────

    public static boolean purchaseHpUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.HpTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addHpBonus(tier.bonus());
        crystal.addRegenBonus(tier.regen());
        crystal.addSpentSkillPoints(tier.cost());
        crystal.setHp(Math.min(crystal.getHp() + tier.bonus(), crystal.getMaxHp()));
        persistCrystalState(crystal);
        crystal.updateNametag();
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    public static boolean purchaseClaimUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ClaimTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addClaimCapacity(tier.amount());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    public static boolean purchaseChestUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ChestTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addPurchasedChest(tier.size());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    public static boolean purchaseProtectionUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ProtectionTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        // Each protection tier can only be purchased once per crystal.
        if (crystal.hasPurchasedProtection(tier.durationMs())) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        if (!crystal.addPurchasedProtection(tier.durationMs())) {
            // Race-safety: refund if the duplicate guard above missed.
            faction.addSkillPoints(tier.cost());
            return false;
        }
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    /**
     * Purchases a single homes-skill tier for a faction. Each tier index is a one-shot
     * purchase that grants {@code tier.amount()} extra home slots to every faction member.
     * Cost is attributed to the crystal that performed the purchase, mirroring the outpost
     * skill — destroying that crystal refunds the cost as a level downgrade.
     */
    public static boolean purchaseHomeUpgrade(String factionName, UUID crystalUUID,
                                              FactionLevelManager.HomeTier tier, int tierIndex) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (faction.hasPurchasedHomeTier(tierIndex)) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        if (!faction.addPurchasedHomeTier(tierIndex)) {
            faction.addSkillPoints(tier.cost());
            return false;
        }
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    /**
     * Purchases a single vault-skill tier for a faction. Each tier index is a one-shot
     * purchase that resizes the faction's ruby-only shared vault to {@code tier.size()}.
     * Cost is attributed to the crystal that performed the purchase, mirroring the homes/outpost
     * skills — destroying that crystal refunds the cost as a level downgrade.
     */
    public static boolean purchaseVaultUpgrade(String factionName, UUID crystalUUID,
                                               FactionLevelManager.VaultTier tier, int tierIndex) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (faction.hasPurchasedVaultTier(tierIndex)) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        if (!faction.addPurchasedVaultTier(tierIndex)) {
            faction.addSkillPoints(tier.cost());
            return false;
        }
        // Expand to the tier's size only if it actually grows the vault. Tiers may be bought
        // out of order (a player who somehow buys tier 4 first should not shrink back to tier 3).
        if (tier.size() > faction.getVaultSize()) faction.setVaultSize(tier.size());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    /**
     * Purchases the outpost skill for a faction (one-time, faction-level unlock).
     * The cost is attributed to the crystal that performed the purchase so it counts
     * toward {@link AtlasCrystal#getSpentSkillPoints()} — destroying that crystal will
     * therefore refund the cost as a level downgrade in {@link #destroyCrystal(UUID)}.
     */
    public static boolean purchaseOutpostUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.OutpostTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null || faction.isOutpostUnlocked()) return false;
        int cost = tier.cost();
        if (!faction.spendSkillPoints(cost)) return false;
        crystal.addSpentSkillPoints(cost);
        faction.setOutpostUnlocked(true);
        persistCrystalState(crystal);
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    // ── Crystal destruction with full consequences ────────────────────────────

    /**
     * Permanently destroys a crystal and applies all consequences of the faction:
     * - Remove its claimed chunks
     * - Drop its chest contents at the crystal location
     * - Lower faction level proportionally to spentSkillPoints
     * - If the main crystal died and an outpost remains, promote the outpost to main
     *   (the surviving crystal's home becomes the faction's first home automatically
     *   via {@link #getFirstHome(String)})
     * - If no crystals remain, disband the faction
     */
    public static void destroyCrystal(UUID crystalUUID) {
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (crystal == null) return;

        String factionName = crystal.getFactionName();
        Faction faction = FactionManager.getFaction(factionName);

        // Capture the drop location BEFORE any state mutation. Prefer the entity's live
        // location; fall back to the crystal's home (e.g. when destroying a stub).
        Location dropLoc = null;
        if (crystal.getEntity() != null) {
            dropLoc = crystal.getEntity().getLocation();
        }
        if (dropLoc == null || dropLoc.getWorld() == null) dropLoc = crystal.getHome();

        // 1. Remove ONLY this crystal's claims; other crystals of the same faction are untouched.
        FactionClaimManager.removeClaimsForCrystal(new ArrayList<>(crystal.getClaimedChunks()));
        crystal.getClaimedChunks().clear();

        // 2. Drop this crystal's chest contents at the captured location.
        if (dropLoc != null && dropLoc.getWorld() != null) {
            for (Map.Entry<Integer, ItemStack[]> entry : crystal.getChestContentsMap().entrySet()) {
                if (entry.getValue() == null) continue;
                for (ItemStack stack : entry.getValue()) {
                    if (stack != null && stack.getType() != Material.AIR) {
                        dropLoc.getWorld().dropItemNaturally(dropLoc, stack);
                    }
                }
            }
        }
        crystal.getChestContentsMap().clear();
        crystal.getPurchasedChestSizes().clear();

        // 3. Lower faction level by spent skill points / skillPointsPerLevel.
        //    Main crystal: its spentSkillPoints already includes the outpost-unlock cost
        //    (see {@link #purchaseOutpostUpgrade}, which attributes the cost to the
        //    purchasing crystal — typically the main).
        //    Outpost crystal: its spentSkillPoints contains only its own per-crystal
        //    upgrades, so the outpost-unlock cost is added on top so destroying the
        //    outpost crystal also refunds that faction-level cost as level loss.
        boolean levelChanged = false;
        if (faction != null) {
            int totalSpent = crystal.getSpentSkillPoints();
            if (crystal.isOutpost()) {
                FactionLevelManager.OutpostTier outpostTier = FactionLevelManager.getOutpostTier();
                if (outpostTier != null) totalSpent += outpostTier.cost();
            }
            if (totalSpent > 0) {
                int sp = FactionLevelManager.getSkillPointsPerLevel();
                int levelsToLose = (sp > 0) ? totalSpent / sp : 0;
                if (levelsToLose > 0) {
                    int newLevel = Math.max(0, faction.getLevel() - levelsToLose);
                    faction.setLevel(newLevel);
                    faction.setExp(0);
                    levelChanged = true;
                }
            }
        }

        // 4. Remove crystal from tracking
        boolean wasMain = !crystal.isOutpost();
        remove(crystalUUID);

        // 5. Disband if no crystals remain. Otherwise, the outpost-unlock is re-locked
        //    so the faction can re-purchase it from the skill GUI:
        //    - Main died: the surviving outpost is promoted to main (it becomes the new
        //      disband-on-loss anchor and getFirstHome() picks its home as faction home).
        //    - Outpost died: the main remains, but the unlock is reset so the player can
        //      buy the outpost skill again and place a fresh outpost crystal.
        Map<UUID, AtlasCrystal> remaining = factionCrystals.get(factionName);
        if (remaining == null || remaining.isEmpty()) {
            // The faction is about to be disbanded — scatter every remaining vault gem at the
            // captured location. Drop happens BEFORE disbandFaction so the faction object is
            // still live to read vault contents from; the explosion already fired upstream so
            // items spawned now survive.
            if (faction != null && dropLoc != null && dropLoc.getWorld() != null) {
                org.minecraft.atlas.faction.FactionVault.dropAll(faction, dropLoc);
            }
            FactionManager.disbandFaction(factionName);
        } else {
            if (wasMain) {
                AtlasCrystal successor = remaining.values().iterator().next();
                successor.setOutpost(false);
            }
            if (faction != null) faction.setOutpostUnlocked(false);
        }

        // 6. Refresh surviving crystals' nametags — the level text reflects the faction
        //    level, which may have just been downgraded by the spent-skills refund.
        if (levelChanged && remaining != null) {
            for (AtlasCrystal c : remaining.values()) c.updateNametag();
        }

        // 7. Persist
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
    }

    // ── Claim helpers ─────────────────────────────────────────────────────────

    /**
     * Finds the best crystal in a faction to absorb a new claim.
     * Returns the nearest crystal (in chunk distance) that has free capacity.
     */
    public static AtlasCrystal findCrystalForClaim(String factionName, String worldName, int cx, int cz) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        AtlasCrystal best = null;
        double bestDist = Double.MAX_VALUE;
        for (AtlasCrystal c : map.values()) {
            if (c.getEntity() == null) continue; // can't claim around unloaded crystal
            if (!c.getEntity().getWorld().getName().equals(worldName)) continue;
            if (c.getClaimedChunks().size() >= c.getClaimCapacity()) continue;
            int ccx = c.getEntity().getLocation().getBlockX() >> 4;
            int ccz = c.getEntity().getLocation().getBlockZ() >> 4;
            double dist = Math.hypot(ccx - cx, ccz - cz);
            if (dist < bestDist) { bestDist = dist; best = c; }
        }
        return best;
    }

    public static int getTotalClaimCapacity(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return 0;
        return map.values().stream().mapToInt(AtlasCrystal::getClaimCapacity).sum();
    }

    public static int getTotalClaimedChunks(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return 0;
        return map.values().stream().mapToInt(c -> c.getClaimedChunks().size()).sum();
    }

    // ── Nametag display (TextDisplay) ─────────────────────────────────────────

    /**
     * Spawns or updates the TextDisplay entity used as this crystal's overhead nametag.
     */
    public static void spawnOrUpdateNametagDisplay(AtlasCrystal crystal) {
        Location loc = crystal.getEntity().getLocation().add(0, 2.5, 0);
        TextDisplay display = null;
        UUID existingUUID = crystal.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }
        if (display == null) {
            if (!loc.isChunkLoaded()) return;
            display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
            });
            crystal.setTextDisplayUUID(display.getUniqueId());
            crystal.getEntity().getPersistentDataContainer()
                    .set(getKeyNametagDisplay(), PersistentDataType.STRING, display.getUniqueId().toString());
        }
        Component text = crystal.buildNametagComponent();
        display.text(text);
    }

    /** Removes the TextDisplay nametag entity for a crystal, if present. */
    private static void removeNametagDisplay(AtlasCrystal crystal) {
        UUID displayUUID = crystal.getTextDisplayUUID();
        if (displayUUID == null) return;
        Entity e = Bukkit.getEntity(displayUUID);
        if (e != null) e.remove();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static AtlasCrystal getCrystal(UUID entityUUID) { return crystals.get(entityUUID); }
    public static boolean isAtlasCrystal(UUID entityUUID)  { return crystals.containsKey(entityUUID); }

    /** Returns true if this faction has any crystal with the given display name. */
    public static boolean hasCrystalWithName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;
        return map.values().stream().anyMatch(c -> crystalName.equals(c.getName()));
    }

    /** Returns the first crystal in this faction with the given display name, or null. */
    public static AtlasCrystal getCrystalByName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        return map.values().stream().filter(c -> crystalName.equals(c.getName())).findFirst().orElse(null);
    }

    /** Returns all named crystals for a faction in insertion order. */
    public static Collection<AtlasCrystal> getFactionCrystals(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        return map == null ? Collections.emptyList() : Collections.unmodifiableCollection(map.values());
    }

    /**
     * Promotes the given crystal to "main" status (and demotes the previous main to outpost).
     * Reorders the per-faction crystal map so the new main is iterated first, which is what
     * {@link #getFirstHome(String)} returns for {@code /faction home} with no argument.
     * Returns false if the crystal isn't found, has no faction map, or is already the main.
     */
    public static boolean setAsMainCrystal(UUID crystalUUID) {
        AtlasCrystal target = crystals.get(crystalUUID);
        if (target == null || !target.isOutpost()) return false;
        String factionName = target.getFactionName();
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null || !map.containsKey(crystalUUID)) return false;

        // Demote any current main(s) and promote the target.
        for (AtlasCrystal c : map.values()) {
            if (!c.isOutpost()) c.setOutpost(true);
        }
        target.setOutpost(false);

        // LinkedHashMap iteration order drives getFirstHome — put target first.
        LinkedHashMap<UUID, AtlasCrystal> reordered = new LinkedHashMap<>();
        reordered.put(crystalUUID, target);
        for (Map.Entry<UUID, AtlasCrystal> e : map.entrySet()) {
            if (!e.getKey().equals(crystalUUID)) reordered.put(e.getKey(), e.getValue());
        }
        factionCrystals.put(factionName, reordered);

        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
        return true;
    }

    /** Returns the home of the first crystal with a home set for this faction, or null if none. */
    public static Location getFirstHome(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        for (AtlasCrystal c : map.values()) {
            if (c.getHome() != null) return c.getHome();
        }
        return null;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Updates the faction name stored on every crystal that belongs to {@code oldName}.
     */
    public static void renameFactionCrystals(String oldName, String newName) {
        for (AtlasCrystal crystal : crystals.values()) {
            if (crystal.getFactionName().equals(oldName)) {
                crystal.setFactionName(newName);
                if (crystal.getEntity() != null) {
                    crystal.getEntity().getPersistentDataContainer()
                            .set(getKeyFaction(), PersistentDataType.STRING, newName);
                }
                crystal.updateNametag();
            }
        }
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(oldName);
        if (map != null) factionCrystals.put(newName, map);
    }

    /**
     * Renames a crystal within a faction.
     * Returns false if no crystal with oldName is found in this faction.
     */
    public static boolean renameCrystal(String factionName, String oldName, String newName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;
        AtlasCrystal found = null;
        for (AtlasCrystal c : map.values()) if (oldName.equals(c.getName())) { found = c; break; }
        if (found == null) return false;
        found.setName(newName);
        if (found.getEntity() != null)
            found.getEntity().getPersistentDataContainer().set(getKeyName(), PersistentDataType.STRING, newName);
        found.updateNametag();
        return true;
    }

    /** Removes crystal from tracking WITHOUT applying destruction consequences. */
    public static void remove(UUID entityUUID) {
        AtlasCrystal crystal = crystals.remove(entityUUID);
        if (crystal == null) return;
        removeNametagDisplay(crystal);
        Map<UUID, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
        if (map != null) map.remove(entityUUID);
    }

    /** Removes and despawns all crystals belonging to a faction. Called when a faction is disbanded. */
    public static void removeAllForFaction(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(factionName);
        if (map == null) return;
        for (AtlasCrystal crystal : map.values()) {
            crystals.remove(crystal.getEntityUUID());
            removeNametagDisplay(crystal);
            if (crystal.getEntity() != null && !crystal.getEntity().isDead()) crystal.getEntity().remove();
        }
    }

    // ── Regen scheduler ───────────────────────────────────────────────────────

    /** Starts the 1-second regen ticker. Call once from Atlas.onEnable. */
    public static void schedule(Plugin plugin) {
        new BukkitRunnable() {
            @Override public void run() {
                long nowMs = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, AtlasCrystal>> iter = crystals.entrySet().iterator();
                while (iter.hasNext()) {
                    AtlasCrystal crystal = iter.next().getValue();

                    // Protection regen check — sequential. Only the SHORTEST broken protection
                    // is currently regenerating at any moment; longer ones queue behind it.
                    // Each head protection has a watch window of 2 × duration after its
                    // brokenAt (immunity duration + regen duration). If the crystal takes
                    // damage during the regen portion, the head is permanently lost; otherwise
                    // it regenerates. When the head resolves, the next-shortest broken
                    // protection takes over: its brokenAt is shifted to (now − itsDuration)
                    // so its own regen window starts immediately (no second immunity period),
                    // and the crystal's last-attack timestamp is cleared so prior damage
                    // doesn't count against the new head.
                    boolean protectionsChanged = false;
                    Map<Long, Long> broken = crystal.getBrokenProtections();
                    if (!broken.isEmpty()) {
                        Long head = null;
                        for (Long d : broken.keySet()) {
                            if (head == null || d < head) head = d;
                        }
                        long brokenAt = broken.get(head);
                        long regenAt  = brokenAt + 2 * head;
                        if (nowMs >= regenAt) {
                            if (crystal.getLastAttackMillis() > brokenAt) {
                                broken.remove(head);
                                crystal.getPurchasedProtections().remove(head);
                            } else {
                                broken.remove(head);
                            }
                            protectionsChanged = true;
                            Long next = null;
                            for (Long d : broken.keySet()) {
                                if (next == null || d < next) next = d;
                            }
                            if (next != null) {
                                broken.put(next, nowMs - next);
                                crystal.clearLastAttack();
                            }
                        }
                    }

                    // Detect immunity expiry so the nametag can flip from ACTIVE to RELOADING
                    // (or clear) without waiting for an HP-regen tick to refresh it.
                    boolean immunityJustExpired = false;
                    if (crystal.getImmuneUntilMillis() > 0 && nowMs >= crystal.getImmuneUntilMillis()) {
                        crystal.setImmuneUntilMillis(0);
                        immunityJustExpired = true;
                    }

                    EnderCrystal entity = crystal.getEntity();
                    if (entity == null) {
                        // Stub: protection changes are persisted by the next storage save/autosave.
                        continue;
                    }
                    if (entity.isDead()) {
                        // Entity invalidated (chunk unloaded, or removed by some other path).
                        // Keep the crystal in the maps as a stub so its data stays queryable.
                        crystal.detachEntity();
                        continue;
                    }
                    if (crystal.canRegen() && crystal.getHp() < crystal.getMaxHp()) {
                        crystal.regen(regenPerSecond + crystal.getRegenBonus());
                        entity.getPersistentDataContainer()
                                .set(getKeyHp(), PersistentDataType.DOUBLE, crystal.getHp());
                    }
                    if (protectionsChanged || immunityJustExpired) {
                        crystal.updateNametag();
                        persistCrystalState(crystal);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Location decodeHome(String encoded) {
        String[] parts = encoded.split(",", 6);
        if (parts.length != 6) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) { return null; }
    }
}
