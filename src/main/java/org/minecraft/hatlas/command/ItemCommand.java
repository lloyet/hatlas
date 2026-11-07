package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.Item.AmethystItem;
import org.minecraft.hatlas.Item.RubyItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin command that hands out any registered Hatlas custom item: ruby gem/block/ores
 * and the full amethyst gear set. Replaces the old /ruby give command.
 */
public final class ItemCommand {

    private ItemCommand() {
    }

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private static Component success(String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    private static List<String> allIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(RubyItem.ids());
        ids.addAll(AmethystItem.ids());
        return ids;
    }

    private static ItemStack templateFor(String id) {
        if (RubyItem.ids().contains(id)) return RubyItem.get(id);
        if (AmethystItem.ids().contains(id)) return AmethystItem.get(id);
        return null;
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("item")
                .requires(src -> src.getSender().hasPermission("hatlas.item.admin"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("Usage: /item give <player> <item> [amount]",
                                    NamedTextColor.GOLD));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            allIds().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> runGive(ctx, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> runGive(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))
                )
                .build();
    }

    private static int runGive(CommandContext<CommandSourceStack> ctx, int amount)
            throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        PlayerSelectorArgumentResolver resolver =
                ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        Player target = resolver.resolve(ctx.getSource()).getFirst();
        String item = StringArgumentType.getString(ctx, "item").toLowerCase();

        ItemStack template = templateFor(item);
        if (template == null) {
            sender.sendMessage(error("Unknown item '" + item + "'. Allowed: "
                    + String.join(", ", allIds()) + "."));
            return Command.SINGLE_SUCCESS;
        }

        give(sender, target, item, template, amount);
        return Command.SINGLE_SUCCESS;
    }

    private static void give(CommandSender sender, Player target, String id,
                             ItemStack template, int amount) {
        int maxStack = template.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int n = Math.min(remaining, maxStack);
            ItemStack stack = template.clone();
            stack.setAmount(n);
            var overflow = target.getInventory().addItem(stack);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            remaining -= n;
        }
        sender.sendMessage(success("Gave " + amount + "× " + id + " to " + target.getName() + "."));
        target.sendMessage(success("You received " + amount + "× " + id + "."));
    }
}
