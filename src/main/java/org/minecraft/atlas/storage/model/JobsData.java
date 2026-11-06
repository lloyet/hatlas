package org.minecraft.atlas.storage.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Domain JOBS: player jobs, daily quests, job-switch cooldowns and Jokeyrini quests. */
public record JobsData(List<PlayerJob> players, Map<UUID, Long> cooldowns, List<Jokeyrini> jokeyrini) {

    public static JobsData empty() { return new JobsData(List.of(), Map.of(), List.of()); }

    public record PlayerJob(UUID uuid, String job, int level, int progress, long lastDailyReset,
                            List<Quest> active, List<Quest> offered, List<String> selected) {}

    /** @param lastDailyReset 0 = never; @param active null = none */
    public record Jokeyrini(UUID uuid, long lastDailyReset, List<Task> offer, Quest active) {}

    /** @param expiresAt 0 for offered (not yet accepted) quests */
    public record Quest(String questId, long expiresAt, List<Task> tasks, Map<String, Integer> progress) {}

    public record Task(String taskId, String name, String description, String actionType,
                       List<String> targets, int difficulty, int amount, long timeLimitMs,
                       double expMultiplier, List<Reward> rewards) {}

    public record Reward(String material, int amount) {}
}
