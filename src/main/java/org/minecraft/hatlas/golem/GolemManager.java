package org.minecraft.hatlas.golem;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.IronGolem;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GolemManager {

    // Lazy-init so Territory.instance is guaranteed non-null when first accessed
    private static NamespacedKey KEY_TYPE;
    private static NamespacedKey KEY_FACTION;

    /** Golem UUID → timestamp (ms) of the last detection alert sent. */
    private static final Map<UUID, Long> alertCooldowns = new HashMap<>();

    private static final long ALERT_COOLDOWN_MS = 30_000; // one alert per golem per 30 s

    // -------------------------------------------------------------------------

    private static NamespacedKey keyType() {
        if (KEY_TYPE == null) KEY_TYPE = new NamespacedKey(Hatlas.instance, "golem_type");

        return KEY_TYPE;
    }

    private static NamespacedKey keyFaction() {
        if (KEY_FACTION == null) KEY_FACTION = new NamespacedKey(Hatlas.instance, "golem_faction");

        return KEY_FACTION;
    }

    // -------------------------------------------------------------------------
    // Initialise a freshly spawned IronGolem entity
    // -------------------------------------------------------------------------

    public static void init(IronGolem golem, GolemType type, String factionName) {
        // Tag
        golem.getPersistentDataContainer().set(keyType(),  PersistentDataType.STRING, type.name());
        golem.getPersistentDataContainer().set(keyFaction(), PersistentDataType.STRING, factionName);

        // Max health
        AttributeInstance health = golem.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(type.getMaxHealth());
            golem.setHealth(type.getMaxHealth());
        }

        // Attack damage
        AttributeInstance dmg = golem.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(type.getAttackDamage());

        // Movement speed (only for Crying Obsidian which has 0.75× multiplier)
        if (type.getSpeedMultiplier() != 1.0) {
            AttributeInstance spd = golem.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(spd.getBaseValue() * type.getSpeedMultiplier());
        }

        // Colored name tag — visible overhead, distinguishes type at a glance
        golem.customName(Component.text(type.getDisplayName() + " Golem", type.getColor()));
        golem.setCustomNameVisible(true);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public static boolean isTagged(IronGolem golem) {
        return golem.getPersistentDataContainer().has(keyType(), PersistentDataType.STRING);
    }

    public static GolemType getType(IronGolem golem) {
        String name = golem.getPersistentDataContainer().get(keyType(), PersistentDataType.STRING);
        if (name == null) return null;
        try { return GolemType.valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }

    public static String getFaction(IronGolem golem) {
        return golem.getPersistentDataContainer().get(keyFaction(), PersistentDataType.STRING);
    }

    /**
     * Re-applies the colored name tag from stored PDC data.
     * Called when a golem entity is loaded from disk after a server restart.
     */
    public static void restoreNameTag(IronGolem golem) {
        GolemType type = getType(golem);
        if (type == null) return;
        golem.customName(Component.text(type.getDisplayName() + " Golem", type.getColor()));
        golem.setCustomNameVisible(true);
    }

    // -------------------------------------------------------------------------
    // Detection alert throttle
    // -------------------------------------------------------------------------

    public static boolean isAlertOnCooldown(UUID golemUUID) {
        Long last = alertCooldowns.get(golemUUID);

        return last != null && (System.currentTimeMillis() - last) < ALERT_COOLDOWN_MS;
    }

    public static void markAlertSent(UUID golemUUID) {
        alertCooldowns.put(golemUUID, System.currentTimeMillis());
    }

    /** Call on golem death to free the cooldown entry. */
    public static void cleanupGolem(UUID golemUUID) {
        alertCooldowns.remove(golemUUID);
    }
}

