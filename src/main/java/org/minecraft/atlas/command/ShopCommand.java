package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.shop.Shop;
import org.minecraft.atlas.shop.ShopManager;
import org.minecraft.atlas.shop.ShopTier;

/** Admin-only management of job shops: create, delete, list, add items, force a restock. */
public final class ShopCommand {

    private ShopCommand() {}

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED);   }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD);  }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shop")
                .requires(src -> src.getSender().hasPermission("atlas.shop.admin"))
                .executes(ShopCommand::sendHelp)
                .then(Commands.literal("help").executes(ShopCommand::sendHelp))
                // ── /shop create <name> <job> ───────────────────────────────
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (Job job : Job.values()) {
                                                if (job != Job.JOKEYRINI) b.suggest(job.name().toLowerCase());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ShopCommand::runCreate))))
                // ── /shop delete <name> ─────────────────────────────────────
                .then(Commands.literal("delete")
                        .then(shopArgument().executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            var sender = ctx.getSource().getSender();
                            if (ShopManager.deleteShop(name)) sender.sendMessage(success("Shop '" + name.toLowerCase() + "' and its NPC deleted."));
                            else sender.sendMessage(error("No shop named '" + name + "'."));
                            return Command.SINGLE_SUCCESS;
                        })))
                // ── /shop list ──────────────────────────────────────────────
                .then(Commands.literal("list").executes(ShopCommand::runList))
                // ── /shop additem <name> <common|rare> <weight> <price> ─────
                .then(Commands.literal("additem")
                        .then(shopArgument()
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (ShopTier t : ShopTier.values()) b.suggest(t.key());
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("weight", IntegerArgumentType.integer(1, 10_000))
                                                .then(Commands.argument("price", IntegerArgumentType.integer(0, 1_000_000))
                                                        .executes(ShopCommand::runAddItem))))))
                // ── /shop reset [name] ──────────────────────────────────────
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            ShopManager.resetAll();
                            ctx.getSource().getSender().sendMessage(success("All shops restocked. Next automatic restock timer restarted."));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(shopArgument().executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            Shop shop = ShopManager.getShop(name);
                            var sender = ctx.getSource().getSender();
                            if (shop == null) {
                                sender.sendMessage(error("No shop named '" + name + "'."));
                                return Command.SINGLE_SUCCESS;
                            }
                            ShopManager.resetShop(shop);
                            sender.sendMessage(success("Shop '" + shop.getName() + "' restocked."));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> shopArgument() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> {
                    ShopManager.getShops().forEach(s -> b.suggest(s.getName()));
                    return b.buildFuture();
                });
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private static int sendHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage(info("--- /shop (admin) ---"));
        sender.sendMessage(helpLine("create <name> <job>", "Create a job shop and spawn its NPC at your location."));
        sender.sendMessage(helpLine("delete <name>", "Delete a shop and remove its NPC."));
        sender.sendMessage(helpLine("list", "List all shops."));
        sender.sendMessage(helpLine("additem <name> <common|rare> <weight> <price>", "Add the item in your main hand to a shop pool (price in ruby)."));
        sender.sendMessage(helpLine("reset [name]", "Restock one shop, or all shops (restarts the timer)."));
        return Command.SINGLE_SUCCESS;
    }

    private static Component helpLine(String sub, String desc) {
        return Component.text("/shop ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD))
                .append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    private static int runCreate(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrNull(ctx);
        if (player == null) return Command.SINGLE_SUCCESS;
        String name = StringArgumentType.getString(ctx, "name");
        String jobName = StringArgumentType.getString(ctx, "job");
        Job job;
        try {
            job = Job.valueOf(jobName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(error("Unknown job '" + jobName + "'."));
            return Command.SINGLE_SUCCESS;
        }
        String err = ShopManager.createShop(name, job, player.getLocation());
        if (err != null) {
            player.sendMessage(error(err));
            return Command.SINGLE_SUCCESS;
        }
        Shop shop = ShopManager.getShop(name);
        player.sendMessage(success("Shop '" + shop.getName() + "' (" + job.getDisplayName() + ") created with "
                + shop.getPool(ShopTier.COMMON).size() + " common and "
                + shop.getPool(ShopTier.RARE).size() + " rare items from the job defaults."));
        return Command.SINGLE_SUCCESS;
    }

    private static int runList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (ShopManager.getShops().isEmpty()) {
            sender.sendMessage(info("No shops. Create one with /shop create <name> <job>."));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(info("--- Shops (" + ShopManager.getShops().size() + ") ---"));
        for (Shop shop : ShopManager.getShops()) {
            Location l = shop.getNpcLocation();
            String where = l == null || l.getWorld() == null ? "no NPC"
                    : l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
            sender.sendMessage(Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(shop.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" [" + shop.getJob().getDisplayName() + "]", shop.getJob().getColor()))
                    .append(Component.text(" common " + shop.getPool(ShopTier.COMMON).size()
                            + ", rare " + shop.getPool(ShopTier.RARE).size() + " — " + where, NamedTextColor.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runAddItem(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrNull(ctx);
        if (player == null) return Command.SINGLE_SUCCESS;
        String name = StringArgumentType.getString(ctx, "name");
        Shop shop = ShopManager.getShop(name);
        if (shop == null) {
            player.sendMessage(error("No shop named '" + name + "'."));
            return Command.SINGLE_SUCCESS;
        }
        ShopTier tier = ShopTier.fromKey(StringArgumentType.getString(ctx, "tier"));
        if (tier == null) {
            player.sendMessage(error("Tier must be 'common' or 'rare'."));
            return Command.SINGLE_SUCCESS;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.isEmpty()) {
            player.sendMessage(error("Hold the item to add in your main hand."));
            return Command.SINGLE_SUCCESS;
        }
        int weight = IntegerArgumentType.getInteger(ctx, "weight");
        int price  = IntegerArgumentType.getInteger(ctx, "price");
        String id = ShopManager.addItem(shop, tier, inHand, weight, price);
        player.sendMessage(success("Added '" + id + "' to " + shop.getName() + " (" + tier.key()
                + ") — weight " + weight + ", price " + price + " ruby."));
        return Command.SINGLE_SUCCESS;
    }

    private static Player playerOrNull(CommandContext<CommandSourceStack> ctx) {
        var executor = ctx.getSource().getExecutor();
        if (executor instanceof Player p) return p;
        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
        return null;
    }
}
