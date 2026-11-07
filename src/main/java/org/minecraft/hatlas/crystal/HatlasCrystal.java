package org.minecraft.hatlas.crystal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.*;

/**
 * Represents an hatlas ender crystal entity that guards a faction.
 * HP is managed independently of vanilla ender crystal mechanics.
 * Skill-point upgrades (hp, claims, chests, protection) are stored per-crystal.
 */
public class HatlasCrystal {

    static final double BASE_MAX_HP = 50.0;

    private EnderCrystal entity;
    private UUID cachedEntityUUID;
    private String factionName;
    private String name;
    private Location home;
    private double hp;
    private double maxHp;
    /** System.currentTimeMillis() after which immunity ends. 0 = not immune. */
    private long immuneUntilMillis = 0;
    /** System.currentTimeMillis() of the last hit by an outside player. 0 = never attacked. */
    private long lastAttackMillis = 0;
    /** UUID of the TextDisplay entity used as this crystal's overhead nametag. */
    private UUID textDisplayUUID = null;

    // ── Skill-point-driven upgrades ───────────────────────────────────────────
    private double hpBonus            = 0;
    private double regenBonus         = 0;
    private int claimCapacity      = 1;
    private final LinkedHashSet<String>  claimedChunks       = new LinkedHashSet<>();
    private final List<Integer>          purchasedChestSizes = new ArrayList<>();
    private final Map<Integer, ItemStack[]> chestContents    = new HashMap<>();
    private int spentSkillPoints      = 0;
    /**
     * Durations (ms) of every protection upgrade the crystal owns.
     * Each entry is a unique tier — a tier with the same duration cannot be purchased twice.
     */
    private final List<Long> purchasedProtections = new ArrayList<>();
    /**
     * Protections that have been consumed (broken) but are not yet permanently lost.
     * Maps duration → epoch-ms when the protection broke. They regenerate after their
     * own duration if no damage occurs in the watch window, otherwise they are lost.
     */
    private final Map<Long, Long> brokenProtections = new HashMap<>();
    /** True when this crystal was placed as an outpost (not the faction's founding crystal). */
    private boolean outpost = false;

    public HatlasCrystal(EnderCrystal entity, String factionName, double hp, double maxHp) {
        this.entity            = entity;
        this.cachedEntityUUID  = entity.getUniqueId();
        this.factionName       = factionName;
        this.name              = "";
        this.home              = null;
        this.hp                = hp;
        this.maxHp             = maxHp;
    }

    /** Stub constructor — used when the backing entity chunk is not yet loaded. */
    HatlasCrystal(UUID entityUUID, String factionName, double hp, double maxHp) {
        this.entity           = null;
        this.cachedEntityUUID = entityUUID;
        this.factionName      = factionName;
        this.name             = "";
        this.home             = null;
        this.hp               = hp;
        this.maxHp            = maxHp;
    }

    // ── Basic getters/setters ─────────────────────────────────────────────────
    public boolean isOutpost()                { return outpost; }
    public void    setOutpost(boolean b)      { this.outpost = b; }
    public EnderCrystal getEntity()           { return entity; }
    /** UUID of the backing entity — valid even when the entity's chunk is unloaded. */
    public UUID getEntityUUID()               { return cachedEntityUUID; }
    /** True when the backing entity is present and valid in a loaded chunk. */
    public boolean isLoaded()                 { return entity != null && entity.isValid(); }
    /** Drops the entity reference; the crystal becomes a stub until the chunk reloads. */
    public void detachEntity()                { this.entity = null; }
    public String getFactionName()            { return factionName; }
    public void setFactionName(String n)      { this.factionName = n; }
    public String getName()                   { return name; }
    public void setName(String n)             { this.name = n; }
    public Location getHome()                 { return home; }
    public void setHome(Location home)        { this.home = home; }
    public double getHp()                     { return hp; }
    public double getMaxHp()                  { return maxHp; }
    public long getImmuneUntilMillis()        { return immuneUntilMillis; }
    public void setImmuneUntilMillis(long ts) { this.immuneUntilMillis = ts; }
    public UUID getTextDisplayUUID()          { return textDisplayUUID; }
    public void setTextDisplayUUID(UUID id)   { this.textDisplayUUID = id; }

