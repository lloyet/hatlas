package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.storage.Storage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@code /atlas} — server administration.
 * <ul>
 *   <li>{@code /atlas storage info}: the active backend.</li>
 *   <li>{@code /atlas storage export}: full backup of the data to a portable SQLite file in
 *       {@code plugins/atlas/backups/}. The backend itself cannot be switched (no SQLite ↔
 *       PostgreSQL migration): the export is a backup only.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class AtlasCommand {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private AtlasCommand() {}

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("atlas")
                .requires(src -> src.getSender().hasPermission("atlas.storage.admin"))
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
        sender.sendMessage(info("/atlas storage info").append(Component.text(" — active storage backend", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(info("/atlas storage export"))
                .append(Component.text(" — full backup to a portable SQLite file (plugins/atlas/backups/)", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandSender sender) {
        File folder = new File(Atlas.instance.getDataFolder(), "backups");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            sender.sendMessage(error("Cannot create " + folder.getPath() + "."));
            return 0;
        }
        File target = new File(folder, "atlas-" + LocalDateTime.now().format(STAMP) + ".db");
        sender.sendMessage(info("Exporting all data to " + target.getName() + "…"));
        Storage.export(target, err -> {
            if (err == null) {
                sender.sendMessage(success("Export done: plugins/atlas/backups/" + target.getName()
                        + " (" + Math.max(1, target.length() / 1024) + " KB)."));
            } else {
                sender.sendMessage(error("Export failed: " + err));
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
