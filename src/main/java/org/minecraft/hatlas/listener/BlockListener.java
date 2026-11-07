package org.minecraft.hatlas.listener;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Creeper;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.block.RubyBlockManager;
import org.minecraft.hatlas.Item.RubyItem;
import org.minecraft.hatlas.donjon.ElectricalCreeperManager;
import org.minecraft.hatlas.faction.FactionClaimManager;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.Map;
import java.util.Set;

/**
 * Routes vanilla note-block side effects around custom ruby blocks: places the
 * note-block state on placement, drops the custom item (iron+ pickaxe required)
 * on break, and suppresses note play / redstone / physics / piston interactions
 * for tracked locations.
 */
public final class BlockListener implements Listener {

    private static final Set<Material> ACCEPTED_PICKAXES = Set.of(
            Material.IRON_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    private static final BlockFace[] NEIGHBOR_FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        String customId = RubyBlockManager.customBlockIdForItem(inHand);
        if (customId == null) return;
        Player player = event.getPlayer();
        if (!canPlaceRubyAt(player, event.getBlockPlaced().getChunk())) {
            event.setCancelled(true);
            player.sendActionBar(rubyClaimDenialMessage());
            return;
        }
        RubyBlockManager.placeAt(event.getBlockPlaced(), customId);
        recordPlace(player, inHand, event.getBlockPlaced().getLocation());
    }

    /** Ruby ledger: a placed ruby block takes its 9 gems out of the economy until it is broken. */
    private static void recordPlace(Player player, ItemStack hand, Location at) {
        if (player.getGameMode() == GameMode.CREATIVE) return; // nothing is consumed
        ItemStack one = hand.clone();
        one.setAmount(1);
        org.minecraft.hatlas.economy.RubyLedger.record(org.minecraft.hatlas.economy.LedgerKind.BLOCK_PLACE,
                org.minecraft.hatlas.economy.RubyLedger.gemValue(one),
                org.minecraft.hatlas.economy.Party.player(player), null, player, null, at);
    }

    /**
     * Ruby blocks/ores may only be placed inside a chunk claimed by the player's
     * own faction. Unclaimed chunks and other-faction chunks are both rejected.
     */
    private static boolean canPlaceRubyAt(Player player, Chunk chunk) {
        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());
        if (playerFaction == null) return false;
        String owner = FactionClaimManager.getClaimingFaction(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        return playerFaction.equals(owner);
    }

    private static Component rubyClaimDenialMessage() {
        return Component.text("⚔ Ruby blocks can only be placed in your faction's claim!",
                NamedTextColor.RED);
    }

