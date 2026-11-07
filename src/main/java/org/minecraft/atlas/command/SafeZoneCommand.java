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
import org.minecraft.atlas.safezone.SafeZone;
import org.minecraft.atlas.safezone.SafeZoneManager;
import org.minecraft.atlas.safezone.SafeZoneNpcManager;
import org.minecraft.atlas.safezone.SafeZoneTeleportManager;

public class SafeZoneCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

    private static final String PERM       = "atlas.safezone.admin";
    private static final String PERM_TP    = "atlas.safezone.tp";

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("safezone")
                // No root permission gate — /safezone tp is available to non-admins.
                // Each admin subcommand carries its own .requires() below.

                // /safezone (no args) — show help
                .executes(ctx -> {
                    boolean admin = ctx.getSource().getSender().hasPermission(PERM);
                    Component msg = Component.text("--- Safe Zone Commands ---", NamedTextColor.GOLD)
                            .append(Component.newline()).append(entry("tp", "<name>", "Teleport to a safe zone's spawn point"));
                    if (admin) {
                        msg = msg
                                .append(Component.newline()).append(entry("create",   "<name>", "Create a new safe zone"))
                                .append(Component.newline()).append(entry("delete",   "<name>", "Delete a safe zone (removes its claims and spawn)"))
                                .append(Component.newline()).append(entry("claim",    "<name>", "Add current chunk to a safe zone"))
                                .append(Component.newline()).append(entry("unclaim",  "<name>", "Remove current chunk from a safe zone"))
                                .append(Component.newline()).append(entry("list",     "",       "List all safe zones with chunk counts"))
                                .append(Component.newline()).append(entry("setspawn", "<name>", "Set the teleport location for a safe zone"))
                                .append(Component.newline()).append(entry("describe", "<name> <description>", "Set the description shown in the Explorer GUI"))
                                .append(Component.newline()).append(entry("npc summon", "", "Summon the Explorer NPC at your location"));
                    }
                    ctx.getSource().getSender().sendMessage(msg);
                    return Command.SINGLE_SUCCESS;
                })

                // /safezone tp <name> — countdown teleport for any player; ops bypass the delay.
                .then(Commands.literal("tp")
                        .requires(src -> src.getSender().hasPermission(PERM_TP))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "tp <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    SafeZoneManager.getAll().stream()
                                            .filter(z -> z.getSpawnPoint() != null)
                                            .forEach(z -> b.suggest(z.getName()));
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    SafeZone zone = SafeZoneManager.get(name);
                                    if (zone == null) {
                                        player.sendMessage(error("No safe zone named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Location dest = zone.getSpawnPoint();
                                    if (dest == null) {
                                        player.sendMessage(error("No teleport location is set for '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String label = SafeZoneManager.capitalizedName(zone.getName());
                                    SafeZoneTeleportManager.startTeleport(player, dest, label);
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone npc summon
                .then(Commands.literal("npc")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .then(Commands.literal("summon")
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    SafeZoneNpcManager.summonExplorer(player.getLocation());
                                    player.sendMessage(success("Explorer NPC summoned at your location."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone create <name>
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "create <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    if (SafeZoneManager.create(name)) {
                                        save();
                                        player.sendMessage(success("Safe zone '" + name + "' created."));
                                    } else {
                                        player.sendMessage(error("A safe zone named '" + name + "' already exists."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone delete <name>
                .then(Commands.literal("delete")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "delete <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { SafeZoneManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    if (SafeZoneManager.delete(name)) {
                                        save();
                                        sender.sendMessage(success("Safe zone '" + name + "' deleted."));
                                    } else {
                                        sender.sendMessage(error("No safe zone named '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone claim <name>
                .then(Commands.literal("claim")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "claim <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { SafeZoneManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    SafeZone zone = SafeZoneManager.get(name);
                                    if (zone == null) {
                                        player.sendMessage(error("No safe zone named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    org.bukkit.Chunk chunk = player.getLocation().getChunk();
                                    boolean added = zone.claim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
                                    save();
                                    if (added) {
                                        player.sendMessage(success("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] added to '" + name + "'. (" + zone.getChunkCount() + " chunks total)"));
                                    } else {
                                        player.sendMessage(info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] is already in '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone unclaim <name>
                .then(Commands.literal("unclaim")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "unclaim <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { SafeZoneManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    SafeZone zone = SafeZoneManager.get(name);
                                    if (zone == null) {
                                        player.sendMessage(error("No safe zone named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    org.bukkit.Chunk chunk = player.getLocation().getChunk();
                                    boolean removed = zone.unclaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
                                    save();
                                    if (removed) {
                                        player.sendMessage(success("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] removed from '" + name + "'. (" + zone.getChunkCount() + " chunks remaining)"));
                                    } else {
                                        player.sendMessage(error("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] is not part of '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone list
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> {
                            var sender = ctx.getSource().getSender();
                            var all = SafeZoneManager.getAll();
                            if (all.isEmpty()) {
                                sender.sendMessage(info("No safe zones defined."));
                                return Command.SINGLE_SUCCESS;
                            }
                            sender.sendMessage(Component.text("=== Safe Zones ===", NamedTextColor.GOLD));
                            for (SafeZone p : all) {
                                String spawnInfo = p.getSpawnPoint() != null ? " [spawn set]" : "";
                                sender.sendMessage(
                                        Component.text("  " + p.getName(), NamedTextColor.AQUA)
                                                .append(Component.text(" — " + p.getChunkCount() + " chunks" + spawnInfo,
                                                        NamedTextColor.GRAY)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))

                // /safezone setspawn <name>
                .then(Commands.literal("setspawn")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "setspawn <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { SafeZoneManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    SafeZone zone = SafeZoneManager.get(name);
                                    if (zone == null) {
                                        player.sendMessage(error("No safe zone named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    zone.setSpawnPoint(player.getLocation());
                                    save();
                                    player.sendMessage(success("Teleport location set for '" + name + "' at "
                                            + player.getLocation().getBlockX() + ", "
                                            + player.getLocation().getBlockY() + ", "
                                            + player.getLocation().getBlockZ() + "."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /safezone describe <name> <description...>
                .then(Commands.literal("describe")
                        .requires(src -> src.getSender().hasPermission(PERM))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "describe <name> <description>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { SafeZoneManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> usage(ctx.getSource().getSender(), "describe <name> <description>"))
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                            SafeZone zone = SafeZoneManager.get(name);
                                            if (zone == null) {
                                                sender.sendMessage(error("No safe zone named '" + name + "'."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String desc = StringArgumentType.getString(ctx, "description").trim();
                                            zone.setDescription(desc);
                                            save();
                                            sender.sendMessage(success("Description set for '" + name + "'."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                .build();
    }

    private static void save() {
        Storage.save(DataDomain.SAFEZONES);
    }

    private static int usage(net.kyori.adventure.audience.Audience audience, String syntax) {
        audience.sendMessage(error("Usage: /safezone " + syntax));
        return Command.SINGLE_SUCCESS;
    }

    private static Component entry(String sub, String args, String desc) {
        Component line = Component.text("/safezone ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
