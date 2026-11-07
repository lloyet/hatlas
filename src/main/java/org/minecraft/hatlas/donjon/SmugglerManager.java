package org.minecraft.hatlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Illusioner;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmugglerManager {

    private static NamespacedKey KEY_SMUGGLER;
    private static long cooldownMs = 10_000L;

    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    public static void init() {
        KEY_SMUGGLER = new NamespacedKey(Hatlas.instance, "smuggler_npc");
    }

    public static void loadConfig(FileConfiguration config) {
        cooldownMs = config.getLong("smuggler_teleport_cooldown_seconds", 10L) * 1000L;
    }

    public static NamespacedKey getKey() {
        return KEY_SMUGGLER;
    }

    public static void spawnSmuggler(Location location) {
        location.getWorld().spawn(location, Illusioner.class, illusioner -> {
            illusioner.setAI(false);
            illusioner.setInvulnerable(true);
            illusioner.setGravity(true);
            illusioner.setPersistent(true);
            illusioner.setRemoveWhenFarAway(false);
            illusioner.customName(Component.text("Smuggler", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            illusioner.setCustomNameVisible(true);
            illusioner.getPersistentDataContainer().set(KEY_SMUGGLER, PersistentDataType.STRING, "SMUGGLER");
        });
    }

    public static boolean isSmugglerNpc(Entity entity) {
        if (!(entity instanceof Illusioner illusioner)) return false;
        return "SMUGGLER".equals(illusioner.getPersistentDataContainer()
                .get(KEY_SMUGGLER, PersistentDataType.STRING));
    }

    public static boolean isOnCooldown(UUID playerUUID) {
        Long expiry = cooldownExpiry.get(playerUUID);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldownExpiry.remove(playerUUID);
            return false;
        }
        return true;
    }

    public static long getRemainingCooldown(UUID playerUUID) {
        Long expiry = cooldownExpiry.get(playerUUID);
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public static void startCooldown(UUID playerUUID) {
        cooldownExpiry.put(playerUUID, System.currentTimeMillis() + cooldownMs);
    }
}
