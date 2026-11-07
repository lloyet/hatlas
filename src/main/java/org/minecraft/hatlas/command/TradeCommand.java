package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.trade.Trade;
import org.minecraft.hatlas.trade.TradeManager;

import java.util.UUID;

public class TradeCommand {

    // -------------------------------------------------------------------------
    // Message helpers
    // -------------------------------------------------------------------------

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private static Component success(String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    private static Component info(String msg) {
        return Component.text(msg, NamedTextColor.GOLD);
    }

    private static Component helpEntry(String sub, String desc) {
        Component line = Component.text("/trade ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));

        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("trade")
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Trade Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("accept", "Accept a trade invitation"))
                                    .append(Component.newline()).append(helpEntry("decline", "Decline a trade invitation"))
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("accept")
                        // /trade accept          — works when you have exactly one pending request
                        .executes(ctx -> handleAccept(ctx.getSource(), null))
                        // /trade accept <player> — sent by the clickable link in the request message
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> handleAccept(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("decline")
                        .executes(ctx -> handleDecline(ctx.getSource(), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> handleDecline(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    private static int handleAccept(CommandSourceStack src, String initiatorName) {
        Entity executor = src.getExecutor();
        if (!(executor instanceof Player target)) {
            src.getSender().sendMessage(Component.text("Only players can run this command.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        UUID initiatorUUID = TradeManager.getPendingInitiator(target.getUniqueId());

        if (initiatorUUID == null) {
            target.sendMessage(Component.text("You have no pending trade request.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Player initiator = Bukkit.getPlayer(initiatorUUID);

        // If a name was supplied (from the clickable link), verify it still matches
        if (initiatorName != null && (initiator == null || !initiator.getName().equalsIgnoreCase(initiatorName))) {
            target.sendMessage(Component.text("That trade request is no longer valid.", NamedTextColor.RED));
            TradeManager.removePendingRequest(target.getUniqueId());
            return Command.SINGLE_SUCCESS;
        }

        if (initiator == null) {
            target.sendMessage(Component.text("That trade request is no longer valid.", NamedTextColor.RED));
            TradeManager.removePendingRequest(target.getUniqueId());
            return Command.SINGLE_SUCCESS;
        }

        if (TradeManager.hasTrade(initiator.getUniqueId()) || TradeManager.hasTrade(target.getUniqueId())) {
            target.sendMessage(Component.text("One of you is already in a trade.", NamedTextColor.RED));
            TradeManager.removePendingRequest(target.getUniqueId());
            return Command.SINGLE_SUCCESS;
        }

        TradeManager.removePendingRequest(target.getUniqueId());
        Trade trade = new Trade(initiator, target);
        TradeManager.addTrade(trade);

        return Command.SINGLE_SUCCESS;
    }

    private static int handleDecline(CommandSourceStack src, String initiatorName) {
        Entity executor = src.getExecutor();
        if (!(executor instanceof Player target)) return Command.SINGLE_SUCCESS;

        UUID initiatorUUID = TradeManager.getPendingInitiator(target.getUniqueId());

        if (initiatorUUID == null) {
            target.sendMessage(Component.text("You have no pending trade request.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        TradeManager.removePendingRequest(target.getUniqueId());
        target.sendMessage(Component.text("Trade request declined.", NamedTextColor.RED));

        Player initiator = Bukkit.getPlayer(initiatorUUID);
        if (initiator != null) {
            initiator.sendMessage(Component.text(
                    target.getName() + " declined your trade request.", NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }
}

