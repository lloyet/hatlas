package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.storage.Storage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@code /hatlas} — server administration.
 * <ul>
 *   <li>{@code /hatlas storage info}: the active backend.</li>
 *   <li>{@code /hatlas storage export}: full backup of the data to a portable SQLite file in
 *       {@code plugins/hatlas/backups/}. The backend itself cannot be switched (no SQLite ↔
 *       PostgreSQL migration): the export is a backup only.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class HatlasCommand {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private HatlasCommand() {}

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

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
                        .then(Commands.literal("export")
                                .executes(ctx -> export(ctx.getSource().getSender()))))
                .build();
    }

    private static int help(CommandSender sender) {
        sender.sendMessage(info("/hatlas storage info").append(Component.text(" — active storage backend", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(info("/hatlas storage export"))
                .append(Component.text(" — full backup to a portable SQLite file (plugins/hatlas/backups/)", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandSender sender) {
        File folder = new File(Hatlas.instance.getDataFolder(), "backups");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            sender.sendMessage(error("Cannot create " + folder.getPath() + "."));
            return 0;
        }
        File target = new File(folder, "hatlas-" + LocalDateTime.now().format(STAMP) + ".db");
        sender.sendMessage(info("Exporting all data to " + target.getName() + "…"));
        Storage.export(target, err -> {
            if (err == null) {
                sender.sendMessage(success("Export done: plugins/hatlas/backups/" + target.getName()
                        + " (" + Math.max(1, target.length() / 1024) + " KB)."));
            } else {
                sender.sendMessage(error("Export failed: " + err));
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
