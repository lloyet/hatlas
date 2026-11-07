package org.minecraft.hatlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.quest.ActiveQuest;
import org.minecraft.hatlas.quest.GeneratedTask;
import org.minecraft.hatlas.quest.QuestCodec;
import org.minecraft.hatlas.storage.model.JobsData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    // ── PDC key (stored on Villager entities) ─────────────────────────────────

    private static NamespacedKey KEY_NPC_JOB;

    public static NamespacedKey getKeyNpcJob() {
        if (KEY_NPC_JOB == null)
            KEY_NPC_JOB = new NamespacedKey(Hatlas.instance, "job_npc_type");
        return KEY_NPC_JOB;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<UUID, PlayerJobData> playerJobs   = new HashMap<>();
    private static final Map<UUID, Long>          jobCooldowns = new HashMap<>();

    private static final long JOB_RESET_COOLDOWN_MS = 24L * 3_600_000L;

    // ── Job reset cooldown ────────────────────────────────────────────────────

    public static void startJobResetCooldown(UUID uuid) {
        jobCooldowns.put(uuid, System.currentTimeMillis() + JOB_RESET_COOLDOWN_MS);
    }

    public static boolean isOnJobCooldown(UUID uuid) {
        Long until = jobCooldowns.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) { jobCooldowns.remove(uuid); return false; }
        return true;
    }

    public static long getJobCooldownRemaining(UUID uuid) {
        Long until = jobCooldowns.get(uuid);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    // ── Player job queries ────────────────────────────────────────────────────

    public static boolean hasJob(UUID playerUUID) {
        return playerJobs.containsKey(playerUUID);
    }

    public static PlayerJobData getJobData(UUID playerUUID) {
        return playerJobs.get(playerUUID);
    }

    public static boolean setJob(UUID playerUUID, Job job) {
        if (playerJobs.containsKey(playerUUID)) return false;
        playerJobs.put(playerUUID, new PlayerJobData(job));
        return true;
    }

    public static boolean removeJob(UUID playerUUID) {
        return playerJobs.remove(playerUUID) != null;
    }

    public static void forceSetJob(UUID playerUUID, Job job) {
        playerJobs.put(playerUUID, new PlayerJobData(job));
    }

    // ── Collection access ─────────────────────────────────────────────────────

    public static Collection<PlayerJobData> getAllJobData() { return playerJobs.values(); }

    // ── Admin level / XP helpers ──────────────────────────────────────────────

    public static boolean adminSetLevel(UUID uuid, int level) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(level); return true;
    }
    public static boolean adminAddLevel(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(d.getLevel() + amount); return true;
    }
    public static boolean adminRemoveLevel(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(d.getLevel() - amount); return true;
    }
    public static boolean adminSetXp(UUID uuid, int xp) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setProgress(xp); return true;
    }
    public static boolean adminAddXp(UUID uuid, int amount, Player player) {
        return addProgress(uuid, amount, player);
    }
    public static boolean adminRemoveXp(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setProgress(d.getProgress() - amount); return true;
    }

    public static boolean addProgress(UUID playerUUID, int amount, Player player) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        if (data.addProgress(amount)) {
            player.sendMessage(
                Component.text("You leveled up your ", NamedTextColor.GOLD)
                    .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                    .append(Component.text(" job to level " + data.getLevel() + "!", NamedTextColor.GOLD)));
            return true;
        }
        return false;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Jobs, quests, cooldowns and Jokeyrini data, for storage. Main thread only. */
    public static JobsData snapshot() {
        long now = System.currentTimeMillis();
        Map<UUID, Long> cooldowns = new HashMap<>();
        jobCooldowns.forEach((u, until) -> {
            if (until > now) cooldowns.put(u, until);
        });
        List<JobsData.PlayerJob> players = new ArrayList<>();
        for (Map.Entry<UUID, PlayerJobData> e : playerJobs.entrySet()) {
            PlayerJobData d = e.getValue();
            List<JobsData.Quest> active = new ArrayList<>();
            for (ActiveQuest q : d.activeQuests) active.add(QuestCodec.toData(q));
            List<JobsData.Quest> offered = new ArrayList<>();
            d.dailyOfferedQuests.forEach((id, tasks) -> offered.add(new JobsData.Quest(id, 0L, QuestCodec.toData(tasks), Map.of())));
            players.add(new JobsData.PlayerJob(e.getKey(), d.getJob().name(), d.getLevel(), d.getProgress(),
                    d.lastDailyResetMs, List.copyOf(active), List.copyOf(offered), List.copyOf(d.dailySelectedIds)));
        }
        return new JobsData(List.copyOf(players), Map.copyOf(cooldowns), JokeyriniManager.snapshot());
    }

    /** Restores jobs and Jokeyrini data. Expired quests and cooldowns are dropped. */
    public static void restore(JobsData data) {
        playerJobs.clear();
        jobCooldowns.clear();
        long now = System.currentTimeMillis();
        data.cooldowns().forEach((u, until) -> {
            if (until > now) jobCooldowns.put(u, until);
        });
        for (JobsData.PlayerJob p : data.players()) {
            Job job;
            try {
                job = Job.valueOf(p.job());
            } catch (IllegalArgumentException e) {
                continue;
            }
            PlayerJobData d = new PlayerJobData(job, p.level(), p.progress());
            for (JobsData.Quest q : p.active()) {
                ActiveQuest aq = QuestCodec.fromData(q, now);
                if (aq != null) d.activeQuests.add(aq);
            }
            d.lastDailyResetMs = p.lastDailyReset();
            for (JobsData.Quest q : p.offered()) {
                List<GeneratedTask> tasks = QuestCodec.fromData(q.tasks());
                if (!tasks.isEmpty()) d.dailyOfferedQuests.put(q.questId(), tasks);
            }
            d.dailySelectedIds.addAll(p.selected());
            playerJobs.put(p.uuid(), d);
        }
        JokeyriniManager.restore(data.jokeyrini());
    }
}
