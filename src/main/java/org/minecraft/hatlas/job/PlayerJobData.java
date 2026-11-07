package org.minecraft.hatlas.job;

import org.minecraft.hatlas.quest.ActiveQuest;
import org.minecraft.hatlas.quest.GeneratedTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerJobData {

    // ── Job core ──────────────────────────────────────────────────────────────
    private final Job job;
    private int level;
    private int progress;

    // ── Quest state ───────────────────────────────────────────────────────────
    /** Quests currently being worked on (max 2). */
    public final List<ActiveQuest> activeQuests = new ArrayList<>();

    /** System.currentTimeMillis() when the last daily offer was generated (0 = never). */
    public long lastDailyResetMs = 0;

    /**
     * Maps generated questId → list of GeneratedTasks for today's offered quests.
     * Insertion order is preserved so the GUI slot order stays consistent.
     */
    public final Map<String, List<GeneratedTask>> dailyOfferedQuests = new LinkedHashMap<>();

    /** IDs of quests the player selected today (max 2). */
    public final List<String> dailySelectedIds = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public PlayerJobData(Job job) {
        this.job      = job;
        this.level    = 1;
        this.progress = 0;
    }

    public PlayerJobData(Job job, int level, int progress) {
        this.job      = job;
        this.level    = level;
        this.progress = progress;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Job  getJob()      { return job; }
    public int  getLevel()    { return level; }
    public int  getProgress() { return progress; }

    public void setLevel(int level)       { this.level    = Math.max(1, level); }
    public void setProgress(int progress) { this.progress = Math.max(0, progress); }

    /** Progress required to advance from the current level to the next. */
    public int getProgressRequired() {
        return (level + 1) * 64;
    }

    /**
     * Adds progress toward the next level.
     * @return true if the player levelled up as a result.
     */
    public boolean addProgress(int amount) {
        progress += amount;
        if (progress >= getProgressRequired()) {
            progress -= getProgressRequired();
            level++;
            return true;
        }
        return false;
    }
}
