package org.minecraft.atlas.command;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.teleport.HomeManager;

public class HomeCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    public static LiteralCommandNode<CommandSourceStack> buildSetHome() {
        return Commands.literal("sethome")
                .requires(src -> src.getSender().hasPermission("atlas.home.set"))
                // /sethome — set the main home
                .executes(ctx -> doSetHome(ctx, HomeManager.MAIN_HOME, true))
                // /sethome <name> — set a named secondary home
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> doSetHome(ctx, StringArgumentType.getString(ctx, "name"), false)))
                .build();
    }

    private static int doSetHome(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                 String name, boolean isMain) {
        Entity executor = ctx.getSource().getExecutor();
        if (!(executor instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
            return Command.SINGLE_SUCCESS;
        }
        // The main slot is reserved — players cannot use "main" as a custom secondary name.
        if (!isMain && name.equalsIgnoreCase(HomeManager.MAIN_HOME)) {
            player.sendMessage(error("'" + HomeManager.MAIN_HOME
                    + "' is reserved — use /sethome (no name) to set your main home."));
            return Command.SINGLE_SUCCESS;
        }

        java.util.UUID uuid = player.getUniqueId();
        boolean alreadyExists = HomeManager.hasHome(uuid, name);

        // Cap check: only secondary homes count against the cap; main is always free.
        if (!isMain && !alreadyExists) {
            int limit = HomeManager.getHomeLimit(uuid);
            int currentCount = HomeManager.getHomeNames(uuid).size();
            if (currentCount >= limit) {
                player.sendMessage(error("You have reached your home limit (" + limit
                        + "). Buy the homes faction skill to unlock more, or delete a home first."));
                return Command.SINGLE_SUCCESS;
            }
        }

        // Block /sethome inside another faction's territory
        Location loc = player.getLocation();
        String claimOwner = FactionClaimManager.getClaimingFaction(
                loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        if (claimOwner != null && !claimOwner.equals(FactionManager.getPlayerFaction(uuid))) {
            player.sendMessage(error("You cannot set a home in another faction's territory!"));
            return Command.SINGLE_SUCCESS;
        }

        HomeManager.setHome(uuid, name, player.getLocation());
        Storage.save(DataDomain.HOMES);

        String label = isMain ? "Main home" : "Home '" + name + "'";
        player.sendMessage(success(label + (alreadyExists ? " updated." : " set.")));
        return Command.SINGLE_SUCCESS;
    }

    public static LiteralCommandNode<CommandSourceStack> buildHome() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission("atlas.home"))

                // /home — teleport to first home
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                error("Only players can use this command."));
                        return Command.SINGLE_SUCCESS;
                    }
                    HomeManager.startTeleport(player, null);
                    return Command.SINGLE_SUCCESS;
                })

                // /home <name> — teleport to named home
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (executor instanceof Player player) {
                                HomeManager.getHomeNames(player.getUniqueId()).forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                        error("Only players can use this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "name");
                            HomeManager.startTeleport(player, name);
                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildDelHome() {
        return Commands.literal("delhome")
                .requires(src -> src.getSender().hasPermission("atlas.home.delete"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (executor instanceof Player player) {
                                HomeManager.getHomeNames(player.getUniqueId()).forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                        error("Only players can use this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean deleted = HomeManager.deleteHome(player.getUniqueId(), name);
                            if (deleted) {
                                Storage.save(DataDomain.HOMES);
                                player.sendMessage(success("Home '" + name + "' deleted."));
                            } else {
                                player.sendMessage(error("Home '" + name + "' not found."));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
