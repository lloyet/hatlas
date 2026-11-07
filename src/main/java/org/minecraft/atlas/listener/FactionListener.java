package org.minecraft.atlas.listener;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
import org.minecraft.atlas.util.TitleUtil;
import org.minecraft.atlas.crystal.AtlasCrystal;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.teleport.RandomTeleportManager;
import org.minecraft.atlas.teleport.HomeManager;
import org.minecraft.atlas.safezone.SafeZoneTeleportManager;
import org.minecraft.atlas.teleport.HomeTeleportManager;
import org.minecraft.atlas.teleport.DeathTeleportCooldownManager;
import org.minecraft.atlas.teleport.TeleportAtManager;
import org.minecraft.atlas.util.TabListManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionListener implements Listener {

    private static final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Crystal naming fallback on disconnect
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        TabListManager.updatePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        DeathTeleportCooldownManager.onPlayerDeath(event.getEntity().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Teleport cancel on damage
    // -------------------------------------------------------------------------

    /**
     * Cancels an active /faction home countdown if the player takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagedCancelTeleport(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (HomeTeleportManager.cancelTeleport(player.getUniqueId())) {
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
        if (RandomTeleportManager.cancelTeleport(player.getUniqueId())) {
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
        if (TeleportAtManager.cancelTeleport(player.getUniqueId())) {
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
        if (HomeManager.cancelTeleport(player.getUniqueId())) {
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
        if (SafeZoneTeleportManager.cancelTeleport(player.getUniqueId())) {
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
    }

    // -------------------------------------------------------------------------
    // Faction territory protection
    // -------------------------------------------------------------------------

    /**
     * Returns true when the player is allowed to act in the given chunk —
     * i.e. the chunk is unclaimed, or the player belongs to the owning faction.
     */
    private boolean isAllowedInChunk(Player player, Chunk chunk) {
        String owner = FactionClaimManager.getClaimingFaction(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;
        return owner.equals(FactionManager.getPlayerFaction(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean isRaider = RaiderPickaxe.isRaiderPickaxe(hand);

        Chunk  chunk        = event.getBlock().getChunk();
        String chunkOwner   = FactionClaimManager.getClaimingFaction(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());
        boolean isEnemyChunk = chunkOwner != null && !chunkOwner.equals(playerFaction);

        if (isRaider) {
            if (!isEnemyChunk) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("⚔ Raider item only works in enemy territory!", NamedTextColor.RED));
                return;
            }
            int remaining = RaiderPickaxe.decrementUses(hand);
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.sendActionBar(Component.text("⚔ Raider item exhausted!", NamedTextColor.GOLD));
            } else {
                player.getInventory().setItemInMainHand(hand);
                player.sendActionBar(Component.text("⚔ " + remaining + " break" + (remaining == 1 ? "" : "s") + " remaining.", NamedTextColor.YELLOW));
            }
            return;
        }

        if (!isAllowedInChunk(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⚔ Enemy territory — can't break!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (RaiderPickaxe.isRaiderPickaxe(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (RaiderPickaxe.isRaiderPickaxe(event.getInventory().getFirstItem())
                || RaiderPickaxe.isRaiderPickaxe(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (RaiderPickaxe.isRaiderPickaxe(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isAllowedInChunk(player, event.getBlock().getChunk())) return;
        if (event.getBlock().getType() == Material.TNT) return;
        event.setCancelled(true);
        player.sendActionBar(Component.text("⚔ Enemy territory — can't place!", NamedTextColor.RED));
    }

    /** Blocks right-click interactions (chests, doors, buttons…) and pressure-plate triggers in claimed chunks. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (isAllowedInChunk(player, block.getChunk())) return;
        // Allow offensive siege items: TNT placement, creeper spawn egg, flint & steel / fire charge on TNT
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.TNT) return;
        if (item != null && item.getType() == Material.CREEPER_SPAWN_EGG) return;
        if (block.getType() == Material.TNT && item != null
                && (item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE)) return;
        event.setCancelled(true);
        if (action == Action.RIGHT_CLICK_BLOCK) {
            player.sendActionBar(Component.text("⚔ Enemy territory — can't interact!", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Atlas Crystal damage handling
    // -------------------------------------------------------------------------

    /** Cancels all non-entity damage to atlas crystals (fire, explosions, etc.). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent) return; // handled separately
        event.setCancelled(true);
    }

    /** Applies our HP system when a player hits an atlas crystal. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAtlasCrystalDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        AtlasCrystal atlasCrystal = AtlasCrystalManager.getCrystal(crystal.getUniqueId());
        if (atlasCrystal == null) return;

        event.setCancelled(true);

        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        // Players with no faction cannot damage atlas crystals
        String attackerFaction = FactionManager.getPlayerFaction(attacker.getUniqueId());
        if (attackerFaction == null) {
            TitleUtil.notify(attacker, "You must be in a faction to attack an Atlas Crystal.", NamedTextColor.RED);
            return;
        }

        // Faction members cannot damage their own crystal
        String crystalFaction = atlasCrystal.getFactionName();
        if (crystalFaction.equals(attackerFaction)) return;

        // Immune crystal: show title + subtitle with remaining time, then bail
        if (atlasCrystal.isImmune()) {
            long secs = Math.max(0, atlasCrystal.getImmuneUntilMillis() - System.currentTimeMillis()) / 1000L;
            String timeStr = secs >= 60 ? (secs / 60) + "m " + (secs % 60) + "s" : secs + "s";
            TitleUtil.notify(attacker, "Crystal Immune!\nEnds in " + timeStr, NamedTextColor.AQUA);
            return;
        }

        // Anti-spam: allow at most 1 hit per second per attacker
        long now = System.currentTimeMillis();
        Long last = lastHitTime.get(attacker.getUniqueId());
        if (last != null && now - last < 1000L) return;
        lastHitTime.put(attacker.getUniqueId(), now);

        double damage = event.getDamage();
        boolean died = atlasCrystal.damage(damage);

        crystal.getPersistentDataContainer()
                .set(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING, crystalFaction);

        // Sound and particles on hit
        crystal.getWorld().playSound(
                crystal.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 1.0f, 1.0f);

        Location loc = crystal.getLocation();
        crystal.getWorld().spawnParticle(Particle.CRIT, loc, 30, 0.4, 0.4, 0.4, 0.25);
        crystal.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 10, 0.3, 0.3, 0.3, 0.0);
        crystal.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 20, 0.5, 0.5, 0.5, 0.1);
        crystal.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
        crystal.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 8, 0.3, 0.3, 0.3, 0.02);
        crystal.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.2, 0.2, 0.0);

        // Show damage as hit feedback: instant appear, brief stay, slow fade-out
        // — the abrupt appearance + gradual dissolution simulates text drifting upward.
        TitleUtil.hitFeedback(attacker, "-" + (int) damage + " points", NamedTextColor.RED);

        // Alert faction members that their crystal is under attack
        String displayName = atlasCrystal.getName().isEmpty()
                ? "A crystal" : "'" + atlasCrystal.getName() + "'";
        TitleUtil.broadcastAlertBold(
                FactionManager.getOnlineFactionMembers(crystalFaction, null),
                "⚔ " + displayName + " is under attack by " + attackerFaction + "!",
                NamedTextColor.RED);

        if (!died) return;

        // ── Crystal HP reached 0 ──────────────────────────────────────────────
        Faction faction = FactionManager.getFaction(crystalFaction);

        Long longestAvailable = atlasCrystal.getLongestAvailableProtection();
        if (longestAvailable != null) {
            // Consume the longest available protection — strongest shield absorbs the first
            // defeat. The broken protection regenerates at the end of its watch window
            // (crystal.protection_watch_multiplier × duration, default 2) if the crystal takes
            // no damage after the immunity ends; otherwise it is permanently lost.
            long immunityMs = longestAvailable;
            long nowMs2 = System.currentTimeMillis();
            atlasCrystal.breakProtection(longestAvailable, nowMs2);
            atlasCrystal.setHp(atlasCrystal.getMaxHp());
            atlasCrystal.setImmuneFor(immunityMs);
            atlasCrystal.updateNametag();
            AtlasCrystalManager.persistCrystalState(atlasCrystal);

            crystal.getWorld().playSound(crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.5f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 3.0f, false, false);

            // Scatter a slice of the vault rubies at the crystal location. Items are dropped
            // AFTER the explosion so the blast can't destroy them. The drop is mutating: the
            // faction's vault is decremented in-place and persisted through Storage.
            if (faction != null) {
                int dropCount = org.minecraft.atlas.faction.FactionVault.computeProtectionBreakDrop(faction);
                if (dropCount > 0) {
                    org.minecraft.atlas.faction.FactionVault.drop(faction, crystal.getLocation(), dropCount);
                    Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
                }
            }

            long immuneSecs = immunityMs / 1000L;
            String immuneStr = immuneSecs >= 60 ? (immuneSecs / 60) + "m " + (immuneSecs % 60) + "s" : immuneSecs + "s";
            TitleUtil.broadcastAlertBold(FactionManager.getOnlineFactionMembers(crystalFaction, null),
                    "⚠ Crystal protected! Immune for " + immuneStr + "!", NamedTextColor.RED);
            TitleUtil.notify(attacker, "Crystal protected! Immune " + immuneStr + ".", NamedTextColor.YELLOW);
        } else {
            // No protection — crystal is permanently destroyed.
            crystal.getWorld().playSound(crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true);
            String displayName2 = atlasCrystal.getName().isEmpty() ? "An Atlas Crystal" : "'" + atlasCrystal.getName() + "'";
            FactionManager.broadcastToFaction(crystalFaction,
                    Component.text("☠ " + displayName2 + " was destroyed by " + attackerFaction + "!", NamedTextColor.RED), null);
            Component serverMsg = Component.text("☠ [" + crystalFaction + "] lost a crystal to [" + attackerFaction + "]!", NamedTextColor.RED);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(serverMsg));
            // Detect the last-crystal case before destroyCrystal triggers the auto-disband
            // so we can attribute the global disband broadcast to the attacker faction.
            boolean willDisband = AtlasCrystalManager.getFactionCrystals(crystalFaction).size() <= 1;
            Faction crystalFactionData = FactionManager.getFaction(crystalFaction);
            Faction attackerFactionData = FactionManager.getFaction(attackerFaction);
            // Drop this crystal's chests + remove its claims while the entity is still valid;
            // other crystals of the same faction keep their own claims and chests.
            AtlasCrystalManager.destroyCrystal(atlasCrystal.getEntityUUID());
            crystal.remove(); // despawn the entity afterward
            if (willDisband) {
                NamedTextColor crystalColor  = crystalFactionData  != null ? crystalFactionData.getColor()  : NamedTextColor.WHITE;
                NamedTextColor attackerColor = attackerFactionData != null ? attackerFactionData.getColor() : NamedTextColor.WHITE;
                Component disbandMsg = Component.text("☠ Faction ", NamedTextColor.RED)
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text(crystalFaction, crystalColor))
                        .append(Component.text("]", NamedTextColor.GRAY))
                        .append(Component.text(" has been disbanded by ", NamedTextColor.RED))
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text(attackerFaction, attackerColor))
                        .append(Component.text("]", NamedTextColor.GRAY))
                        .append(Component.text(".", NamedTextColor.RED));
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(disbandMsg));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Player move — enter/leave own faction territory
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        int fromCX = from.getBlockX() >> 4, fromCZ = from.getBlockZ() >> 4;
        int toCX   = to.getBlockX()   >> 4, toCZ   = to.getBlockZ()   >> 4;
        boolean chunkChanged = fromCX != toCX || fromCZ != toCZ;
        if (!chunkChanged) return;

        boolean wasInSpawn = SafeZoneListener.isInSafeZone(from);
        boolean nowInSpawn = SafeZoneListener.isInSafeZone(to);
        boolean leavingSpawn = wasInSpawn && !nowInSpawn;

        String worldName = player.getWorld().getName();
        String fromFaction = FactionClaimManager.getClaimingFaction(worldName, fromCX, fromCZ);
        String toFaction   = FactionClaimManager.getClaimingFaction(worldName, toCX,   toCZ);
        boolean fromDonjon = DonjonManager.isChunkInDonjon(worldName, fromCX, fromCZ);
        boolean toDonjon   = DonjonManager.isChunkInDonjon(worldName, toCX,   toCZ);
        String playerFaction = FactionManager.getPlayerFaction(uuid);

        // Entering a faction chunk
        if (toFaction != null && !toFaction.equals(fromFaction)) {
            Faction faction = FactionManager.getFaction(toFaction);
            NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
            TitleUtil.alert(player, toFaction + "\nYou enter " + toFaction, color);
            if (toFaction.equals(playerFaction)) {
                player.playSound(player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.6f, 1.0f);
            }
            return;
        }

        // Entering wilderness from any claimed area or spawn protection
        if (!toDonjon && !nowInSpawn && toFaction == null && (fromFaction != null || fromDonjon || leavingSpawn)) {
            TitleUtil.notify(player, "Wilderness\nEnter the Wilderness", NamedTextColor.GREEN);
        }
    }

    // -------------------------------------------------------------------------
    // Restore atlas crystals after chunk load / server restart
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderCrystal crystal) AtlasCrystalManager.restoreIfNeeded(crystal);
        }
    }
}