    // ── Skill upgrade fields ──────────────────────────────────────────────────
    public double getHpBonus()                            { return hpBonus; }
    public double getRegenBonus()                         { return regenBonus; }
    public int    getClaimCapacity()                      { return claimCapacity; }
    public LinkedHashSet<String> getClaimedChunks()       { return claimedChunks; }
    public List<Integer> getPurchasedChestSizes()         { return purchasedChestSizes; }
    public Map<Integer, ItemStack[]> getChestContentsMap(){ return chestContents; }
    public int  getSpentSkillPoints()                     { return spentSkillPoints; }
    public long getLastAttackMillis()                     { return lastAttackMillis; }

    /** All purchased protection durations (in ms), regardless of broken/available state. */
    public List<Long> getPurchasedProtections()           { return purchasedProtections; }
    /** Map of duration → broken-at timestamp for protections currently consumed. */
    public Map<Long, Long> getBrokenProtections()         { return brokenProtections; }
    /** Returns true if this crystal already owns a protection of exactly this duration. */
    public boolean hasPurchasedProtection(long durationMs) { return purchasedProtections.contains(durationMs); }
    /** Returns true if the protection of {@code durationMs} is currently broken (not available). */
    public boolean isProtectionBroken(long durationMs)    { return brokenProtections.containsKey(durationMs); }

    /**
     * Returns the LONGEST currently-available protection duration, or {@code null}
     * if every owned protection is currently broken (or none are owned).
     * Longest is consumed first so the strongest shield absorbs the first defeat.
     */
    public Long getLongestAvailableProtection() {
        Long best = null;
        for (Long d : purchasedProtections) {
            if (brokenProtections.containsKey(d)) continue;
            if (best == null || d > best) best = d;
        }
        return best;
    }

    /** Marks the given protection as broken at {@code nowMs}. */
    public void breakProtection(long durationMs, long nowMs) {
        brokenProtections.put(durationMs, nowMs);
    }

    /** Restores a broken protection to the available pool. */
    public void regenerateProtection(long durationMs) {
        brokenProtections.remove(durationMs);
    }

    /** Permanently removes a protection (it neither defends nor is owned anymore). */
    public void loseProtection(long durationMs) {
        brokenProtections.remove(durationMs);
        purchasedProtections.remove((Long) durationMs);
    }

    public void addHpBonus(double bonus) {
        this.hpBonus += bonus;
        this.maxHp   += bonus;
    }
    public void addRegenBonus(double regen)  { this.regenBonus += regen; }
    public void addClaimCapacity(int amount) { this.claimCapacity = Math.max(1, claimCapacity + amount); }
    public void addPurchasedChest(int size)  { purchasedChestSizes.add(size); }

    /**
     * Adds a protection tier the crystal didn't already own.
     * Returns false if a protection with the same duration is already owned.
     */
    public boolean addPurchasedProtection(long durationMs) {
        if (purchasedProtections.contains(durationMs)) return false;
        purchasedProtections.add(durationMs);
        return true;
    }
    public void addSpentSkillPoints(int cost)    { this.spentSkillPoints += cost; }

    // Restore-only setters — do NOT modify maxHp (it was already persisted correctly)
    void restoreHpBonus(double bonus)          { this.hpBonus = bonus; }
    void restoreRegenBonus(double regen)       { this.regenBonus = regen; }
    void restoreClaimCapacity(int cap)         { this.claimCapacity = cap; }
    void restoreSpentSkillPoints(int sp)       { this.spentSkillPoints = sp; }

