package org.minecraft.hatlas.quest;

import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.job.Job;

import java.util.List;

/**
 * Static task definition loaded from config.
 * Difficulty, amount, and time limits are generated dynamically per quest — not stored here.
 */
public class TaskTemplate {

    /**
     * Per-difficulty XP reward multiplier applied when a task of this template is
     * completed. Difficulties 1–4 map to easy / normal / hard / hardcore; any other
     * difficulty (e.g. 5/legendary) falls back to 1.0.
     */
    public record ExpMultiplier(double easy, double normal, double hard, double hardcore) {
        public static ExpMultiplier defaults() {
            return new ExpMultiplier(1.0, 1.0, 1.0, 1.0);
        }
        public double forDifficulty(int difficulty) {
            return switch (difficulty) {
                case 1 -> easy;
                case 2 -> normal;
                case 3 -> hard;
                case 4 -> hardcore;
                default -> 1.0;
            };
        }
    }

    private final String          id;
    private final String          name;
    private final String          description;
    private final Job             job;
    private final String          actionType;       // e.g. "break_block", "kill_entity"
    private final List<String>    targets;          // block/entity names that count
    private final List<ItemStack> baseItemRewards;  // base amounts, scaled at generation time
    private final ExpMultiplier   expMultiplier;

    public TaskTemplate(String id, String name, String description, Job job,
                        String actionType, List<String> targets, List<ItemStack> baseItemRewards,
                        ExpMultiplier expMultiplier) {
        this.id              = id;
        this.name            = name;
        this.description     = description;
        this.job             = job;
        this.actionType      = actionType;
        this.targets         = List.copyOf(targets);
        this.baseItemRewards = List.copyOf(baseItemRewards);
        this.expMultiplier   = expMultiplier != null ? expMultiplier : ExpMultiplier.defaults();
    }

    public String          getId()              { return id; }
    public String          getName()            { return name; }
    public String          getDescription()     { return description; }
    public Job             getJob()             { return job; }
    public String          getActionType()      { return actionType; }
    public List<String>    getTargets()         { return targets; }
    public List<ItemStack> getBaseItemRewards() { return baseItemRewards; }
    public ExpMultiplier   getExpMultiplier()   { return expMultiplier; }
}
