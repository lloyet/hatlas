package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.access.AccessManager;
import org.minecraft.hatlas.access.TicketChecker;
import org.minecraft.hatlas.storage.Storage;

import java.util.UUID;

/**
 * {@code /hatlas} — server administration.
 * <ul>
 *   <li>{@code /hatlas storage info}: the active backend.</li>
 *   <li>{@code /hatlas storage save}: writes all data now and waits for it (used by
 *       {@code scripts/backup/hatlas-backup.sh} before the database dump).</li>
 *   <li>{@code /hatlas access check <player|uuid>}: the player's ticket as the login check sees it.</li>
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
                .requires(src -> src.getSender().hasPermission("hatlas.storage.admin")
                        || src.getSender().hasPermission("hatlas.access.admin"))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("access")
                        .requires(src -> src.getSender().hasPermission("hatlas.access.admin"))
                        .executes(ctx -> help(ctx.getSource().getSender()))
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> checkAccess(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("storage")
                        .requires(src -> src.getSender().hasPermission("hatlas.storage.admin"))
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
        Component msg = Component.empty();
        if (sender.hasPermission("hatlas.storage.admin")) {
            msg = msg.append(info("/hatlas storage info")).append(Component.text(" — active storage backend", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(info("/hatlas storage save"))
                    .append(Component.text(" — write all data now (before a backup)", NamedTextColor.GRAY));
        }
        if (sender.hasPermission("hatlas.access.admin")) {
            if (sender.hasPermission("hatlas.storage.admin")) msg = msg.append(Component.newline());
            msg = msg.append(info("/hatlas access check <player|uuid>"))
                    .append(Component.text(" — a player's access ticket", NamedTextColor.GRAY));
        }
        sender.sendMessage(msg);
        return Command.SINGLE_SUCCESS;
    }

    /** Resolves the player and queries the ticket off the main thread, then replies. */
    private static int checkAccess(CommandSender sender, String who) {
        Bukkit.getScheduler().runTaskAsynchronously(Hatlas.instance, () -> {
            UUID id;
            String name;
            try {
                id = UUID.fromString(who);
                name = who;
            } catch (IllegalArgumentException notUuid) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(who); // may look the name up: async only
                id = p.getUniqueId();
                name = p.getName() == null ? who : p.getName();
            }
            TicketChecker.Result r = AccessManager.check(id);
            String head = name + " (" + id + "): ";
            Component line = switch (r.status()) {
                case VALID -> Component.text(head + "ticket valid until " + AccessManager.format(r.validUntil()), NamedTextColor.GREEN);
                case EXPIRED -> Component.text(head + "ticket expired on " + AccessManager.format(r.validUntil()), NamedTextColor.YELLOW);
                case NONE -> Component.text(head + "no ticket", NamedTextColor.YELLOW);
                case ERROR -> Component.text(head + "check failed: " + r.error(), NamedTextColor.RED);
            };
            Component state = Component.text(" (login check " + (AccessManager.isRequired() ? "ON" : "OFF") + ")", NamedTextColor.GRAY);
            Bukkit.getScheduler().runTask(Hatlas.instance, () -> sender.sendMessage(line.append(state)));
        });
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
