package org.minecraft.hatlas.command;

import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.Storage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.orezone.OreZone;
import org.minecraft.hatlas.orezone.OreZoneManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class OreZoneCommand {

    private static final String PERM = "hatlas.orezone.admin";

    /** Look-distance for /orezone pos1 and /orezone pos2 (blocks). */
    private static final int LOOK_DISTANCE = 100;

    /** Per-player pos1 selection (block-snapped). Reset on /orezone add success. */
    private static final Map<UUID, Location> pos1 = new HashMap<>();
    private static final Map<UUID, Location> pos2 = new HashMap<>();

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("orezone")
                .requires(src -> src.getSender().hasPermission(PERM))

                // /orezone — show help
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Ore Zone Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(entry("pos1",   "[x y z]", "Mark corner 1: the block you're looking at, or x y z"))
                                    .append(Component.newline()).append(entry("pos2",   "[x y z]", "Mark corner 2: the block you're looking at, or x y z"))
                                    .append(Component.newline()).append(entry("add",    "<name>",  "Create an ore zone from pos1 and pos2"))
                                    .append(Component.newline()).append(entry("remove", "<name>",  "Delete an ore zone"))
                                    .append(Component.newline()).append(entry("reset",  "<name>",  "Re-fill an ore zone with random ores now"))
                                    .append(Component.newline()).append(entry("list",   "",        "List all ore zones"))
                    );
                    return Command.SINGLE_SUCCESS;
                })

                // /orezone pos1 [x y z]
                .then(Commands.literal("pos1")
                        .executes(ctx -> selectPos(ctx, 1, null))
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .executes(ctx -> selectPos(ctx, 1, resolvePos(ctx)))))

                // /orezone pos2 [x y z]
                .then(Commands.literal("pos2")
                        .executes(ctx -> selectPos(ctx, 2, null))
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .executes(ctx -> selectPos(ctx, 2, resolvePos(ctx)))))

                // /orezone add <name>
                .then(Commands.literal("add")
                        .executes(ctx -> usage(ctx, "add <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Location a = pos1.get(player.getUniqueId());
                                    Location b = pos2.get(player.getUniqueId());
                                    if (a == null || b == null) {
                                        player.sendMessage(error("Set both /orezone pos1 and /orezone pos2 first."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (a.getWorld() == null || b.getWorld() == null
                                            || !a.getWorld().getName().equals(b.getWorld().getName())) {
                                        player.sendMessage(error("pos1 and pos2 must be in the same world."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    OreZone zone = OreZoneManager.create(name);
                                    if (zone == null) {
                                        player.sendMessage(error("An ore zone named '" + name + "' already exists."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    zone.setBounds(a, b);
                                    save();
                                    pos1.remove(player.getUniqueId());
                                    pos2.remove(player.getUniqueId());
                                    player.sendMessage(success("Ore zone '" + name + "' created — "
                                            + zone.volume() + " blocks."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /orezone remove <name>
                .then(Commands.literal("remove")
                        .executes(ctx -> usage(ctx, "remove <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, bldr) -> { OreZoneManager.getAll().forEach(z -> bldr.suggest(z.getName())); return bldr.buildFuture(); })
                                .executes(ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    if (OreZoneManager.remove(name)) {
                                        save();
                                        sender.sendMessage(success("Ore zone '" + name + "' removed."));
                                    } else {
                                        sender.sendMessage(error("No ore zone named '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /orezone reset <name>
                .then(Commands.literal("reset")
                        .executes(ctx -> usage(ctx, "reset <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, bldr) -> { OreZoneManager.getAll().forEach(z -> bldr.suggest(z.getName())); return bldr.buildFuture(); })
                                .executes(ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    OreZone zone = OreZoneManager.get(name);
                                    if (zone == null) {
                                        sender.sendMessage(error("No ore zone named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    long placed = OreZoneManager.fill(zone);
                                    if (placed < 0) {
                                        sender.sendMessage(error("Cannot reset '" + name + "' — its world is unloaded or no bounds set."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    sender.sendMessage(success("Ore zone '" + name + "' refilled — " + placed + " blocks placed."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /orezone list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            var sender = ctx.getSource().getSender();
                            var all = OreZoneManager.getAll();
                            if (all.isEmpty()) {
                                sender.sendMessage(info("No ore zones defined."));
                                return Command.SINGLE_SUCCESS;
                            }
                            sender.sendMessage(Component.text("=== Ore Zones ===", NamedTextColor.GOLD));
                            for (OreZone z : all) {
                                String bounds = z.hasBounds()
                                        ? " — " + z.volume() + " blocks in " + z.getWorldName()
                                        : " — no bounds";
                                sender.sendMessage(
                                        Component.text("  " + z.getName(), NamedTextColor.AQUA)
                                                .append(Component.text(bounds, NamedTextColor.GRAY)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }

    private static BlockPosition resolvePos(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getArgument("pos", BlockPositionResolver.class).resolve(ctx.getSource());
    }

    /**
     * Stores corner {@code which} for the executing player: the given block position (in the
     * player's world), or, when {@code pos} is null, the block the player is looking at.
     * Coordinates make underground corners possible (and scripted setups).
     */
    private static int selectPos(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int which,
                                 BlockPosition pos) {
        Entity exec = ctx.getSource().getExecutor();
        if (!(exec instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }
        Location loc;
        if (pos != null) {
            loc = new Location(player.getWorld(), pos.blockX(), pos.blockY(), pos.blockZ());
        } else {
            Block target = player.getTargetBlockExact(LOOK_DISTANCE);
            if (target == null) {
                player.sendMessage(error("No block in sight (look at a block within " + LOOK_DISTANCE
                        + " blocks, or give x y z)."));
                return Command.SINGLE_SUCCESS;
            }
            loc = target.getLocation();
        }
        if (which == 1) pos1.put(player.getUniqueId(), loc);
        else            pos2.put(player.getUniqueId(), loc);
        player.sendMessage(success("pos" + which + " set to "
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " (" + loc.getWorld().getName() + ")."));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String syntax) {
        ctx.getSource().getSender().sendMessage(error("Usage: /orezone " + syntax));
        return Command.SINGLE_SUCCESS;
    }

    private static void save() {
        Storage.save(DataDomain.OREZONES);
    }

    private static Component entry(String sub, String args, String desc) {
        Component line = Component.text("/orezone ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