    /**
     * Re-assert state on any tracked custom block adjacent to a newly placed block.
     * Specifically: placing a vanilla note block (or any block that changes the
     * note-block instrument tag of the neighbor below/above) triggers vanilla's
     * NoteBlock#updateShape on the adjacent tracked block, which recomputes the
     * instrument away from {@code pling} — flipping the variant key off our custom
     * model. Paper's BlockPhysicsEvent cancellation does not fully suppress this
     * recompute in every path, so we re-assert one tick later (after vanilla's
     * post-place flow has finished).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceNeighborRefresh(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        for (BlockFace face : NEIGHBOR_FACES) {
            Block neighbor = placed.getRelative(face);
            String id = RubyBlockManager.getCustomIdAt(neighbor);
            if (id == null) continue;
            Bukkit.getScheduler().runTask(Hatlas.instance, () ->
                    RubyBlockManager.ensureState(neighbor, id));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String id = RubyBlockManager.getCustomIdAt(block);
        if (id == null) return;

        // Take full control of the break so we can play stone effects instead of the
        // note-block ones — vanilla captures the destroy state before our handler can
        // mutate it, so swapping the type mid-event doesn't change the sound.
        event.setCancelled(true);
        RubyBlockManager.removeAt(block);

        Location loc = block.getLocation();
        block.setType(Material.AIR, false);
        block.getWorld().playEffect(loc, Effect.STEP_SOUND, breakEffectMaterial(id));

        Player breaker = event.getPlayer();
        ItemStack tool = breaker.getInventory().getItemInMainHand();
        // Apply durability damage like vanilla — Paper's damage() honors Unbreaking
        // and is a no-op in creative mode. We damage even when the tool isn't a
        // valid pickaxe, matching vanilla's "any tool takes 1 use on block break".
        if (tool != null && !tool.isEmpty()) {
            tool.damage(1, breaker);
        }
        if (!ACCEPTED_PICKAXES.contains(tool.getType())) return;

        // Ruby ledger: an ore creates 1 gem (MINE), a placed ruby block gives its 9 back.
        boolean ore = !"ruby_block".equals(id);
        org.minecraft.hatlas.economy.Party owner = org.minecraft.hatlas.economy.Party.player(breaker);
        ItemStack drop = RubyBlockManager.dropFor(id);
        org.minecraft.hatlas.economy.RubyLedger.record(
                ore ? org.minecraft.hatlas.economy.LedgerKind.MINE : org.minecraft.hatlas.economy.LedgerKind.BLOCK_BREAK,
                org.minecraft.hatlas.economy.RubyLedger.gemValue(drop), null, owner, breaker,
                ore ? org.minecraft.hatlas.orezone.OreZoneManager.zoneNameAt(loc) : null, loc);
        org.minecraft.hatlas.economy.RubyLedger.drop(loc.toCenterLocation(), drop, owner, ore ? "MINE" : "BLOCK_BREAK");
    }

    private static Material breakEffectMaterial(String id) {
        return switch (id) {
            case "ruby_block" -> Material.REDSTONE_BLOCK;
            case "deepslate_ruby_ore" -> Material.DEEPSLATE;
            default -> Material.STONE;
        };
    }

    /** Twin of {@link #onPlaceNeighborRefresh} for the break path. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakNeighborRefresh(BlockBreakEvent event) {
        Block broken = event.getBlock();
        for (BlockFace face : NEIGHBOR_FACES) {
            Block neighbor = broken.getRelative(face);
            String id = RubyBlockManager.getCustomIdAt(neighbor);
            if (id == null) continue;
            Bukkit.getScheduler().runTask(Hatlas.instance, () ->
                    RubyBlockManager.ensureState(neighbor, id));
        }
    }

    /**
     * Right-clicking a tracked note block normally triggers vanilla's pitch cycle.
     * Cancel that and, if the player is holding a usable item, dispatch:
     * <ul>
     *   <li>spawn egg → spawn the entity at the targeted face</li>
     *   <li>custom ruby item → place via {@link RubyBlockManager}</li>
     *   <li>other vanilla block → set the target type directly (default state)</li>
     * </ul>
     * Vanilla does not fall through to the item action once the note block "consumes"
     * the right-click, so we drive it manually.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!RubyBlockManager.isTrackedNoteBlock(clicked)) return;

        event.setCancelled(true);

        ItemStack hand = event.getItem();
        if (hand == null || hand.isEmpty()) return;

        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.getType().isAir()) return;

        Player player = event.getPlayer();
        if (target.getBoundingBox().overlaps(player.getBoundingBox())) return;

        Material handType = hand.getType();

        EntityType eggType = entityFromSpawnEgg(handType);
        if (eggType != null) {
            Location spawnLoc = target.getLocation().add(0.5, 0, 0.5);
            // Detect the donjon "powered creeper egg" — a CREEPER_SPAWN_EGG carrying our
            // PDC marker. If present, mirror DonjonListener.onElectricalEggUse: setPowered
            // + tag the spawned entity so it counts as an electrical creeper at runtime.
            if (eggType == EntityType.CREEPER
                    && ElectricalCreeperManager.isElectricalCreeperEgg(hand)) {
                target.getWorld().spawn(spawnLoc, Creeper.class, creeper -> {
                    creeper.setPowered(true);
                    creeper.getPersistentDataContainer().set(
                            ElectricalCreeperManager.getElectricalCreeperKey(),
                            PersistentDataType.BYTE, (byte) 1);
                });
            } else {
                target.getWorld().spawnEntity(spawnLoc, eggType);
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
            return;
        }

        String heldId = RubyBlockManager.customBlockIdForItem(hand);
        if (heldId != null) {
            if (!canPlaceRubyAt(player, target.getChunk())) {
                player.sendActionBar(rubyClaimDenialMessage());
                return;
            }
            RubyBlockManager.placeAt(target, heldId);
            recordPlace(player, hand, target.getLocation());
            target.getWorld().playSound(target.getLocation().toCenterLocation(),
                    Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        } else {
            if (!handType.isBlock() || handType.isAir()) return;
            target.setType(handType);
            SoundGroup sg = target.getBlockData().getSoundGroup();
            target.getWorld().playSound(target.getLocation().toCenterLocation(),
                    sg.getPlaceSound(), sg.getVolume(), sg.getPitch());
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    /**
     * Returns the {@link EntityType} that a spawn egg material spawns, or null if the
     * material isn't a spawn egg. Relies on the vanilla naming convention
     * {@code <ENTITY>_SPAWN_EGG} which is consistent across all 1.21 spawn eggs.
     */
    private static EntityType entityFromSpawnEgg(Material material) {
        String name = material.name();
        if (!name.endsWith("_SPAWN_EGG")) return null;
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Suppress note playback (and the implicit redstone-triggered animation). */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (RubyBlockManager.isTrackedNoteBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancel physics on tracked note blocks so vanilla doesn't recompute the
     * instrument when the block below them changes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) return;
        if (RubyBlockManager.isTrackedNoteBlock(block)) {
            event.setCancelled(true);
        }
    }

