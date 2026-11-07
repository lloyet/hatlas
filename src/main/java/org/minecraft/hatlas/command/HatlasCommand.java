package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.minecraft.hatlas.storage.Storage;

/**
 * {@code /hatlas} — server administration.
 * <ul>
 *   <li>{@code /hatlas storage info}: the active backend.</li>
 * </ul>
 * Backups are taken outside the game (database dump together with the world), not by a command.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HatlasCommand {

    private HatlasCommand() {}

    private static Component info(String msg) { return Component.text(msg, NamedTextColor.GOLD); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("hatlas")
                .requires(src -> src.getSender().hasPermission("hatlas.storage.admin"))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("storage")
                        .executes(ctx -> help(ctx.getSource().getSender()))
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            info("Storage: ").append(Component.text(Storage.describe(), NamedTextColor.WHITE)));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private static int help(CommandSender sender) {
        sender.sendMessage(info("/hatlas storage info").append(Component.text(" — active storage backend", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }
}
