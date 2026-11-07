package org.minecraft.hatlas.quest;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A dynamically generated quest composed of one or more {@link GeneratedTask}s.
 * All scaling (difficulty, amount, rewards, time) is baked into the tasks.
 */
public class QuestTemplate {

    private final String              id;
    private final List<GeneratedTask> tasks;
    private final long                timeLimitMs;

    public QuestTemplate(String id, List<GeneratedTask> tasks) {
        this.id          = id;
        this.tasks       = List.copyOf(tasks);
        long sum = tasks.stream().mapToLong(GeneratedTask::getTimeLimitMs).sum();
        this.timeLimitMs = sum > 0 ? sum : 3_600_000L;
    }

    public String              getId()          { return id; }
    public List<GeneratedTask> getTasks()       { return tasks; }
    public long                getTimeLimitMs() { return timeLimitMs; }

    /** Aggregated scaled rewards from all tasks. */
    public List<ItemStack> getItemRewards() {
        List<ItemStack> all = new ArrayList<>();
        for (GeneratedTask t : tasks) all.addAll(t.getRewards());
        return all;
    }

    /** Sum of difficulty values across all tasks (used in faction XP formula). */
    public int getSumDifficulty() {
        return tasks.stream().mapToInt(GeneratedTask::getDifficulty).sum();
    }

    /**
     * Faction XP awarded on completion. Each task contributes {@code difficulty *
     * expMultiplier}; the multiplier is configured per difficulty on the task template.
     * Formula: baseExpReward * sum(difficulty_i * expMultiplier_i) * sqrt(factionLevel + 1)
     */
    public int calculateExpReward(int factionLevel, int baseExpReward) {
        double weighted = tasks.stream()
                .mapToDouble(t -> t.getDifficulty() * t.getExpMultiplier())
                .sum();
        return (int) (baseExpReward * weighted * Math.sqrt(factionLevel + 1));
    }
}
