package org.minecraft.hatlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.gui.JobMainGui;
import org.minecraft.hatlas.gui.JokeyriniQuestGui;
import org.minecraft.hatlas.gui.NpcJobSwitchGui;
import org.minecraft.hatlas.job.Job;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.job.JokeyriniManager;
import org.minecraft.hatlas.job.PlayerJobData;
import org.minecraft.hatlas.quest.QuestManager;
import org.minecraft.hatlas.util.GuiUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JobListener implements Listener {

    /** Last player who damaged each entity — used as a fallback when getKiller() is null (e.g. reflected projectiles). */
    private final Map<UUID, UUID> lastPlayerDamager = new HashMap<>();

    private static final Set<Material> MINER_BLOCKS = Set.of(
        Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
        Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
        Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE,
        Material.GLOWSTONE
    );

    private static final Set<Material> LUMBERJACK_LOGS = Set.of(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM
    );

    // Crops harvested by breaking — fire "harvest_crop" action type
    private static final Set<Material> FARMER_HARVEST_CROPS = Set.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.MELON, Material.PUMPKIN,
            Material.TORCHFLOWER_CROP
    );

    // Plants harvested by breaking — fire "break_block" action type
    private static final Set<Material> FARMER_BREAK_CROPS = Set.of(
            Material.SUGAR_CANE, Material.KELP, Material.KELP_PLANT
    );

    // ── NPC right-click ───────────────────────────────────────────────────────

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        // Jokeyrini WanderingTrader
        if (event.getRightClicked() instanceof WanderingTrader trader) {
            String tag = trader.getPersistentDataContainer()
                    .get(JokeyriniManager.getKeyNpc(), PersistentDataType.STRING);
            if ("JOKEYRINI".equals(tag)) {
                event.setCancelled(true);
                if (denyIfNoFaction(player)) return;
                new JokeyriniQuestGui(player).open(player);
            }
            return;
        }

        // Regular job Villager NPC
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        String jobName = villager.getPersistentDataContainer()
                .get(JobManager.getKeyNpcJob(), PersistentDataType.STRING);
        if (jobName == null) return;

        event.setCancelled(true);
        if (denyIfNoFaction(player)) return;

        Job npcJob;
        try { npcJob = Job.valueOf(jobName); }
        catch (IllegalArgumentException e) { return; }
        if (npcJob == Job.JOKEYRINI) return; // guard — not a regular job

        PlayerJobData data = JobManager.getJobData(player.getUniqueId());

        if (data == null) {
            if (JobManager.isOnJobCooldown(player.getUniqueId())) {
                long remaining = JobManager.getJobCooldownRemaining(player.getUniqueId());
                player.sendMessage(Component.text("You recently reset your job. ", NamedTextColor.RED)
                        .append(Component.text("You can select a new job in ", NamedTextColor.GRAY))
                        .append(Component.text(GuiUtil.formatTime(remaining), NamedTextColor.YELLOW))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                return;
            }
            new NpcJobSwitchGui(player, npcJob, false).open(player);
            return;
        }

        if (data.getJob() == npcJob) {
            new JobMainGui(player).open(player);
            return;
        }

        new NpcJobSwitchGui(player, npcJob, true).open(player);
    }

    /**
     * Sends a "must be in a faction" message and returns true if the player has no faction.
     * Caller is responsible for cancelling the event before invoking this.
     */
    static boolean denyIfNoFaction(Player player) {
        if (FactionManager.getPlayerFaction(player.getUniqueId()) != null) return false;
        player.sendMessage(Component.text(
                "You must join a faction before talking to NPCs.", NamedTextColor.RED));
        return true;
    }

    // ── Last-player-damager tracking (handles reflected projectiles) ──────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }
        if (player != null) {
            lastPlayerDamager.put(event.getEntity().getUniqueId(), player.getUniqueId());
        }
    }

    // ── NPC damage protection ─────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.getPersistentDataContainer().has(JobManager.getKeyNpcJob(), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof WanderingTrader trader) {
            if (trader.getPersistentDataContainer().has(JokeyriniManager.getKeyNpc(), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        Material type = event.getBlock().getType();
        String blockName = type.name().toLowerCase();

        if (data != null) {
            boolean isHarvest = data.getJob() == Job.FARMER && FARMER_HARVEST_CROPS.contains(type);
            boolean isBreak = (data.getJob() == Job.MINER && MINER_BLOCKS.contains(type))
                    || (data.getJob() == Job.LUMBERJACK && LUMBERJACK_LOGS.contains(type))
                    || (data.getJob() == Job.FARMER && FARMER_BREAK_CROPS.contains(type));
            if (isHarvest || isBreak) {
                JobManager.addProgress(player.getUniqueId(), 1, player);
                String actionType = isHarvest ? "harvest_crop" : "break_block";
                QuestManager.onTargetGathered(player.getUniqueId(), actionType, blockName, player);
            }
        }

        JokeyriniManager.onTargetGathered(player.getUniqueId(), "break_block", blockName, player);
    }

    // ── Entity kill ───────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID entityUUID = entity.getUniqueId();

        Player killer = entity.getKiller();
        if (killer == null) {
            // Fallback: handle kills from reflected projectiles where getKiller() returns null
            UUID lastDamagerUUID = lastPlayerDamager.remove(entityUUID);
            if (lastDamagerUUID == null) return;
            killer = org.bukkit.Bukkit.getPlayer(lastDamagerUUID);
            if (killer == null) return;
        } else {
            lastPlayerDamager.remove(entityUUID);
        }

        String entityType = entity.getType().name().toLowerCase();
        PlayerJobData data = JobManager.getJobData(killer.getUniqueId());

        // Only HUNTER levels up from kills; ALCHEMIST tracks brewing and crafting only.
        if (data != null && data.getJob() == Job.HUNTER) {
            JobManager.addProgress(killer.getUniqueId(), 1, killer);
            QuestManager.onTargetGathered(killer.getUniqueId(), "kill_entity", entityType, killer);
        }

        JokeyriniManager.onTargetGathered(killer.getUniqueId(), "kill_entity", entityType, killer);
    }

    // ── Brewing stand pickup ──────────────────────────────────────────────────

    @EventHandler
    public void onBrewingPickup(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.BREWING) return;
        if (event.isCancelled()) return;

        int slot = event.getSlot();
        if (slot < 0 || slot > 2) return; // only output bottle slots

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;
        if (!current.getType().name().contains("POTION")) return;

        String target = current.getType().name().toLowerCase();

        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data != null && data.getJob() == Job.ALCHEMIST) {
            JobManager.addProgress(player.getUniqueId(), 1, player);
            QuestManager.onTargetGathered(player.getUniqueId(), "brew_potion", target, player);
        }

        JokeyriniManager.onTargetGathered(player.getUniqueId(), "brew_potion", target, player);
    }

    // ── Crafting table ────────────────────────────────────────────────────────

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.isCancelled()) return;

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() == Material.AIR) return;

        String target = result.getType().name().toLowerCase();
        int amount = getCraftAmount(player, event);

        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data != null && data.getJob() == Job.ALCHEMIST) {
            JobManager.addProgress(player.getUniqueId(), amount, player);
            QuestManager.onTargetGathered(player.getUniqueId(), "craft_item", target, amount, player);
        }

        JokeyriniManager.onTargetGathered(player.getUniqueId(), "craft_item", target, amount, player);
    }

    private static int getCraftAmount(Player player, CraftItemEvent event) {
        int perCraft = event.getRecipe().getResult().getAmount();
        if (!event.isShiftClick()) return perCraft;

        ItemStack[] matrix = event.getInventory().getMatrix();
        Set<Material> seen = new HashSet<>();
        int minCrafts = Integer.MAX_VALUE;

        for (ItemStack slot : matrix) {
            if (slot == null || slot.getType() == Material.AIR) continue;
            Material mat = slot.getType();
            if (!seen.add(mat)) continue;
            // Sum required per craft for this material across all matrix slots
            int reqPerCraft = 0;
            for (ItemStack s : matrix) {
                if (s != null && s.getType() == mat) reqPerCraft += s.getAmount();
            }
            // Count available in player inventory
            int inInventory = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == mat) inInventory += item.getAmount();
            }
            minCrafts = Math.min(minCrafts, (reqPerCraft + inInventory) / reqPerCraft);
        }

        return (minCrafts == Integer.MAX_VALUE ? 1 : minCrafts) * perCraft;
    }

    // ── Harvest interaction (sweet berries, cave vines) ───────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        Player player = event.getPlayer();
        String blockName = type.name().toLowerCase();

        if (type == Material.SWEET_BERRY_BUSH) {
            if (!(block.getBlockData() instanceof Ageable ageable)) return;
            if (ageable.getAge() < 2) return; // age 0-1 have no berries to pick
            PlayerJobData data = JobManager.getJobData(player.getUniqueId());
            if (data != null && data.getJob() == Job.FARMER) {
                JobManager.addProgress(player.getUniqueId(), 1, player);
                QuestManager.onTargetGathered(player.getUniqueId(), "harvest_crop", blockName, player);
            }
            JokeyriniManager.onTargetGathered(player.getUniqueId(), "harvest_crop", blockName, player);
            return;
        }

        if (type == Material.CAVE_VINES_PLANT || type == Material.CAVE_VINES) {
            if (!(block.getBlockData() instanceof CaveVines caveVines)) return;
            if (!caveVines.hasBerries()) return;
            String target = Material.CAVE_VINES_PLANT.name().toLowerCase();
            PlayerJobData data = JobManager.getJobData(player.getUniqueId());
            if (data != null && data.getJob() == Job.FARMER) {
                JobManager.addProgress(player.getUniqueId(), 1, player);
                QuestManager.onTargetGathered(player.getUniqueId(), "harvest_crop", target, player);
            }
            JokeyriniManager.onTargetGathered(player.getUniqueId(), "harvest_crop", target, player);
        }
    }
}
