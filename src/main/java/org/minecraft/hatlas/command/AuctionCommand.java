package org.minecraft.hatlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.Item.RubyItem;
import org.minecraft.hatlas.auction.AuctionListing;
import org.minecraft.hatlas.auction.AuctionManager;
import org.minecraft.hatlas.auction.AuctionNpcManager;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.faction.FactionRole;
import org.minecraft.hatlas.gui.AuctionMarketListGui;
import org.minecraft.hatlas.gui.AuctionSellListGui;

public final class AuctionCommand {

    private AuctionCommand() {}

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED);   }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD);  }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("auction")
                .requires(src -> src.getSender().hasPermission("hatlas.auction"))
                .executes(AuctionCommand::sendHelp)
                .then(Commands.literal("help").executes(AuctionCommand::sendHelp))
                // ── /auction sell <price> [description] ─────────────────────
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> runSell(ctx, ""))
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(ctx -> runSell(ctx,
                                                StringArgumentType.getString(ctx, "description"))))))
                // ── /auction market ─────────────────────────────────────────
                .then(Commands.literal("market")
                        .executes(ctx -> {
                            Player player = playerOrNull(ctx);
                            if (player == null) return Command.SINGLE_SUCCESS;
                            new AuctionMarketListGui(player).open(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ── /auction sales ──────────────────────────────────────────
                .then(Commands.literal("sales")
                        .executes(ctx -> {
                            Player player = playerOrNull(ctx);
                            if (player == null) return Command.SINGLE_SUCCESS;
                            new AuctionSellListGui(player).open(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ── /auction npc spawn ──────────────────────────────────────
                .then(Commands.literal("npc")
                        .requires(src -> src.getSender().hasPermission("hatlas.auction.npc"))
                        .then(Commands.literal("spawn")
                                .executes(ctx -> {
                                    Player player = playerOrNull(ctx);
                                    if (player == null) return Command.SINGLE_SUCCESS;
                                    AuctionNpcManager.summonAuctioneer(player.getLocation());
                                    player.sendMessage(success("Auctioneer summoned at your location."));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private static int sendHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage(info("--- /auction ---"));
        sender.sendMessage(helpLine("sell <price> [description]", "List the item in your main hand on the market."));
        sender.sendMessage(helpLine("market", "Browse all market listings."));
        sender.sendMessage(helpLine("sales",  "View and retrieve your own listings."));
        sender.sendMessage(helpLine("npc spawn", "(op) Spawn an auctioneer NPC at your location."));
        return Command.SINGLE_SUCCESS;
    }

    private static Component helpLine(String sub, String desc) {
        return Component.text("/auction ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD))
                .append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    private static int runSell(CommandContext<CommandSourceStack> ctx, String description) {
        Player player = playerOrNull(ctx);
        if (player == null) return Command.SINGLE_SUCCESS;
        int price = IntegerArgumentType.getInteger(ctx, "price");

        // Seller must be in a faction — payout target is their faction vault.
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) {
            player.sendMessage(error("You must be in a faction to sell — payment goes to your faction vault."));
            return Command.SINGLE_SUCCESS;
        }
        Faction faction = FactionManager.getFaction(factionName);
        if (faction == null) {
            player.sendMessage(error("Your faction could not be resolved."));
            return Command.SINGLE_SUCCESS;
        }
        // Only the owner or a leader can transact against the faction vault.
        if (!isLeaderOrOwner(faction, player.getUniqueId())) {
            player.sendMessage(error("Only the faction owner or a leader can list items for sale."));
            return Command.SINGLE_SUCCESS;
        }

        int currentListings = AuctionManager.getBySeller(player.getUniqueId()).size();
        int maxListings = AuctionManager.getMaxListingsPerPlayer();
        if (currentListings >= maxListings) {
            player.sendMessage(error("You already have " + currentListings + " active listings (max "
                    + maxListings + "). Cancel one via /auction sales first."));
            return Command.SINGLE_SUCCESS;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.sendMessage(error("Hold the item you want to sell in your main hand."));
            return Command.SINGLE_SUCCESS;
        }
        // Ruby items are the auction's currency and faction-economy backbone — they
        // cannot themselves be auctioned (would let players bypass the vault flow).
        if (RubyItem.isRubyGem(inHand) || RubyItem.isRubyBlockOrOre(inHand)) {
            player.sendMessage(error("Ruby items cannot be listed on the market."));
            return Command.SINGLE_SUCCESS;
        }

        // Take the entire main-hand stack — copy first, then clear the slot.
        ItemStack sold = inHand.clone();
        player.getInventory().setItemInMainHand(null);

        AuctionListing listing = AuctionListing.create(
                player.getUniqueId(), factionName, sold, price, description);
        AuctionManager.addListing(listing);

        Component msg = success("Listed ")
                .append(Component.text(sold.getAmount() + "× " + sold.getType().name().toLowerCase(),
                        NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Component.text(price + " ruby", NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.GREEN));
        player.sendMessage(msg);
        return Command.SINGLE_SUCCESS;
    }

    public static boolean isLeaderOrOwner(Faction faction, java.util.UUID uuid) {
        return faction.getOwner().equals(uuid)
                || faction.getRole(uuid) == FactionRole.LEADER;
    }

    private static Player playerOrNull(CommandContext<CommandSourceStack> ctx) {
        var executor = ctx.getSource().getExecutor();
        if (executor instanceof Player p) return p;
        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
        return null;
    }
}
