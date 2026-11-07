package org.minecraft.hatlas.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.job.Job;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.job.PlayerJobData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class QuestManager {

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<Job, List<TaskTemplate>> taskTemplates = new EnumMap<>(Job.class);
    private static int  baseExpReward       = 10;
    private static long questResetIntervalMs = 24 * 3_600_000L;

    // ── Task template access ──────────────────────────────────────────────────

    public static List<TaskTemplate> getTasksForJob(Job job) {
        return taskTemplates.getOrDefault(job, List.of());
    }

    public static TaskTemplate getTaskTemplate(String taskId) {
        for (List<TaskTemplate> tasks : taskTemplates.values()) {
            for (TaskTemplate t : tasks) {
                if (t.getId().equals(taskId)) return t;
            }
        }
        return null;
    }

    public static int getBaseExpReward() { return baseExpReward; }

    // ── Active quest management ───────────────────────────────────────────────

    public static List<ActiveQuest> getActiveQuests(UUID uuid) {
        PlayerJobData data = JobManager.getJobData(uuid);
        return data == null ? List.of() : List.copyOf(data.activeQuests);
    }

    /** Reconstructs a QuestTemplate directly from the tasks stored in an ActiveQuest. */
    public static QuestTemplate resolveQuest(ActiveQuest aq) {
        List<GeneratedTask> tasks = aq.getTasks();
        return tasks.isEmpty() ? null : new QuestTemplate(aq.getQuestId(), tasks);
    }

    private static boolean addActiveQuest(UUID uuid, String questId,
                                          List<GeneratedTask> tasks, long timeLimitMs) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null || data.activeQuests.size() >= 2) return false;
        for (ActiveQuest aq : data.activeQuests) {
            if (aq.getQuestId().equals(questId)) return false;
        }
        data.activeQuests.add(new ActiveQuest(questId, tasks,
                System.currentTimeMillis() + timeLimitMs));
        return true;
    }

    public static boolean completeQuest(UUID uuid, String questId, Player player) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) return false;

        ActiveQuest found = null;
        for (ActiveQuest aq : data.activeQuests) {
            if (aq.getQuestId().equals(questId) && !aq.isExpired()) { found = aq; break; }
        }
        if (found == null) return false;

        data.activeQuests.remove(found);

        QuestTemplate qt = resolveQuest(found);
        if (qt == null) return true;

        String factionName = FactionManager.getPlayerFaction(uuid);
        if (factionName != null) {
            Faction faction = FactionManager.getFaction(factionName);
            int factionLevel = faction != null ? faction.getLevel() : 0;
            int expReward = qt.calculateExpReward(factionLevel, baseExpReward);
            FactionManager.addExpToFaction(factionName, expReward);
        }

        for (ItemStack reward : qt.getItemRewards()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }

        return true;
    }

    // ── Task progress tracking ────────────────────────────────────────────────

    public static void onTargetGathered(UUID playerUUID, String actionType,
                                        String targetName, Player player) {
        onTargetGathered(playerUUID, actionType, targetName, 1, player);
    }

    public static void onTargetGathered(UUID playerUUID, String actionType,
                                        String targetName, int amount, Player player) {
        PlayerJobData data = JobManager.getJobData(playerUUID);
        if (data == null || data.activeQuests.isEmpty()) return;

        List<ActiveQuest> toComplete = new ArrayList<>();

        for (ActiveQuest aq : data.activeQuests) {
            if (aq.isExpired()) continue;

            for (GeneratedTask task : aq.getTasks()) {
                if (!task.getActionType().equalsIgnoreCase(actionType)) continue;
                boolean matches = false;
                for (String t : task.getTargets()) {
                    if (t.equalsIgnoreCase(targetName)) { matches = true; break; }
                }
                if (!matches) continue;

                int current = aq.addTaskProgress(task.getTaskId(), amount);
                int required = task.getAmount();
                if (current <= required) {
                    player.sendActionBar(Component.text(
                            "[" + task.getName() + "] " + current + "/" + required,
                            current >= required ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }

            boolean allDone = aq.getTasks().stream()
                    .allMatch(t -> aq.getTaskProgress(t.getTaskId()) >= t.getAmount());
            if (allDone) toComplete.add(aq);
        }

        for (ActiveQuest aq : toComplete) {
            if (completeQuest(playerUUID, aq.getQuestId(), player)) {
                player.sendMessage(Component.text("✔ Quest completed! Rewards granted.", NamedTextColor.GREEN));
            }
        }
    }

    // ── Daily quest management ────────────────────────────────────────────────

    public static List<QuestTemplate> getDailyOfferedQuests(UUID uuid) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) return List.of();

        long now = System.currentTimeMillis();
        boolean needsReset = data.lastDailyResetMs == 0
                || (now - data.lastDailyResetMs) >= questResetIntervalMs
                || data.dailyOfferedQuests.isEmpty();
        if (needsReset) {
            data.dailyOfferedQuests.clear();
            data.dailySelectedIds.clear();
            data.activeQuests.clear();
            data.lastDailyResetMs = now;
            generateDailyQuests(data);
        }

        List<QuestTemplate> result = new ArrayList<>();
        for (Map.Entry<String, List<GeneratedTask>> entry : data.dailyOfferedQuests.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new QuestTemplate(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    public static boolean isDailyLocked(UUID uuid) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) return false;
        long now = System.currentTimeMillis();
        if (data.lastDailyResetMs == 0 || (now - data.lastDailyResetMs) >= questResetIntervalMs) return false;
        return data.dailySelectedIds.size() >= 2;
    }

    public static int getDailySelectedCount(UUID uuid) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) return 0;
        long now = System.currentTimeMillis();
        if (data.lastDailyResetMs == 0 || (now - data.lastDailyResetMs) >= questResetIntervalMs) return 0;
        return data.dailySelectedIds.size();
    }

    public static boolean isDailyQuestSelected(UUID uuid, String questId) {
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null) return false;
        long now = System.currentTimeMillis();
        if (data.lastDailyResetMs == 0 || (now - data.lastDailyResetMs) >= questResetIntervalMs) return false;
        return data.dailySelectedIds.contains(questId);
    }

    public static boolean selectDailyQuest(UUID uuid, String questId) {
        if (isDailyLocked(uuid)) return false;
        PlayerJobData data = JobManager.getJobData(uuid);
        if (data == null || data.dailySelectedIds.contains(questId)) return false;

        List<GeneratedTask> tasks = data.dailyOfferedQuests.get(questId);
        if (tasks == null || tasks.isEmpty()) return false;

        long timeLimitMs = tasks.stream().mapToLong(GeneratedTask::getTimeLimitMs).sum();
        if (timeLimitMs == 0) timeLimitMs = 3_600_000L;

        if (!addActiveQuest(uuid, questId, tasks, timeLimitMs)) return false;
        data.dailySelectedIds.add(questId);
        return true;
    }

    // ── Quest generation ──────────────────────────────────────────────────────

    private static void generateDailyQuests(PlayerJobData data) {
        List<TaskTemplate> pool = new ArrayList<>(getTasksForJob(data.getJob()));
        if (pool.isEmpty()) return;

        int jobLevel = data.getLevel();

        for (int q = 0; q < 3; q++) {
            List<TaskTemplate> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            int taskCount = Math.min(1 + ThreadLocalRandom.current().nextInt(2), shuffled.size());

            List<GeneratedTask> generated = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                int difficulty = ThreadLocalRandom.current().nextInt(1, 5); // 1–4
                generated.add(GeneratedTask.generate(shuffled.get(i), difficulty, jobLevel));
            }

            data.dailyOfferedQuests.put(UUID.randomUUID().toString(), generated);
        }
    }

    // ── Expiry scheduler ──────────────────────────────────────────────────────

    public static void scheduleExpiry(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerJobData data : JobManager.getAllJobData()) {
                    data.activeQuests.removeIf(ActiveQuest::isExpired);
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    // ── Config loading ────────────────────────────────────────────────────────

    public static void loadQuestConfig(FileConfiguration config) {
        taskTemplates.clear();

        ConfigurationSection questSection = config.getConfigurationSection("quests");
        baseExpReward        = questSection != null ? questSection.getInt("exp_base_reward", 10) : 10;
        double resetHours    = questSection != null ? questSection.getDouble("daily_reset_hours", 24.0) : 24.0;
        questResetIntervalMs = (long)(resetHours * 3_600_000L);

        List<?> taskList = questSection != null ? questSection.getList("tasks") : null;
        if (taskList != null) {
            for (Object obj : taskList) {
                if (!(obj instanceof Map<?, ?> tMap)) continue;
                String id      = mapStr(tMap, "id");
                String name    = mapStr(tMap, "name");
                String desc    = mapStr(tMap, "description");
                String jobName = mapStr(tMap, "job");
                String action  = mapStr(tMap, "action_type");
                if (id == null || name == null || desc == null || jobName == null || action == null) continue;

                Job job;
                try { job = Job.valueOf(jobName.toUpperCase()); }
                catch (IllegalArgumentException e) { continue; }

                List<String> targets = new ArrayList<>();
                Object targetsObj = tMap.get("targets");
                if (targetsObj instanceof List<?> tList) {
                    for (Object t : tList) if (t != null) targets.add(t.toString());
                }

                List<ItemStack> baseRewards = new ArrayList<>();
                Object rewardsObj = tMap.get("item_rewards");
                if (rewardsObj instanceof List<?> rList) {
                    for (Object rObj : rList) {
                        if (!(rObj instanceof Map<?, ?> rMap)) continue;
                        String matStr = mapStr(rMap, "material");
                        if (matStr == null) continue;
                        Material mat = Material.matchMaterial(matStr);
                        if (mat == null) continue;
                        baseRewards.add(new ItemStack(mat, mapInt(rMap, "amount", 1)));
                    }
                }

                TaskTemplate.ExpMultiplier expMult = parseExpMultiplier(expMultiplierSection(tMap));

                taskTemplates.computeIfAbsent(job, k -> new ArrayList<>())
                        .add(new TaskTemplate(id, name, desc, job, action, targets, baseRewards, expMult));
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static String mapStr(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int mapInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    /**
     * Returns a task's per-difficulty exp multiplier section. jobs.yml uses
     * {@code multiplier_exp}; the legacy {@code exp_multiplier} key is still accepted.
     */
    public static Object expMultiplierSection(Map<?, ?> taskMap) {
        Object raw = taskMap.get("multiplier_exp");
        return raw != null ? raw : taskMap.get("exp_multiplier");
    }

    public static TaskTemplate.ExpMultiplier parseExpMultiplier(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return TaskTemplate.ExpMultiplier.defaults();
        return new TaskTemplate.ExpMultiplier(
                mapDouble(m, "easy",     1.0),
                mapDouble(m, "normal",   1.0),
                mapDouble(m, "hard",     1.0),
                mapDouble(m, "hardcore", 1.0)
        );
    }

    private static double mapDouble(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}
