package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.hatlas.job.Job;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.job.JokeyriniManager;
import org.minecraft.hatlas.job.PlayerJobData;

import java.util.Arrays;
import java.util.stream.Collectors;

public class JobCommand {

    private static Component error(String msg) { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg) { return Component.text(msg, NamedTextColor.GOLD); }

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/job ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("job")
                .requires(src -> src.getSender().hasPermission("hatlas.job"))
                // /job — show help
                .executes(ctx -> {
                    var sender = ctx.getSource().getSender();
                    Component help = Component.text("--- Job Commands ---", NamedTextColor.GOLD)
                            .append(Component.newline()).append(helpEntry("level", "", "View your current job level and progress"));

                    if (sender.hasPermission("hatlas.job.admin")) {
                        help = help
                                .append(Component.newline()).append(helpEntry("set", "<job> [player]", "[admin] Force-set a job"))
                                .append(Component.newline()).append(helpEntry("remove", "[player]", "[admin] Remove a player's job"))
                                .append(Component.newline()).append(helpEntry("level", "add|set|remove <player> <amount> xp|level", "[admin] Modify job level/xp"))
                                .append(Component.newline()).append(helpEntry("npc", "spawn <job>", "[admin] Spawn a job NPC"));
                    }

                    sender.sendMessage(help);
                    return Command.SINGLE_SUCCESS;
                })
                // /job remove [player]
                .then(Commands.literal("remove")
                        .requires(src -> src.getSender().hasPermission("hatlas.job.admin"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            doRemove(player, player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();
                                    doRemove(player, target);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // /job set <job> [player]
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission("hatlas.job.admin"))
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Job j : Job.values()) builder.suggest(j.name().toLowerCase());
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                    if (job == null) return Command.SINGLE_SUCCESS;
                                    doAdminSet(player, player, job);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                            if (job == null) return Command.SINGLE_SUCCESS;
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doAdminSet(player, target, job);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /job npc spawn <job>
                .then(Commands.literal("npc")
                        .requires(src -> src.getSender().hasPermission("hatlas.job.admin"))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Job j : Job.values()) builder.suggest(j.name().toLowerCase());
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                            if (job == null) return Command.SINGLE_SUCCESS;
                                            doSpawnNpc(player, job);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /job level [add|set|remove <player> <amount> xp|level]
                .then(Commands.literal("level")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            showLevel(player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(levelAdminBranch("add"))
                        .then(levelAdminBranch("set"))
                        .then(levelAdminBranch("remove")))

                .build();
    }

    /**
     * Builds the add/set/remove branch under /job level.
     * Structure: /job level <operation> <player> <amount> (xp|level)
     */
    private static LiteralArgumentBuilder<CommandSourceStack> levelAdminBranch(String operation) {
        return Commands.literal(operation)
                .requires(src -> src.getSender().hasPermission("hatlas.job.admin"))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .then(Commands.literal("xp")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doLevelOp(player, target, operation, "xp", IntegerArgumentType.getInteger(ctx, "amount"));
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("level")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doLevelOp(player, target, operation, "level", IntegerArgumentType.getInteger(ctx, "amount"));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    // -------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------

    private static Job parseJob(Player executor, String input) {
        try {
            return Job.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(Job.values())
                    .map(j -> j.name().toLowerCase())
                    .collect(Collectors.joining(", "));
            executor.sendMessage(error("Unknown job '" + input + "'. Valid jobs: " + valid + "."));
            return null;
        }
    }

    private static void doSpawnNpc(Player player, Job job) {
        if (job == Job.JOKEYRINI) {
            player.getWorld().spawn(player.getLocation(), WanderingTrader.class, wt -> {
                wt.setAI(false);
                wt.setInvulnerable(true);
                wt.setRemoveWhenFarAway(false);
                wt.customName(Component.text(job.getDisplayName(), job.getColor()));
                wt.setCustomNameVisible(true);
                wt.getPersistentDataContainer().set(
                    JokeyriniManager.getKeyNpc(),
                    PersistentDataType.STRING,
                    job.name()
                );
            });
            player.sendMessage(success("Spawned " + job.getDisplayName() + " NPC."));
            return;
        }
        player.getWorld().spawn(player.getLocation(), Villager.class, v -> {
            v.setVillagerType(Villager.Type.SAVANNA);
            v.setProfession(job.getProfession());
            v.setAI(false);
            v.setInvulnerable(true);
            v.setRemoveWhenFarAway(false);
            v.customName(Component.text(job.getDisplayName(), job.getColor()));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(
                JobManager.getKeyNpcJob(),
                PersistentDataType.STRING,
                job.name()
            );
        });
        player.sendMessage(success("Spawned " + job.getDisplayName() + " NPC."));
    }

    private static void doRemove(Player executor, Player target) {
        if (JobManager.removeJob(target.getUniqueId())) {
            executor.sendMessage(success("Removed " + target.getName() + "'s job."));
            if (!executor.equals(target)) {
                target.sendMessage(info("An admin removed your job. Use /job to select a new one."));
            }
        } else {
            executor.sendMessage(error(target.getName() + " does not have a job."));
        }
    }

    private static void doAdminSet(Player executor, Player target, Job job) {
        JobManager.forceSetJob(target.getUniqueId(), job);
        executor.sendMessage(success("Set " + target.getName() + "'s job to " + job.getDisplayName() + "."));
        if (!executor.equals(target)) {
            target.sendMessage(info("An admin set your job to " + job.getDisplayName() + "."));
        }
    }

    private static void showLevel(Player player) {
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(error("You don't have a job yet. Use /job to select one."));
            return;
        }

        int progress = data.getProgress();
        int required = data.getProgressRequired();
        int barLength = 20;
        int filled = Math.min(barLength, (int) ((double) progress / required * barLength));

        Component bar = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("█".repeat(filled), NamedTextColor.GREEN))
                .append(Component.text("█".repeat(barLength - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        player.sendMessage(
                Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Level " + data.getLevel(), NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(Component.text("Progress: ", NamedTextColor.GRAY))
                        .append(bar)
                        .append(Component.text(" " + progress + "/" + required + " XP", NamedTextColor.GRAY))
        );
    }

    private static void doLevelOp(Player executor, Player target, String operation, String type, int amount) {
        boolean ok;
        String desc;

        switch (operation) {
            case "add" -> {
                if (type.equals("xp")) {
                    PlayerJobData data = JobManager.getJobData(target.getUniqueId());
                    if (data == null) {
                        executor.sendMessage(error(target.getName() + " does not have a job."));
                        return;
                    }
                    int maxAddable = data.getProgressRequired() - data.getProgress();
                    if (amount > maxAddable) {
                        executor.sendMessage(error("Cannot add " + amount + " XP — " + target.getName()
                                + " can receive at most " + maxAddable + " XP before leveling up."));
                        return;
                    }
                    ok = JobManager.adminAddXp(target.getUniqueId(), amount, target);
                    desc = "Added " + amount + " XP to " + target.getName();
                } else {
                    ok = JobManager.adminAddLevel(target.getUniqueId(), amount);
                    desc = "Added " + amount + " level(s) to " + target.getName();
                }
            }
            case "set" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminSetXp(target.getUniqueId(), amount);
                    desc = "Set " + target.getName() + "'s XP to " + amount;
                } else {
                    ok = JobManager.adminSetLevel(target.getUniqueId(), amount);
                    desc = "Set " + target.getName() + "'s level to " + amount;
                }
            }
            case "remove" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminRemoveXp(target.getUniqueId(), amount);
                    desc = "Removed " + amount + " XP from " + target.getName();
                } else {
                    ok = JobManager.adminRemoveLevel(target.getUniqueId(), amount);
                    desc = "Removed " + amount + " level(s) from " + target.getName();
                }
            }
            default -> {
                executor.sendMessage(error("Unknown operation."));
                return;
            }
        }

        if (ok) {
            executor.sendMessage(success(desc + "."));
        } else {
            executor.sendMessage(error(target.getName() + " does not have a job."));
        }
    }
}