    /** Suppress redstone power changes — note blocks fire on any signal. */
    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        if (RubyBlockManager.isTrackedNoteBlock(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (RubyBlockManager.isTrackedNoteBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (RubyBlockManager.isTrackedNoteBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Re-assert custom-block state on chunk load. Vanilla's note-block instrument can
     * drift between save and reload via paths that don't fire BlockPhysicsEvent; if
     * the variant key falls off {@code instrument=pling}, the resource pack renders
     * the vanilla note-block model instead of the custom one.
     */
    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Map.Entry<Location, String> entry :
                RubyBlockManager.trackedInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            RubyBlockManager.ensureState(entry.getKey().getBlock(), entry.getValue());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    /**
     * Middle-click pick-block on a tracked custom block: replace vanilla's default
     * (which would spawn a plain {@code NOTE_BLOCK}) with the custom item. Only acts
     * for creative-mode players, matching vanilla pick-block semantics.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        String id = RubyBlockManager.getCustomIdAt(event.getBlock());
        if (id == null) return;

        event.setCancelled(true);
        int slot = event.getTargetSlot();
        player.getInventory().setItem(slot, RubyItem.get(id));
        player.getInventory().setHeldItemSlot(slot);
    }

    /**
     * Play a per-material step sound when a player crosses a horizontal block
     * boundary while standing on a tracked custom block. Note: vanilla's note-block
     * "wood" footstep still plays client-side; ours layers on top, so the perceived
     * sound is dominated by whichever is louder. Volume is matched to vanilla
     * footstep volume (0.15).
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        Player player = event.getPlayer();
        if (player.isFlying() || player.isGliding() || player.isSwimming()) return;

        Block below = to.getBlock().getRelative(BlockFace.DOWN);
        String id = RubyBlockManager.getCustomIdAt(below);
        if (id == null) return;

        Sound step = switch (id) {
            case "ruby_block" -> Sound.BLOCK_METAL_STEP;
            case "deepslate_ruby_ore" -> Sound.BLOCK_DEEPSLATE_STEP;
            default -> Sound.BLOCK_STONE_STEP;
        };
        player.getWorld().playSound(to, step, 0.15f, 1.0f);
    }

    private void handleExplosion(java.util.List<Block> blocks) {
        java.util.Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            String id = RubyBlockManager.getCustomIdAt(b);
            if (id == null) continue;
            RubyBlockManager.removeAt(b);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                    RubyBlockManager.dropFor(id));
            b.setType(Material.AIR, false);
            it.remove();
        }
    }
}