    /** Returns the contents of crystal chest at {@code index}, or an empty array of the right size. */
    public ItemStack[] getChestContents(int index) {
        int size = index < purchasedChestSizes.size() ? purchasedChestSizes.get(index) : 27;
        return chestContents.getOrDefault(index, new ItemStack[size]);
    }
    public void setChestContents(int index, ItemStack[] contents) { chestContents.put(index, contents); }

    // ── HP mechanics ──────────────────────────────────────────────────────────

    /** Directly sets HP (clamped to [0, maxHp]). Does NOT record an attack timestamp. */
    public void setHp(double hp) {
        this.hp = Math.clamp(hp, 0, maxHp);
        updateNametag();
    }

    /** Returns true if this crystal cannot be damaged right now. */
    public boolean isImmune() {
        return immuneUntilMillis > 0 && System.currentTimeMillis() < immuneUntilMillis;
    }

    /** Grants immunity for the specified number of milliseconds from now. */
    public void setImmuneFor(long durationMs) {
        this.immuneUntilMillis = System.currentTimeMillis() + durationMs;
    }

    /**
     * Reduces HP by the given amount.
     * Returns true if HP reached 0.
     * Returns false without applying damage if the crystal is currently immune.
     */
    public boolean damage(double amount) {
        if (isImmune()) return false;
        hp = Math.max(0, hp - amount);
        lastAttackMillis = System.currentTimeMillis();
        updateNametag();
        return hp <= 0;
    }

    /** Regenerates HP by the given amount, capped at maxHp. */
    public void regen(double amount) {
        hp = Math.min(maxHp, hp + amount);
        updateNametag();
    }

    /** Clears the last-attack timestamp. Used when a queued protection takes over the
     * sequential regen slot, so that pre-handoff damage doesn't count against it. */
    public void clearLastAttack() { this.lastAttackMillis = 0; }

    /** Returns true if enough time has elapsed since the last outside-player attack. */
    public boolean canRegen() {
        if (lastAttackMillis == 0) return true;
        return System.currentTimeMillis() - lastAttackMillis >= HatlasCrystalManager.regenTimeoutMs;
    }

    // ── Nametag ───────────────────────────────────────────────────────────────

    /** Builds the 3-line nametag Component without touching any entity. */
    Component buildNametagComponent() {
        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
        int level = faction != null ? faction.getLevel() : 0;

        Component line1;
        if (name != null && !name.isEmpty()) {
            line1 = Component.text(name + " ", NamedTextColor.WHITE)
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(factionName, color))
                    .append(Component.text("]", NamedTextColor.GRAY));
        } else {
            line1 = Component.text("[", NamedTextColor.GRAY)
                    .append(Component.text(factionName, color))
                    .append(Component.text("]", NamedTextColor.GRAY));
        }

        Component line2 = Component.text("LvL.", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW));

        Component line3 = Component.text((int) hp + "/" + (int) maxHp + " ♥", NamedTextColor.RED);

        Component result = line1.append(Component.newline())
                .append(line2).append(Component.newline())
                .append(line3);

        // Immunity state shown on its own line below the HP heart while at least one
        // protection is broken. Format is "[IMMUNITY - <ready>/<total>]" — ready is the
        // number of protections still available, total is the count owned. Color signals
        // the phase: aqua during the active immunity window, yellow while reloading.
        // The line disappears once every queued reload has completed.
        if (!brokenProtections.isEmpty()) {
            int total = purchasedProtections.size();
            int ready = total - brokenProtections.size();
            NamedTextColor immunityColor = isImmune() ? NamedTextColor.AQUA : NamedTextColor.YELLOW;
            result = result.append(Component.newline())
                    .append(Component.text("IMMUNITY - [" + ready + "/" + total + "]", immunityColor));
        }

        return result;
    }

    /** Refreshes the overhead TextDisplay nametag. Creates it if it doesn't exist yet. */
    public void updateNametag() {
        if (entity == null || entity.isDead()) return;
        HatlasCrystalManager.spawnOrUpdateNametagDisplay(this);
    }
}
