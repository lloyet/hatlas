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
 *   <li>{@code /hatlas economy history <player|faction> [n]}: the latest ruby-ledger rows of a party.</li>
 *   <li>{@code /hatlas economy supply}: rubies created and destroyed so far.</li>
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
                        || src.getSender().hasPermission("hatlas.access.admin")
                        || src.getSender().hasPermission("hatlas.economy.admin"))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("access")
                        .requires(src -> src.getSender().hasPermission("hatlas.access.admin"))
                        .executes(ctx -> help(ctx.getSource().getSender()))
                        .then(Commands.literal("check")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> checkAccess(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("economy")
                        .requires(src -> src.getSender().hasPermission("hatlas.economy.admin"))
                        .executes(ctx -> help(ctx.getSource().getSender()))
                        .then(Commands.literal("history")
                                .then(Commands.argument("party", StringArgumentType.word())
                                        .executes(ctx -> history(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "party"), 10))
                                        .then(Commands.argument("limit", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> history(ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "party"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "limit"))))))
                        .then(Commands.literal("supply")
                                .executes(ctx -> supply(ctx.getSource().getSender()))))
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
        if (sender.hasPermission("hatlas.economy.admin")) {
            if (!msg.equals(Component.empty())) msg = msg.append(Component.newline());
            msg = msg.append(info("/hatlas economy history <player|faction> [n]"))
                    .append(Component.text(" — latest ruby moves", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(info("/hatlas economy supply"))
                    .append(Component.text(" — rubies emitted, burned, supply", NamedTextColor.GRAY));
        }
        sender.sendMessage(msg);
        return Command.SINGLE_SUCCESS;
    }

    /** A faction name, else a player (online, cached or UUID). Reads after the pending ledger rows. */
    private static int history(CommandSender sender, String who, int limit) {
        String id;
        String label;
        org.minecraft.hatlas.faction.Faction faction = org.minecraft.hatlas.faction.FactionManager.getFaction(who);
        if (faction != null) {
            id = faction.getName();
            label = "faction " + faction.getName();
        } else {
            UUID uuid = null;
            try {
                uuid = UUID.fromString(who);
            } catch (IllegalArgumentException notUuid) {
                OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(who);
                if (p != null) uuid = p.getUniqueId();
            }
            if (uuid == null) {
                sender.sendMessage(Component.text("No faction or known player named " + who + ".", NamedTextColor.RED));
                return 0;
            }
            id = uuid.toString();
            label = who;
        }
        org.minecraft.hatlas.economy.RubyLedger.flush();
        Storage.readAsync(store -> store.readLedger(id, limit), (rows, err) -> {
            if (err != null) {
                sender.sendMessage(Component.text("Ledger read failed: " + err, NamedTextColor.RED));
                return;
            }
            Component msg = info("Ruby ledger of " + label + " (" + rows.size() + " latest):");
            for (org.minecraft.hatlas.storage.model.LedgerRow r : rows) {
                boolean in = id.equals(r.receiverId());
                msg = msg.append(Component.newline())
                        .append(Component.text(r.kind() + " ", NamedTextColor.WHITE))
                        .append(Component.text((in ? "+" : "-") + r.amount(), in ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(" " + party(r.senderType(), r.senderName()) + " → "
                                + party(r.receiverType(), r.receiverName())
                                + (r.ref() == null ? "" : " (" + r.ref() + ")"), NamedTextColor.GRAY));
            }
            sender.sendMessage(msg);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static String party(String type, String name) {
        if (type == null) return "—";
        return "FACTION".equals(type) ? "[" + name + "]" : name;
    }

    private static int supply(CommandSender sender) {
        org.minecraft.hatlas.economy.RubyLedger.flush();
        Storage.readAsync(org.minecraft.hatlas.storage.DataStore::ledgerTotals, (t, err) -> {
            if (err != null) {
                sender.sendMessage(Component.text("Ledger read failed: " + err, NamedTextColor.RED));
                return;
            }
            long inBlocks = 9L * org.minecraft.hatlas.block.RubyBlockManager.countPlaced("ruby_block");
            sender.sendMessage(info("Rubies emitted: " + t[0] + ", burned: " + t[1]
                    + ", supply: " + (t[0] - t[1]) + " (of which " + inBlocks + " in placed ruby blocks; "
                    + t[2] + " ledger rows)"));
        });
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
