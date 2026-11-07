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
 *   <li>{@code /hatlas storage save}: writes all data now and waits for it (used by
 *       {@code scripts/backup/hatlas-backup.sh} before the database dump).</li>
 * </ul>
 * Backups are taken outside the game (database dump together with the world), not by a command.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HatlasCommand {

    /** Reply prefix the backup script waits for; keep in sync with hatlas-backup.sh. */
    public static final String SAVE_OK = "Storage saved";

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
                                }))
                        .then(Commands.literal("save")
                                .executes(ctx -> save(ctx.getSource().getSender()))))
                .build();
    }

    private static int help(CommandSender sender) {
        sender.sendMessage(info("/hatlas storage info").append(Component.text(" — active storage backend", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(info("/hatlas storage save"))
                .append(Component.text(" — write all data now (before a backup)", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private static int save(CommandSender sender) {
        String err = Storage.flush();
        if (err != null) {
            sender.sendMessage(Component.text("Storage save failed: " + err, NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text(SAVE_OK + " to " + Storage.describe() + ".", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
