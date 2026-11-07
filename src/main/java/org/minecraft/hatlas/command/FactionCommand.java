package org.minecraft.hatlas.command;

import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.Storage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.gui.CrystalDisbandGui;
import org.minecraft.hatlas.faction.Faction;
import org.minecraft.hatlas.faction.FactionClaimBorderRenderer;
import org.minecraft.hatlas.faction.FactionClaimManager;
import org.minecraft.hatlas.faction.FactionLevelManager;
import org.minecraft.hatlas.faction.FactionManager;
import org.minecraft.hatlas.faction.FactionRole;
import org.minecraft.hatlas.teleport.HomeTeleportManager;
import org.minecraft.hatlas.donjon.DonjonManager;
import org.minecraft.hatlas.listener.SafeZoneListener;
import org.minecraft.hatlas.util.TabListManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class FactionCommand {

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

    private static String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString();
    }

    private static Component buildFactionInfo(Faction faction, boolean isInsideFaction) {
        String colorName = NamedTextColor.NAMES.key(faction.getColor());
        if (colorName == null) colorName = "white";
        String desc = (faction.getDescription() == null || faction.getDescription().isBlank())
                ? "No description set." : faction.getDescription();
        String ownerName = getPlayerName(faction.getOwner());
        int totalMembers = 1 + faction.getMembers().size();

        int level = faction.getLevel();
        int exp = faction.getExp();
        int expForNext = level < FactionLevelManager.MAX_LEVEL
                ? FactionLevelManager.getExpRequiredForLevel(level + 1) : 0;

        Component msg = Component.text("--- " + faction.getName() + " ---", faction.getColor())
                .append(Component.newline())
                .append(Component.text("Color: ", NamedTextColor.GRAY))
                .append(Component.text(colorName, faction.getColor()))
                .append(Component.newline())
                .append(Component.text("Level: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW))
                .append(Component.text(" | EXP: ", NamedTextColor.GRAY))
                .append(Component.text(level < FactionLevelManager.MAX_LEVEL
                        ? exp + "/" + expForNext : "MAX", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("Description: ", NamedTextColor.GRAY))
                .append(Component.text(desc, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Owner: ", NamedTextColor.GRAY))
                .append(Component.text(ownerName, NamedTextColor.YELLOW));

        if (isInsideFaction) {
            List<String> leaders = new ArrayList<>();
            List<String> moderators = new ArrayList<>();
            List<String> bankers = new ArrayList<>();
            List<String> plainMembers = new ArrayList<>();

            for (UUID uuid : faction.getMembers()) {
                String name = getPlayerName(uuid);
                switch (faction.getRole(uuid)) {
                    case LEADER -> leaders.add(name);
                    case MODERATOR -> moderators.add(name);
                    case BANKER -> bankers.add(name);
                    case MEMBER -> plainMembers.add(name);
                }
            }

            msg = msg.append(Component.newline())
                    .append(Component.text("Leaders: ", NamedTextColor.GRAY))
                    .append(Component.text(leaders.isEmpty() ? "None" : String.join(", ", leaders), NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("Moderators: ", NamedTextColor.GRAY))
                    .append(Component.text(moderators.isEmpty() ? "None" : String.join(", ", moderators), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("Bankers: ", NamedTextColor.GRAY))
                    .append(Component.text(bankers.isEmpty() ? "None" : String.join(", ", bankers), NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("Members (" + totalMembers + "):", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("  " + ownerName, NamedTextColor.WHITE))
                    .append(Component.text(" [Owner]", NamedTextColor.YELLOW));

            for (String name : leaders) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE))
                        .append(Component.text(" [Leader]", NamedTextColor.GOLD));
            }
            for (String name : moderators) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE))
                        .append(Component.text(" [Moderator]", NamedTextColor.AQUA));
            }
            for (String name : bankers) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE))
                        .append(Component.text(" [Banker]", NamedTextColor.GREEN));
            }
            for (String name : plainMembers) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE));
            }

            // Skill points — shown only to own faction members
            msg = msg.append(Component.newline())
                    .append(Component.text("Skill Points: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(faction.getSkillPoints()), NamedTextColor.LIGHT_PURPLE));

            // Vault ruby balance — shown only to own faction members
            int rubyGems = org.minecraft.hatlas.faction.FactionVault.countGems(faction);
            msg = msg.append(Component.newline())
                    .append(Component.text("Vault Balance: ", NamedTextColor.GRAY))
                    .append(Component.text(rubyGems + (rubyGems == 1 ? " ruby gem" : " ruby gems"),
                            NamedTextColor.RED));

            // Territory info — only visible to own faction members
            int totalClaims    = HatlasCrystalManager.getTotalClaimedChunks(faction.getName());
            int totalCapacity  = HatlasCrystalManager.getTotalClaimCapacity(faction.getName());
            msg = msg.append(Component.newline())
                    .append(Component.text("Claims: ", NamedTextColor.GRAY))
                    .append(Component.text(totalClaims + " / " + totalCapacity + " chunk(s)", NamedTextColor.GREEN));

            // Crystal protections — header + per-crystal lists.
            // Each crystal's protections are listed greatest-to-lowest duration with their
            // current status (ready / active / regenerate). Regeneration is sequential:
            // only the SHORTEST broken protection ("head") is currently regenerating;
            // longer broken ones are queued behind it.
            long now = System.currentTimeMillis();
            boolean printedProtectionHeader = false;
            for (HatlasCrystal crystal : HatlasCrystalManager.getFactionCrystals(faction.getName())) {
                if (crystal.getPurchasedProtections().isEmpty()) continue;

                if (!printedProtectionHeader) {
                    msg = msg.append(Component.newline())
                            .append(Component.text("Protection", NamedTextColor.GRAY));
                    printedProtectionHeader = true;
                }

                String label = crystal.getName().isEmpty() ? "Crystal" : "'" + crystal.getName() + "'";
                msg = msg.append(Component.newline())
                        .append(Component.text(label + ":", NamedTextColor.GRAY));

                Long regenHead = null;
                for (Long d : crystal.getBrokenProtections().keySet()) {
                    if (regenHead == null || d < regenHead) regenHead = d;
                }

                List<Long> sortedProtections = new ArrayList<>(crystal.getPurchasedProtections());
                sortedProtections.sort(Comparator.reverseOrder());

                for (Long d : sortedProtections) {
                    Long brokenAt = crystal.getBrokenProtections().get(d);
                    Component statusTag;
                    if (brokenAt == null) {
                        statusTag = Component.text(" [READY]", NamedTextColor.GREEN);
                    } else if (now < brokenAt + d) {
                        long remaining = (brokenAt + d) - now;
                        statusTag = Component.text(" [ACTIVE — " + formatDuration(remaining) + " left]",
                                NamedTextColor.AQUA);
                    } else if (d.equals(regenHead)) {
                        long regenAt = brokenAt + 2 * d;
                        long remaining = Math.max(0, regenAt - now);
                        statusTag = Component.text(" [REGENERATING — " + formatDuration(remaining) + " left]",
                                NamedTextColor.YELLOW);
                    } else {
                        statusTag = Component.text(" [REGENERATING — queued]", NamedTextColor.YELLOW);
                    }
                    msg = msg.append(Component.newline())
                            .append(Component.text("  " + formatDuration(d), NamedTextColor.WHITE))
                            .append(statusTag);
                }
            }
        } else {
            msg = msg.append(Component.newline())
                    .append(Component.text("Members: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(totalMembers), NamedTextColor.WHITE));
        }

        return msg;
    }

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/faction ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }

    private static int usage(net.kyori.adventure.audience.Audience audience, String syntax) {
        audience.sendMessage(Component.text("Usage: /faction " + syntax, NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    private static String formatDuration(long ms) {
        long secs = Math.max(0, ms) / 1000L;
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }


    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("faction")
                .requires(src -> src.getSender().hasPermission("hatlas.faction"))
                .executes(ctx -> {
                    var sender = ctx.getSource().getSender();
                    Component help = Component.text("--- Faction Commands ---", NamedTextColor.GOLD)
                            .append(Component.newline()).append(helpEntry("create", "<name>", "Create a new faction"))
                            .append(Component.newline()).append(helpEntry("invite", "<player>", "Invite a player (moderator+)"))
                            .append(Component.newline()).append(helpEntry("accept", "", "Accept a pending invitation"))
                            .append(Component.newline()).append(helpEntry("decline", "", "Decline a pending invitation"))
                            .append(Component.newline()).append(helpEntry("leave", "", "Leave your current faction"))
                            .append(Component.newline()).append(helpEntry("rename", "<name>", "Rename your faction (leader+)"))
                            .append(Component.newline()).append(helpEntry("description", "[text]", "View or set description (leader+)"))
                            .append(Component.newline()).append(helpEntry("color", "<color>", "Change faction color (leader+)"))
                            .append(Component.newline()).append(helpEntry("promote", "<player>", "Promote a member (owner only)"))
                            .append(Component.newline()).append(helpEntry("demote", "<player>", "Demote a member (owner only)"))
                            .append(Component.newline()).append(helpEntry("transfer", "<player>", "Transfer ownership (owner only)"))
                            .append(Component.newline()).append(helpEntry("kick", "<player>", "Kick a member (owner only)"))
                            .append(Component.newline()).append(helpEntry("disband", "", "Delete your faction (owner only)"))
                            .append(Component.newline()).append(helpEntry("info", "[faction]", "Show faction information"))
                            .append(Component.newline()).append(helpEntry("list", "", "List all factions"))
                            .append(Component.newline()).append(helpEntry("members", "", "Show members of your faction"))
                            .append(Component.newline()).append(helpEntry("home", "[crystal]", "Teleport to faction home"))
                            .append(Component.newline()).append(helpEntry("claim", "", "Claim the chunk you are standing in (requires crystal capacity)"))
                            .append(Component.newline()).append(helpEntry("showclaim", "<true|false>", "Toggle claim border particle visualization"))
                            .append(Component.newline()).append(helpEntry("unclaim", "", "Unclaim the chunk you are standing in (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("sethome", "<crystal>", "Set the home for a crystal at your location (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("outpost", "", "Place a second Hatlas Crystal (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("ally", "<faction>", "Send an alliance request (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("allyaccept", "<faction>", "Accept an alliance request (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("allydeny", "<faction>", "Deny an alliance request (owner/leader)"))
                            .append(Component.newline()).append(helpEntry("unally", "<faction>", "Break an alliance (owner/leader)"));

                    if (sender.hasPermission("hatlas.faction.debug")) {
                        help = help
                                .append(Component.newline()).append(helpEntry("exp", "add <amount>", "[debug] Add exp to faction"))
                                .append(Component.newline()).append(helpEntry("exp", "set <amount>", "[debug] Set faction exp"))
                                .append(Component.newline()).append(helpEntry("level", "set <level>", "[debug] Set faction level (0-99)"))
                                .append(Component.newline()).append(helpEntry("skillpoints", "set <amount>", "[debug] Set faction skill points"));
                    }
                    if (sender.hasPermission("hatlas.faction.admin.disband")) {
                        help = help
                                .append(Component.newline()).append(helpEntry("disband", "<faction>", "[admin] Forcibly disband any faction"));
                    }
                    if (sender.hasPermission("hatlas.faction.admin.join")) {
                        help = help
                                .append(Component.newline()).append(helpEntry("join", "<player> <faction>", "[admin] Force-join a player into a faction"));
                    }

                    sender.sendMessage(help);
                    return Command.SINGLE_SUCCESS;
                })
                // ----- create -----
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.create"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "create <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name");

                                    // Determine the target chunk first so we can validate it
                                    Chunk chunk = player.getLocation().getChunk();

                                    // Reject if the chunk is already claimed by another faction
                                    String existingClaim = FactionClaimManager.getClaimingFaction(
                                            player.getWorld().getName(), chunk.getX(), chunk.getZ());
                                    if (existingClaim != null) {
                                        player.sendMessage(error("This chunk is already claimed by faction '"
                                                + existingClaim + "'. Move to an unclaimed area."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    // Reject if the chunk belongs to a donjon
                                    if (DonjonManager.isChunkInDonjon(
                                            player.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                                        player.sendMessage(error("Cannot create a faction inside a donjon area."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    // Reject if inside spawn protection
                                    if (SafeZoneListener.isInSafeZone(player.getLocation())) {
                                        player.sendMessage(error("You cannot create a faction inside the spawn protection zone."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.getPlayerFaction(player.getUniqueId()) != null) {
                                        player.sendMessage(error("You are already in a faction. Leave or disband it first."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (!FactionManager.createFaction(name, player.getUniqueId())) {
                                        player.sendMessage(error("A faction with that name already exists."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    player.sendMessage(success("Faction '" + name + "' created successfully!"));
                                    player.sendMessage(info("Don't forget to pick a job using the /job command!"));
                                    TabListManager.updatePlayer(player);

                                    int playerX = player.getLocation().getBlockX();
                                    int playerZ = player.getLocation().getBlockZ();
                                    int playerY = player.getLocation().getBlockY();

                                    Location spawnLoc = new Location(player.getWorld(),
                                            playerX + 0.5, playerY + 1.0, playerZ + 0.5);

                                    EnderCrystal crystalEntity = spawnLoc.getWorld().spawn(spawnLoc, EnderCrystal.class);
                                    crystalEntity.setShowingBottom(true);

                                    HatlasCrystal hatlasCrystal = HatlasCrystalManager.register(crystalEntity, name);

                                    // Claim the chunk where the crystal was spawned
                                    FactionClaimManager.initializeClaim(name,
                                            player.getWorld().getName(), chunk.getX(), chunk.getZ());

                                    // Auto-name the founding crystal "main"; player can shift-click
                                    // the nether-star in CrystalMainGui later to rename.
                                    HatlasCrystalManager.assignName(hatlasCrystal, "main");
                                    // Auto-set faction home to the player's current spot (same chunk
                                    // as the crystal — avoids spawning the home inside the entity).
                                    hatlasCrystal.setHome(player.getLocation());
                                    HatlasCrystalManager.saveHome(hatlasCrystal);

                                    player.sendMessage(success("Crystal 'main' placed and faction home set to your location."));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- invite -----
                .then(Commands.literal("invite")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.invite"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "invite <player>"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (target.equals(player)) {
                                        player.sendMessage(error("You cannot invite yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(info("Invitation sent to " + target.getName() + "."));
                                        target.sendMessage(
                                                info(player.getName() + " invited you to join faction '")
                                                        .append(Component.text(factionName, NamedTextColor.GOLD))
                                                        .append(info("'.  "))
                                                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                                                .clickEvent(ClickEvent.runCommand("/faction accept")))
                                                        .append(Component.text("  "))
                                                        .append(Component.text("[Decline]", NamedTextColor.RED)
                                                                .clickEvent(ClickEvent.runCommand("/faction decline")))
                                        );
                                    } else {
                                        player.sendMessage(error("Could not invite " + target.getName() + ". You must be at least a Moderator, and the player must not already be in a faction."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- accept -----
                .then(Commands.literal("accept")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.accept"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionName = FactionManager.acceptInvitation(player.getUniqueId());

                            if (factionName != null) {
                                player.sendMessage(success("You joined faction '" + factionName + "'."));
                                FactionManager.broadcastToFaction(factionName,
                                        info(player.getName() + " joined the faction."),
                                        player.getUniqueId());
                                player.sendMessage(info("Don't forget to pick a job using the /job command!"));
                                TabListManager.updatePlayer(player);
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- decline -----
                .then(Commands.literal("decline")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.decline"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (FactionManager.declineInvitation(player.getUniqueId())) {
                                player.sendMessage(Component.text("Invitation declined.", NamedTextColor.RED));
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- rename -----
                .then(Commands.literal("rename")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.rename"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "rename <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String newName = StringArgumentType.getString(ctx, "name");

                                    if (FactionManager.renameFaction(newName, player.getUniqueId())) {
                                        player.sendMessage(success("Faction renamed to '" + newName + "'."));
                                        FactionManager.broadcastToFaction(newName,
                                                info(player.getName() + " renamed the faction to '" + newName + "'."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not rename. You must be a Leader or Owner, and the new name must not already be taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- description -----
                .then(Commands.literal("description")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.description"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Faction faction = FactionManager.getFaction(factionName);
                            String desc = faction.getDescription();
                            if (desc == null || desc.isBlank()) {
                                player.sendMessage(info("Your faction has no description set."));
                            } else {
                                player.sendMessage(Component.text("--- " + factionName + " ---", faction.getColor())
                                        .append(Component.newline())
                                        .append(Component.text(desc, NamedTextColor.WHITE)));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String text = StringArgumentType.getString(ctx, "text");

                                    if (FactionManager.setFactionDescription(player.getUniqueId(), text)) {
                                        player.sendMessage(success("Faction description updated."));
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        FactionManager.broadcastToFaction(factionName,
                                                info(player.getName() + " updated the faction description."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not update description. You must be a Leader or Owner."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- color -----
                .then(Commands.literal("color")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.color"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "color <color>"))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    NamedTextColor.NAMES.keys().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String colorName = StringArgumentType.getString(ctx, "color");
                                    NamedTextColor color = NamedTextColor.NAMES.value(colorName);

                                    if (color == null) {
                                        player.sendMessage(error("Unknown color '" + colorName + "'. Use tab-complete to see valid colors."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String factionNameForColor = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (FactionManager.setFactionColor(player.getUniqueId(), color)) {
                                        Component colorMsg = info("Faction color changed to ")
                                                .append(Component.text(colorName, color))
                                                .append(info("."));
                                        player.sendMessage(colorMsg);
                                        FactionManager.broadcastToFaction(factionNameForColor,
                                                info(player.getName() + " changed the faction color to ")
                                                        .append(Component.text(colorName, color))
                                                        .append(info(".")),
                                                player.getUniqueId());
                                        TabListManager.updateFactionMembers(factionNameForColor);
                                    } else {
                                        player.sendMessage(error("Could not change color. You must be a Leader or Owner."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- promote -----
                .then(Commands.literal("promote")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.promote"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "promote <player>"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (target.equals(player)) {
                                        player.sendMessage(error("You cannot promote yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.promotePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        FactionRole newRole = FactionManager.getPlayerRole(target.getUniqueId());
                                        String roleName = newRole != null ? newRole.displayName() : "Leader";
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success(target.getName() + " has been promoted to " + roleName + "."));
                                        target.sendMessage(info("You have been promoted to " + roleName + " in your faction."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " has been promoted to " + roleName + "."),
                                                player.getUniqueId(), target.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not promote " + target.getName() + ". Only the Owner can promote, and the player must be in your faction and not already at the highest role."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- demote -----
                .then(Commands.literal("demote")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.demote"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "demote <player>"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (target.equals(player)) {
                                        player.sendMessage(error("You cannot demote yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.demotePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        FactionRole newRole = FactionManager.getPlayerRole(target.getUniqueId());
                                        String roleName = newRole != null ? newRole.displayName() : "Member";
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success(target.getName() + " has been demoted to " + roleName + "."));
                                        target.sendMessage(info("You have been demoted to " + roleName + " in your faction."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " has been demoted to " + roleName + "."),
                                                player.getUniqueId(), target.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not demote " + target.getName() + ". Only the Owner can demote, and the player must be in your faction and not already at the lowest role."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- transfer -----
                .then(Commands.literal("transfer")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.transfer"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "transfer <player>"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (target.equals(player)) {
                                        player.sendMessage(error("You cannot transfer ownership to yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.transferOwnership(player.getUniqueId(), target.getUniqueId())) {
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success("Ownership of the faction has been transferred to " + target.getName() + "."));
                                        target.sendMessage(info("You are now the Owner of faction '" + factionName + "'."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " is now the Owner of the faction."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not transfer ownership. You must be the Owner and the target must be a member of your faction."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- kick -----
                .then(Commands.literal("kick")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.kick"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "kick <player>"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (exec instanceof Player p) {
                                        String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                        if (fn != null) {
                                            for (UUID mid : FactionManager.getFactionPlayers(fn)) {
                                                if (mid.equals(p.getUniqueId())) continue;
                                                OfflinePlayer op = Bukkit.getOfflinePlayer(mid);
                                                if (op.getName() != null) builder.suggest(op.getName());
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    String factionNameForKick = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (factionNameForKick == null) {
                                        player.sendMessage(error("You are not in a faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    // Resolve target UUID by name from faction member list (supports offline players)
                                    UUID targetUUID = null;
                                    for (UUID memberUUID : FactionManager.getFactionPlayers(factionNameForKick)) {
                                        if (memberUUID.equals(player.getUniqueId())) continue;
                                        OfflinePlayer op = Bukkit.getOfflinePlayer(memberUUID);
                                        if (targetName.equalsIgnoreCase(op.getName())) {
                                            targetUUID = memberUUID;
                                            break;
                                        }
                                    }

                                    if (targetUUID == null) {
                                        player.sendMessage(error("Player '" + targetName + "' is not a member of your faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.kickPlayer(player.getUniqueId(), targetUUID)) {
                                        player.sendMessage(info(targetName + " has been kicked from the faction."));
                                        Player targetOnline = Bukkit.getPlayer(targetUUID);
                                        if (targetOnline != null) {
                                            targetOnline.sendMessage(error("You have been kicked from the faction."));
                                            TabListManager.updatePlayer(targetOnline);
                                        }
                                        FactionManager.broadcastToFaction(factionNameForKick,
                                                info(targetName + " has been kicked from the faction."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not kick " + targetName + ". You can only kick members with a lower role than yours."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- disband / admin disband -----
                .then(Commands.literal("disband")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.disband")
                                || src.getSender().hasPermission("hatlas.faction.admin.disband"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForDisband = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionNameForDisband == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Faction grpForDisband = FactionManager.getFaction(factionNameForDisband);
                            if (!grpForDisband.getOwner().equals(player.getUniqueId())) {
                                player.sendMessage(error("Only the Owner can disband the faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            CrystalDisbandGui.open(player, grpForDisband);
                            return Command.SINGLE_SUCCESS;
                        })
                        // ----- admin disband <faction> -----
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission("hatlas.faction.admin.disband"))
                                .suggests((ctx, builder) -> {
                                    FactionManager.getFactions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String targetFaction = StringArgumentType.getString(ctx, "faction");

                                    if (FactionManager.getFaction(targetFaction) == null) {
                                        sender.sendMessage(error("Faction '" + targetFaction + "' does not exist."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    FactionManager.broadcastToFaction(targetFaction,
                                            error("Your faction has been forcibly disbanded by an administrator."),
                                            null);
                                    Faction disbandedFaction = FactionManager.getFaction(targetFaction);
                                    NamedTextColor disbandColor = disbandedFaction != null
                                            ? disbandedFaction.getColor() : NamedTextColor.WHITE;
                                    Component globalMsg = Component.text("☠ Faction ", NamedTextColor.RED)
                                            .append(Component.text("[", NamedTextColor.GRAY))
                                            .append(Component.text(targetFaction, disbandColor))
                                            .append(Component.text("]", NamedTextColor.GRAY))
                                            .append(Component.text(" has been disbanded by an administrator.",
                                                    NamedTextColor.RED));
                                    Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(globalMsg));
                                    FactionManager.disbandFaction(targetFaction);
                                    sender.sendMessage(success("Faction '" + targetFaction + "' has been disbanded."));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- admin join -----
                .then(Commands.literal("join")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.admin.join"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "join <player> <faction>"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            FactionManager.getFactions().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            List<Player> resolved = resolver.resolve(ctx.getSource());
                                            if (resolved.isEmpty()) {
                                                sender.sendMessage(error("Player not found or not online."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Player target = resolved.getFirst();
                                            String targetFaction = StringArgumentType.getString(ctx, "faction");
                                            if (FactionManager.getPlayerFaction(target.getUniqueId()) != null) {
                                                sender.sendMessage(error(target.getName() + " is already in a faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            if (FactionManager.getFaction(targetFaction) == null) {
                                                sender.sendMessage(error("Faction '" + targetFaction + "' does not exist."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            FactionManager.forceJoin(target.getUniqueId(), targetFaction);
                                            sender.sendMessage(success("Force-joined " + target.getName() + " into '" + targetFaction + "'."));
                                            target.sendMessage(info("An administrator added you to faction '" + targetFaction + "'."));
                                            FactionManager.broadcastToFaction(targetFaction,
                                                    info(target.getName() + " was added to the faction by an admin."),
                                                    target.getUniqueId());
                                            TabListManager.updatePlayer(target);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // ----- leave -----
                .then(Commands.literal("leave")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.leave"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForLeave = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionNameForLeave == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (FactionManager.leaveFaction(player.getUniqueId())) {
                                player.sendMessage(success("You left the faction '" + factionNameForLeave + "'."));
                                FactionManager.broadcastToFaction(factionNameForLeave,
                                        info(player.getName() + " left the faction."),
                                        player.getUniqueId());
                                TabListManager.updatePlayer(player);
                            } else {
                                player.sendMessage(error("You are the Owner — use /faction disband to disband the faction instead."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- info -----
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.info"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String ownFaction = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (ownFaction == null) {
                                player.sendMessage(error("You are not in any faction. Use /faction info <name> to look up a faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            player.sendMessage(buildFactionInfo(FactionManager.getFaction(ownFaction), true));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    FactionManager.getFactions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String targetFactionName = StringArgumentType.getString(ctx, "faction");
                                    Faction faction = FactionManager.getFaction(targetFactionName);

                                    if (faction == null) {
                                        player.sendMessage(error("Faction '" + targetFactionName + "' does not exist."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    boolean isInsideFaction = targetFactionName.equals(
                                            FactionManager.getPlayerFaction(player.getUniqueId()));

                                    player.sendMessage(buildFactionInfo(faction, isInsideFaction));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- list -----
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.list"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            var factionMap = FactionManager.getFactions();
                            if (factionMap.isEmpty()) {
                                player.sendMessage(info("No factions exist yet."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Component list = Component.text("--- Factions (" + factionMap.size() + ") ---", NamedTextColor.GOLD);
                            for (Faction g : factionMap.values()) {
                                int total = 1 + g.getMembers().size();
                                String desc = (g.getDescription() == null || g.getDescription().isBlank())
                                        ? "No description set." : g.getDescription();
                                list = list.append(Component.newline())
                                        .append(Component.text(g.getName(), g.getColor()))
                                        .append(Component.text(" [LvL." + g.getLevel() + "]", NamedTextColor.YELLOW))
                                        .append(Component.text(" (" + total + " member" + (total == 1 ? "" : "s") + ")", NamedTextColor.GRAY))
                                        .append(Component.text(" - " + desc, NamedTextColor.DARK_GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- home -----
                .then(Commands.literal("home")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.home"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Location home = HatlasCrystalManager.getFirstHome(factionName);
                            if (home == null) {
                                player.sendMessage(error("Your faction has no home crystal yet."));
                                return Command.SINGLE_SUCCESS;
                            }
                            HomeTeleportManager.startTeleport(player, home, "faction home");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("crystal", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (exec instanceof Player p) {
                                        String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                        if (fn != null) {
                                            HatlasCrystalManager.getFactionCrystals(fn)
                                                    .forEach(c -> builder.suggest(c.getName()));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (factionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String crystalName = StringArgumentType.getString(ctx, "crystal");
                                    HatlasCrystal crystal = HatlasCrystalManager.getCrystalByName(factionName, crystalName);
                                    if (crystal == null) {
                                        player.sendMessage(error("No crystal named '" + crystalName + "' found in your faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Location home = crystal.getHome();
                                    if (home == null) {
                                        player.sendMessage(error("Crystal '" + crystalName + "' has no home set."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    HomeTeleportManager.startTeleport(player, home, crystalName);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- claim -----
                .then(Commands.literal("claim")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.claim"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            org.bukkit.Chunk chunk = player.getLocation().getChunk();
                            String worldName = player.getWorld().getName();
                            int cx = chunk.getX(), cz = chunk.getZ();
                            String existing = FactionClaimManager.getClaimingFaction(worldName, cx, cz);
                            if (existing != null) {
                                player.sendMessage(error("This chunk is already claimed by '" + existing + "'."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (DonjonManager.isChunkInDonjon(worldName, cx, cz)) {
                                player.sendMessage(error("Cannot claim a chunk inside a donjon area."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (SafeZoneListener.isInSafeZone(player.getLocation())) {
                                player.sendMessage(error("Cannot claim a chunk inside the spawn protection zone."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (!FactionClaimManager.hasAdjacentClaim(factionName, worldName, cx, cz)) {
                                player.sendMessage(error("You can only claim chunks adjacent to your faction's existing territory."));
                                return Command.SINGLE_SUCCESS;
                            }
                            HatlasCrystal crystalForClaim = HatlasCrystalManager.findCrystalForClaim(factionName, worldName, cx, cz);
                            if (crystalForClaim == null) {
                                player.sendMessage(error("No crystal with available claim capacity. Purchase more claims via your hatlas crystal."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String chunkKey = worldName + ":" + cx + ":" + cz;
                            crystalForClaim.getClaimedChunks().add(chunkKey);
                            FactionClaimManager.claimChunk(factionName, worldName, cx, cz);
                            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
                            int used = HatlasCrystalManager.getTotalClaimedChunks(factionName);
                            int cap  = HatlasCrystalManager.getTotalClaimCapacity(factionName);
                            player.sendMessage(success("Chunk claimed! Claims used: " + used + " / " + cap + "."));
                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- unclaim -----
                .then(Commands.literal("unclaim")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.unclaim"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Faction faction = FactionManager.getFaction(factionName);
                            boolean isOwner = faction.getOwner().equals(player.getUniqueId());
                            boolean isLeader = faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                            if (!isOwner && !isLeader) {
                                player.sendMessage(error("Only the Owner or a Leader can unclaim chunks."));
                                return Command.SINGLE_SUCCESS;
                            }
                            org.bukkit.Chunk chunk = player.getLocation().getChunk();
                            String worldName = player.getWorld().getName();
                            int cx = chunk.getX(), cz = chunk.getZ();
                            String owner = FactionClaimManager.getClaimingFaction(worldName, cx, cz);
                            if (!factionName.equals(owner)) {
                                player.sendMessage(error("This chunk is not claimed by your faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String unclaimKey = worldName + ":" + cx + ":" + cz;
                            // Block unclaiming the chunk that hosts an hatlas crystal entity —
                            // the crystal anchors that chunk and must be destroyed (not unclaimed) to free it.
                            for (HatlasCrystal c : HatlasCrystalManager.getFactionCrystals(factionName)) {
                                EnderCrystal ce = c.getEntity();
                                if (ce == null) continue;
                                Location cloc = ce.getLocation();
                                if (cloc.getWorld() == null || !cloc.getWorld().getName().equals(worldName)) continue;
                                if ((cloc.getBlockX() >> 4) == cx && (cloc.getBlockZ() >> 4) == cz) {
                                    String label = c.getName().isEmpty() ? "hatlas crystal" : "'" + c.getName() + "' crystal";
                                    player.sendMessage(error("This chunk hosts the " + label + " — destroy the crystal to release it."));
                                    return Command.SINGLE_SUCCESS;
                                }
                            }
                            // Remove from the crystal that owns this chunk
                            for (HatlasCrystal c : HatlasCrystalManager.getFactionCrystals(factionName)) {
                                c.getClaimedChunks().remove(unclaimKey);
                            }
                            FactionClaimManager.unclaimChunk(worldName, cx, cz);
                            Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
                            int used2 = HatlasCrystalManager.getTotalClaimedChunks(factionName);
                            int cap2  = HatlasCrystalManager.getTotalClaimCapacity(factionName);
                            player.sendMessage(success("Chunk unclaimed. Claims used: " + used2 + " / " + cap2 + "."));
                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- sethome -----
                .then(Commands.literal("sethome")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.sethome"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "sethome <crystal>"))
                        .then(Commands.argument("crystal", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (exec instanceof Player p) {
                                        String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                        if (fn != null) {
                                            HatlasCrystalManager.getFactionCrystals(fn)
                                                    .forEach(c -> builder.suggest(c.getName()));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (factionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction faction = FactionManager.getFaction(factionName);
                                    boolean isOwner = faction.getOwner().equals(player.getUniqueId());
                                    boolean isLeader = faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                                    if (!isOwner && !isLeader) {
                                        player.sendMessage(error("Only the Owner or a Leader can set a crystal home."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Chunk hereChunk = player.getLocation().getChunk();
                                    String hereOwner = FactionClaimManager.getClaimingFaction(
                                            hereChunk.getWorld().getName(), hereChunk.getX(), hereChunk.getZ());
                                    if (!factionName.equals(hereOwner)) {
                                        player.sendMessage(error("You can only set a faction home inside your own claimed territory."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String crystalName = StringArgumentType.getString(ctx, "crystal");
                                    HatlasCrystal crystal = HatlasCrystalManager.getCrystalByName(factionName, crystalName);
                                    if (crystal == null) {
                                        player.sendMessage(error("No crystal named '" + crystalName + "' in your faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    crystal.setHome(player.getLocation());
                                    HatlasCrystalManager.saveHome(crystal);
                                    player.sendMessage(success("Home for crystal '" + crystalName + "' set to your current location."));
                                    FactionManager.broadcastToFaction(factionName,
                                            info(player.getName() + " set the home for crystal '" + crystalName + "'."),
                                            player.getUniqueId());
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- upgrade -----
                // ----- level (debug) -----
                .then(Commands.literal("level")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.debug"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "level set <0-99>"))
                        .then(Commands.literal("set")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "level set <0-99>"))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, FactionLevelManager.MAX_LEVEL))
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                            if (factionName == null) {
                                                player.sendMessage(error("You are not in any faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int level = IntegerArgumentType.getInteger(ctx, "value");
                                            Faction faction = FactionManager.getFaction(factionName);
                                            faction.setLevel(level);
                                            faction.setExp(0);
                                            for (HatlasCrystal c : HatlasCrystalManager.getFactionCrystals(factionName)) {
                                                c.updateNametag();
                                            }
                                            player.sendMessage(success("[DEBUG] Faction level set to " + level + "."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // ----- exp (debug) -----
                .then(Commands.literal("exp")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.debug"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "exp set|add <amount>"))
                        .then(Commands.literal("set")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "exp set <amount>"))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                            if (factionName == null) {
                                                player.sendMessage(error("You are not in any faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int exp = IntegerArgumentType.getInteger(ctx, "value");
                                            FactionManager.getFaction(factionName).setExp(exp);
                                            player.sendMessage(success("[DEBUG] Faction exp set to " + exp + "."));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("add")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "exp add <amount>"))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                            if (factionName == null) {
                                                player.sendMessage(error("You are not in any faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int amount = IntegerArgumentType.getInteger(ctx, "value");
                                            List<Integer> reached = FactionManager.addExpToFaction(factionName, amount);

                                            Faction faction = FactionManager.getFaction(factionName);
                                            int currentLevel = faction.getLevel();
                                            int currentExp = faction.getExp();
                                            int expForNext = currentLevel < FactionLevelManager.MAX_LEVEL
                                                    ? FactionLevelManager.getExpRequiredForLevel(currentLevel + 1) : 0;

                                            player.sendMessage(success("[DEBUG] Added " + amount + " exp."));
                                            player.sendMessage(info("Level: " + currentLevel
                                                    + " | EXP: " + (currentLevel < FactionLevelManager.MAX_LEVEL
                                                    ? currentExp + "/" + expForNext : "MAX")));

                                            if (!reached.isEmpty()) {
                                                FactionManager.broadcastToFaction(factionName,
                                                        Component.text("Faction reached level " + currentLevel + "! New skill points available.", NamedTextColor.GOLD),
                                                        null);
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // ----- skillpoints (debug) -----
                .then(Commands.literal("skillpoints")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.debug"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "skillpoints set <amount>"))
                        .then(Commands.literal("set")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "skillpoints set <amount>"))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                            if (factionName == null) {
                                                player.sendMessage(error("You are not in any faction."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                            FactionManager.getFaction(factionName).setSkillPoints(value);
                                            player.sendMessage(success("[DEBUG] Faction skill points set to " + value + "."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // ----- outpost -----
                .then(Commands.literal("outpost")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.outpost"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Faction faction = FactionManager.getFaction(factionName);
                            boolean isOwner = faction.getOwner().equals(player.getUniqueId());
                            boolean isLeader = faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                            if (!isOwner && !isLeader) {
                                player.sendMessage(error("Only the Owner or a Leader can place an outpost crystal."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (!faction.isOutpostUnlocked()) {
                                player.sendMessage(error("Your faction must unlock the Outpost skill in the Hatlas Crystal skill menu first."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (HatlasCrystalManager.getFactionCrystals(factionName).size() >= 2) {
                                player.sendMessage(error("Your faction already has 2 Hatlas Crystals. No more outposts can be placed."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Chunk chunk = player.getLocation().getChunk();
                            String existingClaim = FactionClaimManager.getClaimingFaction(
                                    player.getWorld().getName(), chunk.getX(), chunk.getZ());
                            if (existingClaim != null) {
                                player.sendMessage(error("This chunk is already claimed. Move to an unclaimed area."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (DonjonManager.isChunkInDonjon(
                                    player.getWorld().getName(), chunk.getX(), chunk.getZ())) {
                                player.sendMessage(error("Cannot place an outpost crystal inside a donjon area."));
                                return Command.SINGLE_SUCCESS;
                            }
                            if (SafeZoneListener.isInSafeZone(player.getLocation())) {
                                player.sendMessage(error("You cannot place an outpost crystal inside the spawn protection zone."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Location playerLoc = player.getLocation();
                            Location spawnLoc = new Location(player.getWorld(),
                                    playerLoc.getBlockX() + 0.5,
                                    playerLoc.getBlockY() + 1.0,
                                    playerLoc.getBlockZ() + 0.5);

                            EnderCrystal crystalEntity = spawnLoc.getWorld().spawn(spawnLoc, EnderCrystal.class);
                            crystalEntity.setShowingBottom(true);
                            HatlasCrystal hatlasCrystal = HatlasCrystalManager.register(crystalEntity, factionName);
                            hatlasCrystal.setOutpost(true);
                            FactionClaimManager.claimChunk(factionName,
                                    player.getWorld().getName(), chunk.getX(), chunk.getZ());

                            // Auto-name the outpost. Pick a unique suffix if "outpost" is taken
                            // — outposts are capped at 1 per faction today, but stay resilient.
                            String outpostName = "outpost";
                            int suffix = 2;
                            while (HatlasCrystalManager.hasCrystalWithName(factionName, outpostName)) {
                                outpostName = "outpost-" + suffix++;
                            }
                            HatlasCrystalManager.assignName(hatlasCrystal, outpostName);

                            player.sendMessage(success("Outpost crystal '" + outpostName + "' placed!"));
                            player.sendMessage(info("Use /faction sethome " + outpostName + " to set its home location."));
                            FactionManager.broadcastToFaction(factionName,
                                    info(player.getName() + " placed a faction outpost crystal!"),
                                    player.getUniqueId());
                            return Command.SINGLE_SUCCESS;
                        }))
                // ----- ally -----
                .then(Commands.literal("ally")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.ally"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "ally <faction>"))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    FactionManager.getFactions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String myFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (myFactionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction myFaction = FactionManager.getFaction(myFactionName);
                                    boolean isOwner = myFaction.getOwner().equals(player.getUniqueId());
                                    boolean isLeader = myFaction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                                    if (!isOwner && !isLeader) {
                                        player.sendMessage(error("Only the Owner or a Leader can manage alliances."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String targetName = StringArgumentType.getString(ctx, "faction");
                                    if (myFactionName.equalsIgnoreCase(targetName)) {
                                        player.sendMessage(error("You cannot ally your own faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction targetFaction = FactionManager.getFaction(targetName);
                                    if (targetFaction == null) {
                                        player.sendMessage(error("Faction '" + targetName + "' does not exist."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (FactionManager.areAllied(myFactionName, targetName)) {
                                        player.sendMessage(error("You are already allied with " + targetName + "."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (FactionManager.hasPendingAllyRequest(myFactionName, targetName)) {
                                        player.sendMessage(error("An alliance request to " + targetName + " is already pending."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    FactionManager.sendAllyRequest(myFactionName, targetName);
                                    player.sendMessage(info("Alliance request sent to faction " + targetName + "."));

                                    // Notify online owners and leaders of the target faction
                                    Component requestMsg = info("[" + myFactionName + "] ")
                                            .append(Component.text("wants to form an alliance with your faction!  ", NamedTextColor.YELLOW))
                                            .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                                    .clickEvent(ClickEvent.runCommand("/faction allyaccept " + myFactionName)))
                                            .append(Component.text("  "))
                                            .append(Component.text("[Deny]", NamedTextColor.RED)
                                                    .clickEvent(ClickEvent.runCommand("/faction allydeny " + myFactionName)));

                                    for (Player member : FactionManager.getOnlineFactionMembers(targetName, null)) {
                                        boolean tOwner = targetFaction.getOwner().equals(member.getUniqueId());
                                        boolean tLeader = targetFaction.getRole(member.getUniqueId()) == FactionRole.LEADER;
                                        if (tOwner || tLeader) member.sendMessage(requestMsg);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- allyaccept -----
                .then(Commands.literal("allyaccept")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.ally"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "allyaccept <faction>"))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String myFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (myFactionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction myFaction = FactionManager.getFaction(myFactionName);
                                    boolean isOwner = myFaction.getOwner().equals(player.getUniqueId());
                                    boolean isLeader = myFaction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                                    if (!isOwner && !isLeader) {
                                        player.sendMessage(error("Only the Owner or a Leader can accept alliance requests."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String requestingName = StringArgumentType.getString(ctx, "faction");
                                    if (!FactionManager.hasPendingAllyRequest(requestingName, myFactionName)) {
                                        player.sendMessage(error("No pending alliance request from " + requestingName + "."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (!FactionManager.acceptAllyRequest(myFactionName, requestingName)) {
                                        player.sendMessage(error("Could not accept alliance — faction may no longer exist."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    FactionManager.broadcastToFaction(myFactionName,
                                            success("⚔ Your faction is now allied with " + requestingName + "!"), null);
                                    FactionManager.broadcastToFaction(requestingName,
                                            success("⚔ Faction " + myFactionName + " accepted your alliance request!"), null);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- allydeny -----
                .then(Commands.literal("allydeny")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.ally"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "allydeny <faction>"))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String myFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (myFactionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction myFaction = FactionManager.getFaction(myFactionName);
                                    boolean isOwner = myFaction.getOwner().equals(player.getUniqueId());
                                    boolean isLeader = myFaction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                                    if (!isOwner && !isLeader) {
                                        player.sendMessage(error("Only the Owner or a Leader can deny alliance requests."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String requestingName = StringArgumentType.getString(ctx, "faction");
                                    if (!FactionManager.hasPendingAllyRequest(requestingName, myFactionName)) {
                                        player.sendMessage(error("No pending alliance request from " + requestingName + "."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    FactionManager.denyAllyRequest(myFactionName, requestingName);
                                    player.sendMessage(Component.text("Alliance request from " + requestingName + " denied.", NamedTextColor.RED));
                                    FactionManager.broadcastToFaction(requestingName,
                                            error("Faction " + myFactionName + " denied your alliance request."), null);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- unally -----
                // /faction msg <text>
                .then(Commands.literal("msg")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.msg"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "msg <text>"))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String myFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (myFactionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction myFaction = FactionManager.getFaction(myFactionName);
                                    String text = StringArgumentType.getString(ctx, "text");
                                    net.kyori.adventure.text.format.NamedTextColor factionColor =
                                            myFaction != null ? myFaction.getColor() : net.kyori.adventure.text.format.NamedTextColor.WHITE;
                                    Component msg = Component.text("[" + myFactionName + "] ", factionColor)
                                            .append(Component.text(player.getName() + ": ", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                                            .append(Component.text(text, net.kyori.adventure.text.format.NamedTextColor.GRAY));
                                    FactionManager.broadcastToFaction(myFactionName, msg, null);
                                    return Command.SINGLE_SUCCESS;
                                })))

                .then(Commands.literal("unally")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.ally"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "unally <faction>"))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (executor instanceof Player player) {
                                        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
                                        if (fn != null) {
                                            Faction f = FactionManager.getFaction(fn);
                                            if (f != null) f.getAllies().forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String myFactionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (myFactionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Faction myFaction = FactionManager.getFaction(myFactionName);
                                    boolean isOwner = myFaction.getOwner().equals(player.getUniqueId());
                                    boolean isLeader = myFaction.getRole(player.getUniqueId()) == FactionRole.LEADER;
                                    if (!isOwner && !isLeader) {
                                        player.sendMessage(error("Only the Owner or a Leader can manage alliances."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String targetName = StringArgumentType.getString(ctx, "faction");
                                    if (!myFaction.hasAlly(targetName)) {
                                        player.sendMessage(error("You are not allied with " + targetName + "."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    myFaction.removeAlly(targetName);
                                    Faction targetFaction = FactionManager.getFaction(targetName);
                                    if (targetFaction != null) targetFaction.removeAlly(myFactionName);
                                    player.sendMessage(success("Alliance with " + targetName + " has been dissolved."));
                                    FactionManager.broadcastToFaction(myFactionName,
                                            info("Your faction ended the alliance with " + targetName + "."),
                                            player.getUniqueId());
                                    FactionManager.broadcastToFaction(targetName,
                                            info("Faction " + myFactionName + " has ended their alliance with you."),
                                            null);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- showclaim -----
                .then(Commands.literal("showclaim")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.claim"))
                        .executes(ctx -> usage(ctx.getSource().getSender(), "showclaim <true|false>"))
                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (FactionManager.getPlayerFaction(player.getUniqueId()) == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    boolean visible = BoolArgumentType.getBool(ctx, "visible");
                                    FactionClaimBorderRenderer.setVisible(player.getUniqueId(), visible);
                                    if (visible) {
                                        player.sendMessage(success("Claim borders are now visible."));
                                    } else {
                                        player.sendMessage(info("Claim borders are now hidden."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ----- balance -----
                .then(Commands.literal("balance")
                        .requires(src -> src.getSender().hasPermission("hatlas.faction.balance"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String fn = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (fn == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Faction faction = FactionManager.getFaction(fn);
                            int gems = org.minecraft.hatlas.faction.FactionVault.countGems(faction);
                            Component msg = Component.text("[", NamedTextColor.GRAY)
                                    .append(Component.text(faction.getName(), faction.getColor()))
                                    .append(Component.text("] vault holds ", NamedTextColor.GRAY))
                                    .append(Component.text(String.valueOf(gems), NamedTextColor.RED))
                                    .append(Component.text(gems == 1 ? " ruby gem." : " ruby gems.", NamedTextColor.GRAY));
                            player.sendMessage(msg);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
