package org.minecraft.hatlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.minecraft.hatlas.golem.GolemManager;
import org.minecraft.hatlas.golem.GolemType;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.concurrent.ThreadLocalRandom;

public class GolemListener implements Listener {

    /** How far (in blocks) a tagged golem scans for enemies every tick cycle. */
    private static final double SCAN_RADIUS    = 16.0;
    private static final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;

    // =========================================================================
    // Scheduled targeting scan — runs every 20 ticks (1 s)
    // =========================================================================

    /**
     * Starts the repeating task that drives golem targeting.
     * Iron Golems have no natural AI to attack players, so we must call
     * {@code setTarget()} explicitly; this task does that every second.
     */
    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, GolemListener::tickAllGolems, 20L, 20L);
    }

    private static void tickAllGolems() {
        for (World world : Bukkit.getWorlds()) {
            for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
                if (!GolemManager.isTagged(golem)) continue;

                String   golemFaction = GolemManager.getFaction(golem);
                Location golemLoc   = golem.getLocation();

                // Find the nearest non-faction player within SCAN_RADIUS
                Player nearest = null;
                double nearestDist = SCAN_RADIUS_SQ;

                for (Player player : world.getPlayers()) {
                    String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());
                    if (golemFaction != null && golemFaction.equals(playerFaction)) continue;

                    double dist = player.getLocation().distanceSquared(golemLoc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = player;
                    }
                }

                if (nearest == null) continue;

                // Drive the golem's AI toward the target
                golem.setTarget(nearest);

                // Detection alert (throttled to once per 10 s per golem)
                if (golemFaction != null && !GolemManager.isAlertOnCooldown(golem.getUniqueId())) {
                    GolemManager.markAlertSent(golem.getUniqueId());
                    Component alert = Component.text("⚠ Your golem detected ", NamedTextColor.YELLOW)
                            .append(Component.text(nearest.getName(), NamedTextColor.RED))
                            .append(Component.text("!", NamedTextColor.YELLOW));
                    FactionManager.broadcastToFaction(golemFaction, alert, null);
                }
            }
        }
    }

    // =========================================================================
    // Post-restart name restoration
    // =========================================================================

    /**
     * When the server restarts, PDC and attributes survive in entity NBT, but
     * the custom name (Adventure component) is not persisted by Paper — it is
     * stored only in memory.  Re-apply it as entities load from disk.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof IronGolem golem) || !GolemManager.isTagged((golem))) continue;

            GolemManager.restoreNameTag(golem);
        }
    }

    // =========================================================================
    // Golem creation
    // =========================================================================

    /**
     * Detects the T-shaped golem pattern when a carved pumpkin is placed.
     * Cancels the placement, consumes the pumpkin from the builder's hand,
     * removes the four body blocks, and spawns the appropriate tagged IronGolem.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CARVED_PUMPKIN) return;

        GolemType type = detectPattern(placed);
        if (type == null) return;

        Player player = event.getPlayer();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());

        if (factionName == null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You must be in a faction to build a golem.", NamedTextColor.RED));
            return;
        }

        // Cancel so Paper does not place the pumpkin block or trigger setPlacedBy().
        event.setCancelled(true);

        // Manually consume the pumpkin from the builder's hand.
        consumeHeldItem(player, event.getHand());

        // Remove the four body blocks (the pumpkin was canceled, so only 4 remain).
        removeBodyBlocks(placed, type);

        // Spawn the tagged golem with its feet at the feet-block position.
        Block feet = placed.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN);
        Location spawnLoc = feet.getLocation().add(0.5, 0, 0.5);

        IronGolem golem = feet.getWorld().spawn(spawnLoc, IronGolem.class, g -> g.setPlayerCreated(true));
        GolemManager.init(golem, type, factionName);

        player.sendMessage(Component.text("A " + type.getDisplayName() + " Golem has been summoned!", NamedTextColor.GREEN));
    }

    /**
     * Safety net: if Paper's internal golem-pattern check fires before our
     * BlockPlaceEvent cancellation takes effect (version-dependent ordering),
     * cancel the vanilla BUILD_IRONGOLEM spawn so we don't end up with two golems.
     * Our tagged golem was already spawned in onBlockPlace.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) return;
        if (!(event.getEntity() instanceof IronGolem)) return;

        // If there is already a tagged golem very close by (spawned by us this tick),
        // cancel the vanilla spawn to prevent duplicates.
        event.getLocation().getWorld().getNearbyEntities(event.getLocation(), 1.5, 1.5, 1.5,
                        e -> e instanceof IronGolem ig && GolemManager.isTagged(ig))
                .stream().findAny().ifPresent(e -> event.setCancelled(true));
    }

    // =========================================================================
    // Targeting — safety net: block attacks on same-faction members
    // =========================================================================

    /**
     * Cancels targeting of same-faction players.  The scheduler (tickAllGolems)
     * is the one that calls setTarget() for non-faction players and sends alerts;
     * this handler is purely a safety guard.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (!GolemManager.isTagged(golem)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        String golemFaction  = GolemManager.getFaction(golem);
        String targetFaction = FactionManager.getPlayerFaction(target.getUniqueId());

        if (golemFaction != null && golemFaction.equals(targetFaction)) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // Combat — type-specific special effects on attack
    // =========================================================================

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof IronGolem golem)) return;
        if (!GolemManager.isTagged(golem)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        String golemFaction  = GolemManager.getFaction(golem);
        String targetFaction = FactionManager.getPlayerFaction(target.getUniqueId());

        // Safety net: never harm same-faction players
        if (golemFaction != null && golemFaction.equals(targetFaction)) {
            event.setCancelled(true);

            return;
        }

        GolemType type = GolemManager.getType(golem);
        if (type == null) return;

        switch (type) {
            case AMETHYST -> {
                // Zero damage; 15 % chance to apply Glowing (spectral visibility through walls)
                event.setDamage(0.0);
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                }
            }
            case SOULSAND -> {
                // 30 % chance Slowness I for 20 s
                if (ThreadLocalRandom.current().nextDouble() < 0.30) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 0, false, false));
                }
            }
            case EMERALD -> {
                // Regeneration II for 20 s to all faction members within 16 blocks
                applyRegenToFaction(golem, golemFaction);
            }
            case CRYING_OBSIDIAN -> {
                // 5 % chance to teleport the target to the Nether
                if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                    teleportToNether(target);
                }
            }
            default -> {} // IRON, GOLD: no special effect beyond attribute-based stats
        }
    }

    // =========================================================================
    // Healing — right-click the golem with its building material
    // =========================================================================

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof IronGolem golem)) return;
        if (!GolemManager.isTagged(golem)) return;

        Player player = event.getPlayer();
        String golemFaction = GolemManager.getFaction(golem);
        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());

        if (golemFaction == null || !golemFaction.equals(playerFaction)) return;

        GolemType type = GolemManager.getType(golem);
        if (type == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != type.getMaterial()) return;

        event.setCancelled(true);

        AttributeInstance maxHpAttr = golem.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr == null) return;

        double maxHp = maxHpAttr.getValue();

        if (golem.getHealth() >= maxHp) {
            player.sendMessage(Component.text("The golem is already at full health.", NamedTextColor.YELLOW));
            return;
        }

        // Heal 10 % of max HP
        golem.setHealth(Math.min(golem.getHealth() + maxHp * 0.10, maxHp));

        // Consume one item
        consumeHeldItem(player, EquipmentSlot.HAND);

        player.sendMessage(Component.text("You healed the golem.", NamedTextColor.GREEN));
    }

    // =========================================================================
    // Death — clear vanilla drops, drop 0–5 building material if killed by player
    // =========================================================================

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (!GolemManager.isTagged(golem)) return;

        // Replace vanilla drops (iron ingots, poppies) with nothing by default
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Only drop material when killed by a player
        GolemType type = GolemManager.getType(golem);
        if (type != null && golem.getKiller() != null) {
            int count = ThreadLocalRandom.current().nextInt(6); // 0–5 inclusive
            if (count > 0) {
                golem.getWorld().dropItemNaturally(golem.getLocation(), new ItemStack(type.getMaterial(), count));
            }
        }

        GolemManager.cleanupGolem(golem.getUniqueId());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns the GolemType whose material matches all 4 body blocks of the
     * T-pattern below the just-placed pumpkin, or null if no pattern matches
     * Pattern (pumpkin placed at P):
     *      P          ← carved pumpkin (trigger)
     *   B  B  B    ← body center + 2 arms (same material, one axis)
     *      F       ← feet (same material)
     */
    private static GolemType detectPattern(Block pumpkin) {
        Block body = pumpkin.getRelative(BlockFace.DOWN); // y − 1
        Block feet = body.getRelative(BlockFace.DOWN);    // y − 2

        Material mat = body.getType();
        if (mat != feet.getType() || mat.isAir()) return null;

        GolemType type = GolemType.fromMaterial(mat);
        if (type == null) return null;

        // East–West arm pair
        if (body.getRelative(BlockFace.EAST).getType() == mat
                && body.getRelative(BlockFace.WEST).getType() == mat) return type;
        // North–South arm pair
        if (body.getRelative(BlockFace.NORTH).getType() == mat
                && body.getRelative(BlockFace.SOUTH).getType() == mat) return type;

        return null;
    }

    /**
     * Removes the four body blocks (body-center + feet + both arms).
     * The pumpkin was already canceled and never placed in the world.
     */
    private static void removeBodyBlocks(Block pumpkin, GolemType type) {
        Block body = pumpkin.getRelative(BlockFace.DOWN);
        Block feet = body.getRelative(BlockFace.DOWN);
        Material mat = type.getMaterial();

        // Determine which arm axis is active before we start clearing
        boolean ewArms = body.getRelative(BlockFace.EAST).getType() == mat;

        body.setType(Material.AIR, false);
        feet.setType(Material.AIR, false);

        if (ewArms) {
            body.getRelative(BlockFace.EAST).setType(Material.AIR, false);
            body.getRelative(BlockFace.WEST).setType(Material.AIR, false);
        } else {
            body.getRelative(BlockFace.NORTH).setType(Material.AIR, false);
            body.getRelative(BlockFace.SOUTH).setType(Material.AIR, false);
        }
    }

    /** Removes one item from the player's specified hand slot. */
    private static void consumeHeldItem(Player player, EquipmentSlot hand) {
        ItemStack item = (hand == EquipmentSlot.OFF_HAND)
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (item.getAmount() == 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Applies Regeneration II for 20 s to all online faction members within 16 blocks. */
    private static void applyRegenToFaction(IronGolem golem, String factionName) {
        if (factionName == null) return;
        Location golemLoc = golem.getLocation();

        for (var uuid : FactionManager.getFactionPlayers(factionName)) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) continue;
            if (member.getLocation().distanceSquared(golemLoc) <= 256.0) { // 16² blocks
                member.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1, false, false));
            }
        }
    }

    /**
     * Teleports the target player to the Nether at the coordinate-scaled equivalent
     * of their current position (x/8, y, z/8).  Searches ±16 XZ nether blocks for an
     * existing portal; if none is found, builds a new one at the landing spot.
     */
    private static void teleportToNether(Player target) {
        if (target.getWorld().getEnvironment() == World.Environment.NETHER) return;

        World nether = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst().orElse(null);
        if (nether == null) return;

        Location cur = target.getLocation();
        int nx = (int)(cur.getX() / 8);
        int nz = (int)(cur.getZ() / 8);

        // Prefer an existing portal within 16 nether blocks (= 128 overworld blocks,
        // the vanilla portal-linking search radius).
        Location existing = findNearbyPortal(nether, nx, nz, 16);
        if (existing != null) {
            int rx = existing.getBlockX() + ThreadLocalRandom.current().nextInt(-16, 17);
            int rz = existing.getBlockZ() + ThreadLocalRandom.current().nextInt(-16, 17);
            int ry = findSafeY(nether, rx, rz);
            target.teleport(new Location(nether, rx + 0.5, ry, rz + 0.5, cur.getYaw(), cur.getPitch()));
        } else {
            // Randomise the portal position within ±16 nether blocks of the scaled coordinate
            int rx = nx + ThreadLocalRandom.current().nextInt(-16, 17);
            int rz = nz + ThreadLocalRandom.current().nextInt(-16, 17);
            int ny = findSafeY(nether, rx, rz);
            createNetherPortal(nether, rx, ny, rz);
            target.teleport(new Location(nether, rx + 0.5, ny, rz + 0.5, cur.getYaw(), cur.getPitch()));
        }

        target.sendMessage(Component.text("The golem's power sent you to the Nether!", NamedTextColor.DARK_RED));
    }

    /**
     * Scans all valid nether Y levels in an XZ square of the given radius for a
     * NETHER_PORTAL block.  Returns the bottom-most portal block position (centred
     * on block), or null if none found.
     */
    private static Location findNearbyPortal(World world, int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = 5; y < 120; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        // Walk down to the bottom of this portal column
                        while (y > 1 && world.getBlockAt(x, y - 1, z).getType() == Material.NETHER_PORTAL) y--;

                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Searches downward from y=64 for a solid floor with 3 clear blocks above
     * (headroom for a 3-tall portal interior + player height).
     */
    private static int findSafeY(World world, int nx, int nz) {
        for (int y = 64; y > 2; y--) {
            if ( world.getBlockAt(nx, y,     nz).isSolid()
                    && !world.getBlockAt(nx, y + 1, nz).isSolid()
                    && !world.getBlockAt(nx, y + 2, nz).isSolid()
                    && !world.getBlockAt(nx, y + 3, nz).isSolid()) {
                return y + 1;
            }
        }
        return 32; // fallback
    }

    /**
     * Places a minimum (2 wide × 3 tall interior) nether portal at (nx, ny, nz),
     * oriented along the X axis.  Obsidian frame + nether-portal blocks.
     *
     * Layout (viewed from Z, x right, y up):
     *   x:  nx-1  nx  nx+1  nx+2
     *   ny+3:  O   O    O    O    ← top frame
     *   ny+2:  O   P    P    O
     *   ny+1:  O   P    P    O
     *   ny+0:  O   P    P    O    ← player spawns here
     *   ny-1:  O   O    O    O    ← bottom frame (floor)
     */
    private static void createNetherPortal(World world, int nx, int ny, int nz) {
        for (int x = nx - 1; x <= nx + 2; x++) {
            setBlock(world, x, ny - 1, nz, Material.OBSIDIAN);
            setBlock(world, x, ny + 3, nz, Material.OBSIDIAN);
        }
        for (int y = ny; y <= ny + 2; y++) {
            setBlock(world, nx - 1, y, nz, Material.OBSIDIAN);
            setBlock(world, nx + 2, y, nz, Material.OBSIDIAN);
            world.getBlockAt(nx,     y, nz).setType(Material.AIR, false);
            world.getBlockAt(nx + 1, y, nz).setType(Material.AIR, false);
        }
        for (int y = ny; y <= ny + 2; y++) {
            placePortalBlock(world.getBlockAt(nx,     y, nz));
            placePortalBlock(world.getBlockAt(nx + 1, y, nz));
        }
    }

    private static void placePortalBlock(Block block) {
        block.setType(Material.NETHER_PORTAL, false);
        BlockData bd = block.getBlockData();
        if (bd instanceof Orientable orientable) {
            orientable.setAxis(Axis.X);
            block.setBlockData(bd, false);
        }
    }

    /** Sets a block only if it is not bedrock (avoids punching holes in the nether ceiling). */
    private static void setBlock(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.BEDROCK) block.setType(material, false);
    }
}

