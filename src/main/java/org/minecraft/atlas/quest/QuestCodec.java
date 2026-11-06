package org.minecraft.atlas.quest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.storage.model.JobsData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts quests and tasks to and from their storage records. */
public final class QuestCodec {

    private QuestCodec() {}

    public static JobsData.Task toData(GeneratedTask t) {
        List<JobsData.Reward> rewards = new ArrayList<>();
        for (ItemStack r : t.getRewards()) rewards.add(new JobsData.Reward(r.getType().name(), r.getAmount()));
        return new JobsData.Task(t.getTaskId(), t.getName(), t.getDescription(), t.getActionType(),
                List.copyOf(t.getTargets()), t.getDifficulty(), t.getAmount(), t.getTimeLimitMs(),
                t.getExpMultiplier(), List.copyOf(rewards));
    }

    public static GeneratedTask fromData(JobsData.Task t) {
        List<ItemStack> rewards = new ArrayList<>();
        for (JobsData.Reward r : t.rewards()) {
            Material mat = Material.matchMaterial(r.material());
            if (mat != null) rewards.add(new ItemStack(mat, Math.max(1, r.amount())));
        }
        return new GeneratedTask(t.taskId(), t.name(), t.description(), t.actionType(),
                new ArrayList<>(t.targets()), t.difficulty(), t.amount(), t.timeLimitMs(), rewards, t.expMultiplier());
    }

    public static List<JobsData.Task> toData(List<GeneratedTask> tasks) {
        List<JobsData.Task> out = new ArrayList<>();
        for (GeneratedTask t : tasks) out.add(toData(t));
        return List.copyOf(out);
    }

    public static List<GeneratedTask> fromData(List<JobsData.Task> tasks) {
        List<GeneratedTask> out = new ArrayList<>();
        for (JobsData.Task t : tasks) out.add(fromData(t));
        return out;
    }

    public static JobsData.Quest toData(ActiveQuest q) {
        return new JobsData.Quest(q.getQuestId(), q.getExpiresAt(), toData(q.getTasks()), Map.copyOf(q.getTaskProgressMap()));
    }

    /** Returns null for an expired quest or one without tasks. */
    public static ActiveQuest fromData(JobsData.Quest q, long now) {
        if (q == null || q.expiresAt() <= now) return null;
        List<GeneratedTask> tasks = fromData(q.tasks());
        if (tasks.isEmpty()) return null;
        return new ActiveQuest(q.questId(), tasks, q.expiresAt(), new HashMap<>(q.progress()));
    }
}
