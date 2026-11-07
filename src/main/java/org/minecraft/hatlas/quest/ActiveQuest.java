package org.minecraft.hatlas.quest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A quest currently in progress for a player, with all generated task data baked in. */
public class ActiveQuest {

    private final String              questId;
    private final List<GeneratedTask> tasks;
    private final long                expiresAt;
    private final Map<String, Integer> taskProgress = new HashMap<>();

    public ActiveQuest(String questId, List<GeneratedTask> tasks, long expiresAt) {
        this.questId   = questId;
        this.tasks     = List.copyOf(tasks);
        this.expiresAt = expiresAt;
        for (GeneratedTask t : tasks) taskProgress.put(t.getTaskId(), 0);
    }

    public ActiveQuest(String questId, List<GeneratedTask> tasks, long expiresAt,
                       Map<String, Integer> savedProgress) {
        this(questId, tasks, expiresAt);
        taskProgress.putAll(savedProgress);
    }

    public String              getQuestId()               { return questId; }
    public List<GeneratedTask> getTasks()                  { return tasks; }
    public long                getExpiresAt()             { return expiresAt; }
    public boolean             isExpired()                { return System.currentTimeMillis() > expiresAt; }
    public int                 getTaskProgress(String id) { return taskProgress.getOrDefault(id, 0); }
    public Map<String, Integer> getTaskProgressMap()      { return Collections.unmodifiableMap(taskProgress); }

    /** Increments gather count for the given task and returns the new value. */
    public int incrementTaskProgress(String taskId) {
        return taskProgress.merge(taskId, 1, Integer::sum);
    }

    /**
     * Adds the given amount to task progress and returns the new value.
     */
    public int addTaskProgress(String taskId, int amount) {
        return taskProgress.merge(taskId, amount, Integer::sum);
    }
}
