package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.minecraft.atlas.orezone.OreZoneManager;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.minecraft.atlas.safezone.SafeZone;
import org.minecraft.atlas.safezone.SafeZoneManager;
import org.minecraft.atlas.util.TitleUtil;

public class SafeZoneListener implements Listener {

    // ── Protection check ───────────────────────────────────────────────────────

    /** Returns true if the location is inside any registered safe zone. */
    public static boolean isInSafeZone(Location location) {
        return SafeZoneManager.isProtected(location);
    }

    // ── Visit tracking ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        // Only process when the player enters a new chunk
        if (from.getChunk().equals(to.getChunk())) return;

        SafeZone fromZone = SafeZoneManager.getAt(from);
        SafeZone toZone   = SafeZoneManager.getAt(to);
        if (toZone != null && (fromZone == null || !fromZone.getName().equals(toZone.getName()))) {
            SafeZoneManager.recordVisit(event.getPlayer().getUniqueId(), toZone.getName());
            String label = SafeZoneManager.capitalizedName(toZone.getName());
            TitleUtil.alert(event.getPlayer(), label + "\nYou enter Safe Zone", NamedTextColor.YELLOW);
        }
    }

    // ── Protection event handlers ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInSafeZone(player.getLocation())) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker) {
            attacker.sendActionBar(Component.text("PvP is disabled in spawn protection!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (isInSafeZone(event.getEntity().getLocation())) return;
        if (!isInSafeZone(attacker.getLocation())) return;
        event.setCancelled(true);
        attacker.sendActionBar(Component.text("PvP is disabled in spawn protection!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isInSafeZone(event.getBlock().getLocation())) return;
        // Ore zones win over safe zones: their blocks can be mined (e.g. an ore zone in a city).
        if (OreZoneManager.isInOreZone(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text(
                "Block breaking is disabled in spawn protection.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;
        if (!isInSafeZone(event.getClickedBlock().getLocation())) return;
        event.setCancelled(true);
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.getPlayer().sendActionBar(Component.text(
                    "Interactions are disabled in spawn protection.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isInSafeZone(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isInSafeZone(block.getLocation()));
    }
}
