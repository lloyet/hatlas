package org.minecraft.hatlas.listener;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.donjon.Donjon;
import org.minecraft.hatlas.donjon.DonjonManager;
import org.minecraft.hatlas.donjon.DonjonRarity;
import org.minecraft.hatlas.donjon.DonjonStatus;
import org.minecraft.hatlas.donjon.ElectricalCreeperManager;
import org.minecraft.hatlas.donjon.SmugglerManager;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.gui.DonjonListGui;
import org.minecraft.hatlas.util.TitleUtil;

public class DonjonListener implements Listener {

    // -------------------------------------------------------------------------
    // Block protection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("hatlas.donjon.admin")) return;

        Location loc = event.getBlock().getLocation();
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(loc.getWorld(), key);
        if (donjon == null) return;

        event.setCancelled(true);
        TitleUtil.subtitle(event.getPlayer(), "You cannot break blocks in a donjon!", NamedTextColor.RED);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("hatlas.donjon.admin")) return;

        Location loc = event.getBlock().getLocation();
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(loc.getWorld(), key);
        if (donjon == null) return;

        event.setCancelled(true);
        TitleUtil.subtitle(event.getPlayer(), "You cannot place blocks in a donjon!", NamedTextColor.RED);
    }

    /** Prevents non-admin players from emptying buckets (water, lava, etc.) inside donjon chunks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().hasPermission("hatlas.donjon.admin")) return;
        Block block = event.getBlock();
        long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
        if (DonjonManager.getDonjonAtChunk(block.getWorld(), key) == null) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("You cannot use buckets inside a donjon!", NamedTextColor.RED));
    }

    // Interactive block materials that players must not use inside donjon chunks.
    // Tags cover buttons, doors, trapdoors, fence gates, pressure plates and shulker boxes.
    // The extra set covers containers, mechanisms and utility blocks not in any tag.
    private static final java.util.Set<Material> INTERACTIVE_MATERIALS = java.util.Set.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST,
        Material.HOPPER, Material.DROPPER, Material.DISPENSER,
        Material.CRAFTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
        Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.BEACON,
        Material.GRINDSTONE, Material.LOOM, Material.CARTOGRAPHY_TABLE,
        Material.FLETCHING_TABLE, Material.SMITHING_TABLE, Material.STONECUTTER,
        Material.LEVER, Material.NOTE_BLOCK, Material.JUKEBOX, Material.BELL,
        Material.DAYLIGHT_DETECTOR, Material.COMPOSTER, Material.LECTERN,
        Material.CHISELED_BOOKSHELF, Material.BEEHIVE, Material.BEE_NEST,
        Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON,
        Material.POWDER_SNOW_CAULDRON, Material.CAKE, Material.REPEATER,
        Material.COMPARATOR, Material.TARGET, Material.RESPAWN_ANCHOR,
        Material.VAULT, Material.TRIAL_SPAWNER, Material.CAMPFIRE, Material.SOUL_CAMPFIRE
    );

    private static boolean isInteractiveBlock(Block block) {
        Material m = block.getType();
        return INTERACTIVE_MATERIALS.contains(m)
                || org.bukkit.Tag.BUTTONS.isTagged(m)
                || org.bukkit.Tag.DOORS.isTagged(m)
                || org.bukkit.Tag.TRAPDOORS.isTagged(m)
                || org.bukkit.Tag.FENCE_GATES.isTagged(m)
                || org.bukkit.Tag.PRESSURE_PLATES.isTagged(m)
                || org.bukkit.Tag.SHULKER_BOXES.isTagged(m);
    }

    /**
     * Prevents non-admin players from right-clicking interactive blocks inside donjon chunks.
     * Item use in air (eating, drawing bows, throwing potions) is intentionally allowed.
     * Runs at NORMAL priority so the HIGH vault handler still fires for vault blocks.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractInsideDonjon(PlayerInteractEvent event) {
        if (event.getPlayer().hasPermission("hatlas.donjon.admin")) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block clicked = event.getClickedBlock();

        // Allow vault block — the HIGH vault handler manages it
        if (DonjonManager.getDonjonAtVault(clicked.getLocation()) != null) return;

        // Only restrict interactive blocks (containers, mechanisms, doors, etc.)
        if (!isInteractiveBlock(clicked)) return;

        long key = Chunk.getChunkKey(clicked.getX() >> 4, clicked.getZ() >> 4);
        if (DonjonManager.getDonjonAtChunk(clicked.getWorld(), key) == null) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("You cannot interact with this inside a donjon.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
            return DonjonManager.getDonjonAtChunk(block.getWorld(), key) != null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
            return DonjonManager.getDonjonAtChunk(block.getWorld(), key) != null;
        });
    }

    // -------------------------------------------------------------------------
    // Smuggler NPC
    // -------------------------------------------------------------------------

    @EventHandler
    public void onSmugglerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!SmugglerManager.isSmugglerNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        if (JobListener.denyIfNoFaction(event.getPlayer())) return;
        new DonjonListGui(event.getPlayer()).open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmugglerDamage(EntityDamageByEntityEvent event) {
        if (SmugglerManager.isSmugglerNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Totem interaction — start donjon
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        org.bukkit.block.Block clicked = event.getClickedBlock();
        Donjon donjon = DonjonManager.getDonjonAtVault(clicked.getLocation());
        if (donjon == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) {
            TitleUtil.notify(player, "Join a faction to start a donjon!", NamedTextColor.RED);
            return;
        }

        if (donjon.isInProgress()) {
            TitleUtil.notify(player, "Donjon run already in progress!", NamedTextColor.YELLOW);
            return;
        }

        // Doctor requirements — donjon must be fully configured before it can be started
        if (donjon.getSpawnPointsById().isEmpty()) {
            TitleUtil.notify(player, "Donjon not ready: no wave spawn points defined!", NamedTextColor.RED);
            return;
        }
        if (donjon.getTeleportSpawn() == null) {
            TitleUtil.notify(player, "Donjon not ready: no teleport spawn defined!", NamedTextColor.RED);
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Material itemType = heldItem.getType();
        ItemMeta heldMeta = heldItem.getItemMeta();
        boolean isDonjonKey = heldMeta != null
                && heldMeta.getPersistentDataContainer().has(DonjonManager.keyDonjonMarker, PersistentDataType.BYTE);

        if (donjon.getStatus() != DonjonStatus.ACTIVE) {
            // IDLE donjon — only an Ominous Donjon Key (marker + correct material) can force-activate it
            if (!itemType.equals(Material.OMINOUS_TRIAL_KEY) || !isDonjonKey) {
                TitleUtil.notify(player,
                        "This donjon is not active. Use an Ominous Donjon Key to force-activate it.",
                        NamedTextColor.RED);
                return;
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            // Read pre-rolled level/rarity from the key's PDC (set when the key was created)
            int storedLevel = -1;
            DonjonRarity storedRarity = null;
            var pdc = heldMeta.getPersistentDataContainer();
            if (pdc.has(DonjonManager.keyOminousLevel, PersistentDataType.INTEGER))
                storedLevel = pdc.get(DonjonManager.keyOminousLevel, PersistentDataType.INTEGER);
            if (pdc.has(DonjonManager.keyOminousRarity, PersistentDataType.STRING)) {
                try { storedRarity = DonjonRarity.valueOf(pdc.get(DonjonManager.keyOminousRarity, PersistentDataType.STRING)); }
                catch (IllegalArgumentException ignored) {}
            }
            if (storedLevel >= 0 && storedRarity != null) {
                DonjonManager.activateDonjonWithParams(donjon, storedLevel, storedRarity);
            } else {
                DonjonManager.forceActivateDonjon(donjon);
            }
            DonjonManager.startDonjon(donjon, player, factionName);
        } else {
            // ACTIVE donjon — any Donjon Key (trial or ominous) with the PDC marker
            boolean isTrial   = itemType.equals(Material.TRIAL_KEY);
            boolean isOminous = itemType.equals(Material.OMINOUS_TRIAL_KEY);
            if ((!isTrial && !isOminous) || !isDonjonKey) {
                TitleUtil.notify(player,
                        "You need a Donjon Trial Key or Ominous Donjon Key to start this donjon.",
                        NamedTextColor.RED);
                return;
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

            // Ominous key overrides the donjon's current level and rarity with the key's values
            if (isOminous) {
                var pdc = heldMeta.getPersistentDataContainer();
                int storedLevel = -1;
                DonjonRarity storedRarity = null;
                if (pdc.has(DonjonManager.keyOminousLevel, PersistentDataType.INTEGER))
                    storedLevel = pdc.get(DonjonManager.keyOminousLevel, PersistentDataType.INTEGER);
                if (pdc.has(DonjonManager.keyOminousRarity, PersistentDataType.STRING)) {
                    try { storedRarity = DonjonRarity.valueOf(pdc.get(DonjonManager.keyOminousRarity, PersistentDataType.STRING)); }
                    catch (IllegalArgumentException ignored) {}
                }
                if (storedLevel >= 0) donjon.setLevel(storedLevel);
                if (storedRarity != null) donjon.setRarity(storedRarity);
            }

            DonjonManager.startDonjon(donjon, player, factionName);
        }
    }

    // -------------------------------------------------------------------------
    // Slime / MagmaCube split — register children in current wave
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlimeSplitSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) return;
        if (!(event.getEntity() instanceof LivingEntity child)) return;

        Location loc = child.getLocation();
        long chunkKey = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(loc.getWorld(), chunkKey);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonManager.registerSplitChild(child, donjon, donjon.getCurrentWaveIndex());
    }

    // -------------------------------------------------------------------------
    // Entity death
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!DonjonManager.isDonjonEntity(living)) return;

        // Always suppress vanilla drops and exp for donjon mobs
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Environmental kills (fall, drowning, self-explosion, etc.) don't count — respawn the mob.
        // Player kills and mob-on-mob kills (different entity) count towards wave completion.
        if (!shouldCountKill(living)) {
            DonjonManager.replaceWaveMob(living);
            return;
        }

        DonjonManager.onEntityDeath(living);
    }

    /**
     * Returns true if this death should count as a wave kill.
     * Player kills always count. Kills by a *different* entity (mob-on-mob) also count.
     * Self-inflicted damage and purely environmental causes (fall, drowning, fire, etc.) do not.
     */
    private static boolean shouldCountKill(LivingEntity entity) {
        if (entity.getKiller() != null) return true; // direct player kill
        EntityDamageEvent last = entity.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent ede)) return false;
        Entity damager = ede.getDamager();
        // For projectiles, the shooter is the actual attacker
        if (damager instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return !shooterEntity.getUniqueId().equals(entity.getUniqueId());
            }
            return false;
        }
        return !damager.getUniqueId().equals(entity.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Zombie → Drowned conversion — keep the new entity tracked in the wave
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZombieConvert(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.DROWNED) return;
        if (!(event.getEntity() instanceof LivingEntity zombie)) return;
        if (!DonjonManager.isDonjonEntity(zombie)) return;
        if (!(event.getTransformedEntity() instanceof LivingEntity drowned)) return;

        DonjonManager.transferWaveTracking(zombie, drowned);
    }

    // -------------------------------------------------------------------------
    // Friendly-fire protection inside donjon chunks
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (attacker.equals(target)) return;

        long chunkKey = Chunk.getChunkKey(target.getChunk().getX(), target.getChunk().getZ());
        Donjon donjon = DonjonManager.getDonjonAtChunk(target.getWorld(), chunkKey);
        if (donjon == null || !donjon.isInProgress()) return;

        String attackerFaction = FactionManager.getPlayerFaction(attacker.getUniqueId());
        String targetFaction   = FactionManager.getPlayerFaction(target.getUniqueId());
        if (!FactionManager.areAllied(attackerFaction, targetFaction)) return;

        event.setCancelled(true);
        attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                target.getName() + " is your ally.", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Boss damage tracking
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!DonjonManager.isBossEntity(target)) return;

        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }

        if (player == null) return;

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) return;

        DonjonManager.onBossDamage(target, event.getFinalDamage(), factionName);
    }

    // -------------------------------------------------------------------------
    // Player move — enter/exit donjon title
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();

        int fromCX = from.getBlockX() >> 4, fromCZ = from.getBlockZ() >> 4;
        int toCX = to.getBlockX() >> 4, toCZ = to.getBlockZ() >> 4;
        if (fromCX == toCX && fromCZ == toCZ) return; // same chunk

        long fromKey = Chunk.getChunkKey(fromCX, fromCZ);
        long toKey = Chunk.getChunkKey(toCX, toCZ);

        Player player = event.getPlayer();

        for (Donjon donjon : DonjonManager.getDonjons().values()) {
            if (!player.getWorld().equals(donjon.getWorld())) continue;

            boolean wasIn = donjon.getProtectedChunkKeys().contains(fromKey);
            boolean isIn = donjon.getProtectedChunkKeys().contains(toKey);

            if (!wasIn && isIn) {
                if (donjon.getStatus() == DonjonStatus.IDLE && !donjon.isInProgress()) {
                    // IDLE: show donjon name + "Idle"
                    TitleUtil.alert(player,
                            donjon.getName() + "\nIdle",
                            NamedTextColor.WHITE);
                } else {
                    TitleUtil.alert(player,
                            donjon.getName() + " LvL." + donjon.getLevel()
                                    + " [" + donjon.getRarity().getDisplayName() + "]",
                            donjon.getRarity().getColor());
                }
                player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, SoundCategory.BLOCKS, 0.6f, 1.0f);
                DonjonManager.recordPlayerVisit(player.getUniqueId(), donjon.getId());
            }

        }
    }

    // -------------------------------------------------------------------------
    // Entities load — restore TextDisplay nametags
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (DonjonManager.keyTotemDisplay == null) return;

        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof TextDisplay td)) continue;
            if (!td.getPersistentDataContainer().has(DonjonManager.keyTotemDisplay)) continue;

            DonjonManager.restoreNametag(td);
        }
    }

    // -------------------------------------------------------------------------
    // Electrical Creeper Egg — use to spawn a charged creeper
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElectricalEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (!ElectricalCreeperManager.isElectricalCreeperEgg(item)) return;

        event.setCancelled(true);

        Location spawnLoc = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation().add(0.5, 1, 0.5)
                : event.getPlayer().getLocation();

        World world = spawnLoc.getWorld();
        if (world == null) return;

        world.spawn(spawnLoc, Creeper.class, creeper -> {
            creeper.setPowered(true);
            creeper.getPersistentDataContainer().set(
                    ElectricalCreeperManager.getElectricalCreeperKey(),
                    PersistentDataType.BYTE, (byte) 1);
        });

        // Consume one egg from the stack
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    // -------------------------------------------------------------------------
    // Electrical Creeper — track hits on obsidian (4 hits = destroyed)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onElectricalCreeperExplode(EntityExplodeEvent event) {
        if (!ElectricalCreeperManager.isElectricalCreeper(event.getEntity())) return;

        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        int radius = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block block = world.getBlockAt(
                            loc.getBlockX() + dx,
                            loc.getBlockY() + dy,
                            loc.getBlockZ() + dz);
                    if (block.getType() != Material.OBSIDIAN) continue;

                    int hits = ElectricalCreeperManager.addObsidianHit(block.getLocation());
                    if (hits >= ElectricalCreeperManager.OBSIDIAN_HITS_REQUIRED) {
                        ElectricalCreeperManager.clearObsidianHit(block.getLocation());
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Obsidian hit-map cleanup when a block is broken by other means
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onObsidianBreakCleanup(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.OBSIDIAN) {
            ElectricalCreeperManager.clearObsidianHit(event.getBlock().getLocation());
        }
    }

}
