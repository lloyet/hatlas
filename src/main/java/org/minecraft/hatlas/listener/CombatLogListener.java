package org.minecraft.hatlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.minecraft.hatlas.combat.CombatLogManager;

/**
 * Bukkit-event surface for the combat-log system. Drives {@link CombatLogManager} via
 * its static API — {@code mark(...)} on player-vs-player damage, {@code
 * applyCombatLogoutPenalty(...)} when a flagged player disconnects.
 */
public class CombatLogListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        CombatLogManager.mark(attacker);
        CombatLogManager.mark(victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLogout(PlayerQuitEvent event) {
        CombatLogManager.applyCombatLogoutPenalty(event.getPlayer());
    }
}
