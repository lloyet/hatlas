package org.minecraft.hatlas.quest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.job.Job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One task inside a generated daily quest.
 * Difficulty (1–5) is assigned at generation time; amount, time limit,
 * and item rewards are all derived from difficulty × player job level.
 * Difficulty 5 (Legendary) is exclusive to the Jokeyrini special quest.
 */
public class GeneratedTask {

    // ── Difficulty → gather amount formula ────────────────────────────────────
    // amount = BASE[d] + floor(jobLevel * SCALE[d])
    private static final int[]    AMOUNT_BASE      = {0,  5, 10, 20,  35,  60};
    private static final double[] AMOUNT_PER_LEVEL = {0, 0.5, 1.0, 2.0, 3.5, 5.0};

    // ── Difficulty → time limit (ms) ──────────────────────────────────────────
    private static final long[] TIME_LIMIT_MS = {
        0L,
        15L * 60_000,   // Easy      → 15 min
        30L * 60_000,   // Normal    → 30 min
        60L * 60_000,   // Hard      → 60 min
       120L * 60_000,   // Hardcore  → 120 min
       180L * 60_000    // Legendary → 180 min
    };

    // ── Per-job multipliers ───────────────────────────────────────────────────
    // Hunter kills fewer targets (10% less) but receives 10% more rewards.
    private static double getAmountMultiplier(Job job) {
        if (job == Job.HUNTER) return 0.9;
        return 1.0;
    }
    private static double getRewardMultiplier(Job job) {
        if (job == Job.HUNTER) return 1.1;
        return 1.0;
    }

    // ── Item reward cap per difficulty ────────────────────────────────────────
    // cap   = ceiling fraction of gatherAmount; base = cap/2 at level 0 → full cap at level 20
    private static final double[] REWARD_CAP  = {0, 0.30, 0.30, 0.45, 0.60, 0.75};
    private static final double[] REWARD_BASE = {0, 0.15, 0.15, 0.225, 0.30, 0.375};

    // ─────────────────────────────────────────────────────────────────────────

    private final String       taskId;
    private final String       name;
    private final String       description;
    private final String       actionType;
    private final List<String> targets;
    private final int          difficulty;
    private final int          amount;
    private final long         timeLimitMs;
    private final List<ItemStack> rewards;
    private final double       expMultiplier;

    public GeneratedTask(String taskId, String name, String description, String actionType,
                         List<String> targets, int difficulty, int amount,
                         long timeLimitMs, List<ItemStack> rewards, double expMultiplier) {
        this.taskId        = taskId;
        this.name          = name;
        this.description   = description;
        this.actionType    = actionType;
        this.targets       = List.copyOf(targets);
        this.difficulty    = difficulty;
        this.amount        = amount;
        this.timeLimitMs   = timeLimitMs;
        this.rewards       = List.copyOf(rewards);
        this.expMultiplier = expMultiplier;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String          getTaskId()        { return taskId; }
    public String          getName()          { return name; }
    public String          getDescription()   { return description; }
    public String          getActionType()    { return actionType; }
    public List<String>    getTargets()       { return targets; }
    public int             getDifficulty()    { return difficulty; }
    public int             getAmount()        { return amount; }
    public long            getTimeLimitMs()   { return timeLimitMs; }
    public List<ItemStack> getRewards()       { return rewards; }
    public double          getExpMultiplier() { return expMultiplier; }

    // ── Static factories ──────────────────────────────────────────────────────

    /**
     * Generates a task from a template with a randomly chosen difficulty (1–4)
     * and values scaled to the player's current job level.
     */
    public static GeneratedTask generate(TaskTemplate template, int difficulty, int jobLevel) {
        int d = Math.max(1, Math.min(5, difficulty));
        Job job = template.getJob();
        int rawAmount = AMOUNT_BASE[d] + (int)(jobLevel * AMOUNT_PER_LEVEL[d]);
        int amount    = Math.max(1, (int)(rawAmount * getAmountMultiplier(job)));
        long timeLimit = TIME_LIMIT_MS[d];
        List<ItemStack> scaled = scaleRewards(template.getBaseItemRewards(), rawAmount, jobLevel, d, getRewardMultiplier(job));
        double expMult = template.getExpMultiplier().forDifficulty(d);
        return new GeneratedTask(
            template.getId(), template.getName(), template.getDescription(),
            template.getActionType(), template.getTargets(),
            d, amount, timeLimit, scaled, expMult
        );
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task_id",      taskId);
        map.put("name",         name);
        map.put("description",  description);
        map.put("action_type",  actionType);
        map.put("targets",      new ArrayList<>(targets));
        map.put("difficulty",   difficulty);
        map.put("amount",       amount);
        map.put("time_limit_ms", timeLimitMs);
        map.put("exp_multiplier", expMultiplier);

        List<Map<String, Object>> rewardList = new ArrayList<>();
        for (ItemStack r : rewards) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("material", r.getType().name());
            rm.put("amount",   r.getAmount());
            rewardList.add(rm);
        }
        map.put("rewards", rewardList);
        return map;
    }

    public static GeneratedTask deserialize(Map<?, ?> map) {
        try {
            String taskId      = map.get("task_id").toString();
            String name        = map.get("name").toString();
            String description = map.get("description") != null ? map.get("description").toString() : "";
            String actionType  = map.get("action_type").toString();
            int    difficulty  = ((Number) map.get("difficulty")).intValue();
            int    amount      = ((Number) map.get("amount")).intValue();
            long   timeLimit   = ((Number) map.get("time_limit_ms")).longValue();
            double expMult     = map.get("exp_multiplier") instanceof Number n ? n.doubleValue() : 1.0;

            List<String> targets = new ArrayList<>();
            Object tObj = map.get("targets");
            if (tObj instanceof List<?> tList) {
                for (Object t : tList) if (t != null) targets.add(t.toString());
            }

            List<ItemStack> rewards = new ArrayList<>();
            Object rObj = map.get("rewards");
            if (rObj instanceof List<?> rList) {
                for (Object rItem : rList) {
                    if (!(rItem instanceof Map<?, ?> rMap)) continue;
                    Object matObj = rMap.get("material");
                    if (matObj == null) continue;
                    Material mat = Material.matchMaterial(matObj.toString());
                    if (mat == null) continue;
                    int amt = rMap.get("amount") instanceof Number n ? n.intValue() : 1;
                    rewards.add(new ItemStack(mat, amt));
                }
            }

            return new GeneratedTask(taskId, name, description, actionType, targets,
                    difficulty, amount, timeLimit, rewards, expMult);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static List<ItemStack> scaleRewards(List<ItemStack> base, int rawAmount, int jobLevel, int difficulty, double rewardMult) {
        int cap    = Math.max(1, (int)(REWARD_CAP[difficulty]  * rawAmount * rewardMult));
        int scaled = Math.min(cap, Math.max(1, (int)(REWARD_BASE[difficulty] * rawAmount * (1.0 + jobLevel / 20.0) * rewardMult)));
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack b : base) result.add(new ItemStack(b.getType(), scaled));
        return result;
    }
}
