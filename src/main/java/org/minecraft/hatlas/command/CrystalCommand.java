package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.Collection;

public class CrystalCommand {

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private static Component success(String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    private static Component info(String msg) {
        return Component.text(msg, NamedTextColor.GOLD);
    }

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/crystal ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("crystal")
                .requires(src -> src.getSender().hasPermission("hatlas.crystal"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Crystal Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("list", "", "List all crystals in your faction"))
                                    .append(Component.newline()).append(helpEntry("rename", "<old-name> <new-name>", "Rename a crystal in your faction"))
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("hatlas.crystal.list"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Collection<HatlasCrystal> crystals = HatlasCrystalManager.getFactionCrystals(factionName);
                            if (crystals.isEmpty()) {
                                player.sendMessage(info("Your faction has no Hatlas Crystals."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Component list = Component.text("--- " + factionName + " Crystals ---", NamedTextColor.GOLD);
                            for (HatlasCrystal c : crystals) {
                                list = list.append(Component.newline())
                                        .append(Component.text("  " + c.getName(), NamedTextColor.WHITE))
                                        .append(Component.text("  ", NamedTextColor.GRAY))
                                        .append(Component.text((int) c.getHp() + "/" + (int) c.getMaxHp() + "♥",
                                                NamedTextColor.RED))
                                        .append(Component.text(c.getHome() != null
                                                        ? "  @ " + formatLocation(c.getHome()) : "  (no home)",
                                                NamedTextColor.DARK_GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("rename")
                        .requires(src -> src.getSender().hasPermission("hatlas.crystal.rename"))
                        .then(Commands.argument("old-name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (exec instanceof Player p) {
                                        String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                        if (fn != null) {
                                            HatlasCrystalManager.getFactionCrystals(fn)
                                                    .forEach(c -> builder.suggest(c.getName()));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("new-name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                            if (factionName == null) {
                                                player.sendMessage(error("You are not in any faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String oldName = StringArgumentType.getString(ctx, "old-name");
                                            String newName = StringArgumentType.getString(ctx, "new-name");

                                            if (newName.contains(" ") || newName.isEmpty()) {
                                                player.sendMessage(error("New name must be a single non-empty word."));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            if (HatlasCrystalManager.renameCrystal(factionName, oldName, newName)) {
                                                player.sendMessage(success("Crystal '" + oldName + "' renamed to '" + newName + "'."));
                                                FactionManager.broadcastToFaction(factionName,
                                                        info(player.getName() + " renamed crystal '" + oldName + "' to '" + newName + "'."),
                                                        player.getUniqueId());
                                            } else {
                                                player.sendMessage(error("Could not rename. '" + oldName + "' not found, or '" + newName + "' is already taken."));
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    private static String formatLocation(org.bukkit.Location loc) {
        return loc.getWorld().getName()
                + " " + (int) loc.getX()
                + " " + (int) loc.getY()
                + " " + (int) loc.getZ();
    }
}
