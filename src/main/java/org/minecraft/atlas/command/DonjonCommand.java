package org.minecraft.atlas.command;

import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.Storage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonRarity;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
import org.minecraft.atlas.donjon.SmugglerManager;

import java.util.Map;

public class DonjonCommand {

    /** Suggests all currently registered donjon IDs. */
    private static final SuggestionProvider<CommandSourceStack> DONJON_IDS =
            (ctx, builder) -> {
                DonjonManager.getDonjons().keySet().forEach(builder::suggest);
                return builder.buildFuture();
            };

    /** Suggests all donjon type keys currently loaded from donjons.yml. */
    private static final SuggestionProvider<CommandSourceStack> DONJON_TYPES =
            (ctx, builder) -> {
                DonjonManager.getAvailableTypes().forEach(builder::suggest);
                return builder.buildFuture();
            };

    /** Suggests all valid rarity names in lowercase. */
    private static final SuggestionProvider<CommandSourceStack> RARITY_NAMES =
            (ctx, builder) -> {
                for (DonjonRarity r : DonjonRarity.values()) builder.suggest(r.name().toLowerCase());
                return builder.buildFuture();
            };

    /** Suggests only the rarities valid for an ominous key (EPIC and above). */
    private static final SuggestionProvider<CommandSourceStack> OMINOUS_RARITY_NAMES =
            (ctx, builder) -> {
                builder.suggest("epic");
                builder.suggest("legendary");
                builder.suggest("mystic");
                builder.suggest("goddess");
                return builder.buildFuture();
            };

    /** Suggests currently online player names. */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS =
            (ctx, builder) -> {
                Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                return builder.buildFuture();
            };

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("donjon")
                .requires(src -> src.getSender().hasPermission("atlas.donjon.admin"))

                // /donjon — show help
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Donjon Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("create", "<type> <name>", "Create a donjon instance at your location"))
                                    .append(Component.newline()).append(helpEntry("list", "", "List all registered donjons"))
                                    .append(Component.newline()).append(helpEntry("info", "<id|name>", "Show detailed donjon info"))
                                    .append(Component.newline()).append(helpEntry("activate", "<id>", "Activate a donjon"))
                                    .append(Component.newline()).append(helpEntry("deactivate", "<id>", "Deactivate a donjon"))
                                    .append(Component.newline()).append(helpEntry("reset", "<id>", "Reset a donjon to idle"))
                                    .append(Component.newline()).append(helpEntry("tp", "<id>", "Teleport to a donjon"))
                                    .append(Component.newline()).append(helpEntry("delete", "<id>", "Delete a donjon"))
                                    .append(Component.newline()).append(helpEntry("claim", "<id>", "Add current chunk to donjon protected area"))
                                    .append(Component.newline()).append(helpEntry("unclaim", "<id>", "Remove current chunk from donjon protected area"))
                                    .append(Component.newline()).append(helpEntry("unclaimall", "<id>", "Remove all claimed chunks from a donjon"))
                                    .append(Component.newline()).append(Component.text("  -- /donjon set --", NamedTextColor.DARK_AQUA))
                                    .append(Component.newline()).append(helpEntry("set difficulty", "<id> <0-99>", "Set donjon difficulty level"))
                                    .append(Component.newline()).append(helpEntry("set rarity", "<id> <rarity>", "Set donjon rarity"))
                                    .append(Component.newline()).append(helpEntry("set spawn", "<id>", "Set player teleport spawn for a donjon"))
                                    .append(Component.newline()).append(helpEntry("set vault", "<id>", "Set the vault block (look at the block, max 10 blocks)"))
                                    .append(Component.newline()).append(helpEntry("doctor", "<id>", "List missing/unset donjon parameters"))
                                    .append(Component.newline()).append(Component.text("  -- /donjon wavespawn --", NamedTextColor.DARK_AQUA))
                                    .append(Component.newline()).append(helpEntry("wavespawn add", "<id>", "Add current location as wave spawn point"))
                                    .append(Component.newline()).append(helpEntry("wavespawn list", "<id>", "List all wave spawn points with IDs"))
                                    .append(Component.newline()).append(helpEntry("wavespawn delete", "<id> <spawnid>", "Delete a wave spawn point by ID"))
                                    .append(Component.newline()).append(helpEntry("wavespawn clear", "<id>", "Delete all wave spawn points of a donjon"))
                                    .append(Component.newline()).append(helpEntry("give", "<player> key|ominouskey|creeper_egg|powered_creeper_egg|raider_diamond_pickaxe [...]", "Give a donjon item to a player"))
                    );
                    return Command.SINGLE_SUCCESS;
                })

                // /donjon create <type> <name>
                .then(Commands.literal("create")
                        .executes(ctx -> usage(ctx.getSource().getSender(),
                                "create <type> <name>  — Available types: " + String.join(", ", DonjonManager.getAvailableTypes())))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(DONJON_TYPES)
                                .executes(ctx -> usage(ctx.getSource().getSender(), "create <type> <name>"))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(
                                                        Component.text("Only players can use this.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String typeArg = StringArgumentType.getString(ctx, "type");
                                            if (!DonjonManager.getAvailableTypes().contains(typeArg)) {
                                                player.sendMessage(Component.text(
                                                        "Unknown donjon type '" + typeArg + "'. Available: "
                                                                + String.join(", ", DonjonManager.getAvailableTypes()),
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String nameArg = StringArgumentType.getString(ctx, "name");
                                            if (DonjonManager.getDonjon(nameArg) != null) {
                                                player.sendMessage(Component.text(
                                                        "A donjon named or with id '" + nameArg + "' already exists.",
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Donjon donjon = DonjonManager.generateDonjon(typeArg, nameArg, player.getLocation());
                                            if (donjon == null) {
                                                player.sendMessage(Component.text(
                                                        "Failed to create donjon: no config for this type, or the current chunk is already inside another donjon.",
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Storage.save(DataDomain.DONJONS);
                                            player.sendMessage(Component.text(
                                                    "Donjon created: [" + donjon.getId() + "] " + donjon.getName()
                                                            + " LvL." + donjon.getLevel()
                                                            + " [" + donjon.getRarity().getDisplayName() + "]",
                                                    NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                // /donjon list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Map<String, Donjon> all = DonjonManager.getDonjons();

                            if (all.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("No donjons registered.", NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }

                            ctx.getSource().getSender().sendMessage(
                                    Component.text("=== Donjons ===", NamedTextColor.GOLD));
                            for (Donjon d : all.values()) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("[" + d.getId() + "] ", NamedTextColor.GRAY)
                                                .append(Component.text(d.getName(), d.getRarity().getColor()))
                                                .append(Component.text(
                                                        " LvL." + d.getLevel()
                                                                + " | " + DonjonManager.getTypeDisplayName(d.getType())
                                                                + " | " + d.getStatus().name()
                                                                + (d.isInProgress() ? " (IN PROGRESS)" : ""),
                                                        NamedTextColor.YELLOW)));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))

                // /donjon info <id>
                .then(Commands.literal("info")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "info <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    var s = ctx.getSource().getSender();
                                    s.sendMessage(Component.text("=== " + d.getName() + " ===", d.getRarity().getColor()));
                                    s.sendMessage(infoLine("ID",         d.getId(),                                NamedTextColor.GRAY));
                                    s.sendMessage(infoLine("Type",       DonjonManager.getTypeDisplayName(d.getType()), NamedTextColor.AQUA));
                                    s.sendMessage(infoLine("Difficulty", "LvL." + d.getLevel(),                   NamedTextColor.YELLOW));
                                    s.sendMessage(infoLine("Rarity",     d.getRarity().getDisplayName(),           d.getRarity().getColor()));
                                    s.sendMessage(infoLine("Status",     d.getStatus().name(),                     NamedTextColor.WHITE));
                                    s.sendMessage(infoLine("In Progress",String.valueOf(d.isInProgress()),          d.isInProgress() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
                                    s.sendMessage(infoLine("Wave",       (d.getCurrentWaveIndex() + 1) + "/" + d.getWaves().size(), NamedTextColor.WHITE));
                                    s.sendMessage(infoLine("Claims",     d.getProtectedChunkKeys().size() + " chunk(s)", NamedTextColor.GREEN));
                                    org.bukkit.Location ts = d.getTeleportSpawn();
                                    s.sendMessage(infoLine("TP Spawn",   ts != null
                                            ? ts.getWorld().getName() + " " + ts.getBlockX() + ", " + ts.getBlockY() + ", " + ts.getBlockZ()
                                            : "not set", ts != null ? NamedTextColor.WHITE : NamedTextColor.RED));
                                    org.bukkit.Location vl = d.getVaultLocation();
                                    s.sendMessage(infoLine("Vault",      vl != null
                                            ? vl.getWorld().getName() + " " + vl.getBlockX() + ", " + vl.getBlockY() + ", " + vl.getBlockZ()
                                            : "not set", vl != null ? NamedTextColor.WHITE : NamedTextColor.RED));
                                    Map<Integer, org.bukkit.Location> spawnById = d.getSpawnPointsById();
                                    if (spawnById.isEmpty()) {
                                        s.sendMessage(infoLine("Wave Spawns", "none", NamedTextColor.RED));
                                    } else {
                                        s.sendMessage(infoLine("Wave Spawns", spawnById.size() + " point(s):", NamedTextColor.WHITE));
                                        for (Map.Entry<Integer, org.bukkit.Location> e : spawnById.entrySet()) {
                                            org.bukkit.Location pt = e.getValue();
                                            s.sendMessage(Component.text("    #" + e.getKey() + " → " + pt.getBlockX() + ", " + pt.getBlockY() + ", " + pt.getBlockZ(), NamedTextColor.GRAY));
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon activate <id>
                .then(Commands.literal("activate")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "activate <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (d.getStatus() == DonjonStatus.ACTIVE) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon is already active.", NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    // Doctor requirements
                                    java.util.List<String> missing = new java.util.ArrayList<>();
                                    if (d.getVaultLocation() == null)       missing.add("vault block");
                                    if (d.getTeleportSpawn() == null)       missing.add("teleport spawn");
                                    if (d.getSpawnPointsById().isEmpty())   missing.add("wave spawn points");
                                    if (!missing.isEmpty()) {
                                        ctx.getSource().getSender().sendMessage(Component.text(
                                                "Cannot activate " + d.getName() + ": missing "
                                                + String.join(", ", missing)
                                                + ". Run /donjon doctor " + id + " for details.",
                                                NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    DonjonManager.activateDonjon(d);
                                    Storage.save(DataDomain.DONJONS);
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon activated: " + d.getName(), NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon deactivate <id>
                .then(Commands.literal("deactivate")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "deactivate <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (d.getStatus() != DonjonStatus.ACTIVE) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon is not active.", NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    DonjonManager.setIdle(d, null);
                                    Storage.save(DataDomain.DONJONS);
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon deactivated: " + d.getName(), NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon reset <id>
                .then(Commands.literal("reset")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "reset <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    DonjonManager.setIdle(d, null);
                                    Storage.save(DataDomain.DONJONS);
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon reset to idle: " + d.getName(), NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon tp <id>
                .then(Commands.literal("tp")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "tp <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();

                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Only players can use this.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        player.sendMessage(Component.text(
                                                "Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    org.bukkit.Location dest = d.getTeleportSpawn();
                                    if (dest == null) {
                                        player.sendMessage(Component.text(
                                                "Donjon " + d.getName() + " has no teleport spawn set. "
                                                        + "Use /donjon set spawn " + id + " first.",
                                                NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    player.teleport(dest);
                                    player.sendMessage(Component.text(
                                            "Teleported to donjon: " + d.getName()
                                                    + " (" + dest.getBlockX()
                                                    + "," + dest.getBlockY()
                                                    + "," + dest.getBlockZ() + ")",
                                            NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon delete <id>
                .then(Commands.literal("delete")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "delete <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String name = d.getName();
                                    DonjonManager.deleteDonjon(id);
                                    Storage.save(DataDomain.DONJONS);
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon deleted: " + name, NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon set difficulty <id> <difficulty>
                // /donjon set rarity    <id> <rarity>
                // /donjon set spawn     <id>
                .then(Commands.literal("set")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(
                                Component.text("--- /donjon set ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("set difficulty", "<id> <0-99>", "Set donjon difficulty level"))
                                    .append(Component.newline()).append(helpEntry("set rarity", "<id> <rarity>", "Set donjon rarity"))
                                    .append(Component.newline()).append(helpEntry("set spawn", "<id>", "Set player teleport spawn"))
                                    .append(Component.newline()).append(helpEntry("set vault", "<id>", "Set the vault block"))
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("difficulty")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "set difficulty <id> <0-99>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> usage(ctx.getSource().getSender(), "set difficulty <id> <0-99>"))
                                        .then(Commands.argument("difficulty", IntegerArgumentType.integer(0, 99))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    Donjon d = DonjonManager.getDonjon(id);
                                                    if (d == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    int difficulty = IntegerArgumentType.getInteger(ctx, "difficulty");
                                                    d.setLevel(difficulty);
                                                    DonjonManager.spawnOrUpdateNametag(d);
                                                    Storage.save(DataDomain.DONJONS);
                                                    ctx.getSource().getSender().sendMessage(Component.text(
                                                            "Donjon " + d.getName() + " difficulty set to LvL." + difficulty + ".", NamedTextColor.GREEN));
                                                    return Command.SINGLE_SUCCESS;
                                                }))))
                        .then(Commands.literal("rarity")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "set rarity <id> <common|rare|epic|legendary|mystic|goddess>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> usage(ctx.getSource().getSender(), "set rarity <id> <rarity>"))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests(RARITY_NAMES)
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    Donjon d = DonjonManager.getDonjon(id);
                                                    if (d == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    String rarityArg = StringArgumentType.getString(ctx, "rarity");
                                                    DonjonRarity rarity;
                                                    try { rarity = DonjonRarity.valueOf(rarityArg.toUpperCase()); }
                                                    catch (IllegalArgumentException e) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("Unknown rarity: " + rarityArg, NamedTextColor.RED));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    d.setRarity(rarity);
                                                    DonjonManager.spawnOrUpdateNametag(d);
                                                    Storage.save(DataDomain.DONJONS);
                                                    ctx.getSource().getSender().sendMessage(Component.text(
                                                            "Donjon " + d.getName() + " rarity set to " + rarity.getDisplayName() + ".", NamedTextColor.GREEN));
                                                    return Command.SINGLE_SUCCESS;
                                                }))))
                        .then(Commands.literal("spawn")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "set spawn <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Donjon d = DonjonManager.getDonjon(id);
                                            if (d == null) {
                                                player.sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            if (!playerStandingInDonjonClaim(player, d)) {
                                                player.sendMessage(Component.text(
                                                        "Your current location is not inside a claim of donjon " + d.getName()
                                                                + ". Use /donjon claim first.",
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            d.setTeleportSpawn(player.getLocation());
                                            Storage.save(DataDomain.DONJONS);
                                            player.sendMessage(Component.text("Teleport spawn set for donjon " + d.getName() + ".", NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        // /donjon set vault <id>
                        .then(Commands.literal("vault")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "set vault <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Donjon d = DonjonManager.getDonjon(id);
                                            if (d == null) {
                                                player.sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            org.bukkit.block.Block target = player.getTargetBlockExact(10);
                                            if (target == null) {
                                                player.sendMessage(Component.text("No block in sight (look at a block within 10 blocks).", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            long targetChunkKey = org.bukkit.Chunk.getChunkKey(target.getX() >> 4, target.getZ() >> 4);
                                            if (!d.getProtectedChunkKeys().contains(targetChunkKey)) {
                                                player.sendMessage(Component.text(
                                                        "That block is not inside the donjon's protected area. Use /donjon claim first.",
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            d.setVaultLocation(target.getLocation());
                                            DonjonManager.spawnOrUpdateNametag(d);
                                            Storage.save(DataDomain.DONJONS);
                                            player.sendMessage(Component.text(
                                                    "Vault block set to " + target.getType().name()
                                                    + " at " + target.getX() + ", " + target.getY() + ", " + target.getZ()
                                                    + " for donjon " + d.getName() + ".", NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                // /donjon doctor <id>
                .then(Commands.literal("doctor")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "doctor <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    var s = ctx.getSource().getSender();
                                    s.sendMessage(Component.text("=== Doctor: " + d.getName() + " ===", NamedTextColor.GOLD));
                                    doctorCheck(s, "Vault block",      d.getVaultLocation() != null);
                                    doctorCheck(s, "Teleport spawn",   d.getTeleportSpawn() != null);
                                    doctorCheck(s, "Wave spawn points",!d.getSpawnPointsById().isEmpty());
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon claim <id>  — add player's current chunk to the donjon's protected area
                .then(Commands.literal("claim")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "claim <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        player.sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int cx = player.getLocation().getChunk().getX();
                                    int cz = player.getLocation().getChunk().getZ();
                                    long chunkKey = org.bukkit.Chunk.getChunkKey(cx, cz);
                                    if (d.getProtectedChunkKeys().contains(chunkKey)) {
                                        player.sendMessage(Component.text(
                                                "Chunk [" + cx + ", " + cz + "] is already claimed by donjon " + d.getName() + ".",
                                                NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (!DonjonManager.addProtectedChunk(id, player.getWorld(), cx, cz)) {
                                        player.sendMessage(Component.text("Cannot add chunk: donjon not found or wrong world.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Storage.save(DataDomain.DONJONS);
                                    player.sendMessage(Component.text(
                                            "Chunk [" + cx + ", " + cz + "] added to donjon " + d.getName() + " protected area.", NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon unclaim <id>  — remove player's current chunk from the donjon's protected area
                .then(Commands.literal("unclaim")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "unclaim <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        player.sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int cx = player.getLocation().getChunk().getX();
                                    int cz = player.getLocation().getChunk().getZ();
                                    if (!DonjonManager.removeProtectedChunk(id, player.getWorld(), cx, cz)) {
                                        player.sendMessage(Component.text(
                                                "Chunk [" + cx + ", " + cz + "] is not claimed by donjon " + d.getName() + ".",
                                                NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Storage.save(DataDomain.DONJONS);
                                    player.sendMessage(Component.text(
                                            "Chunk [" + cx + ", " + cz + "] removed from donjon " + d.getName() + " protected area.",
                                            NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon unclaimall <id>  — remove every claimed chunk from the donjon
                .then(Commands.literal("unclaimall")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "unclaimall <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int removed = DonjonManager.clearProtectedChunks(id);
                                    if (removed <= 0) {
                                        ctx.getSource().getSender().sendMessage(Component.text(
                                                "Donjon " + d.getName() + " has no claimed chunks.", NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Storage.save(DataDomain.DONJONS);
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                            "Removed " + removed + " claimed chunk(s) from donjon " + d.getName() + ".",
                                            NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon wavespawn add|list|delete|clear
                .then(Commands.literal("wavespawn")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(
                                Component.text("--- /donjon wavespawn ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("wavespawn add", "<id>", "Add current location as wave spawn point"))
                                    .append(Component.newline()).append(helpEntry("wavespawn list", "<id>", "List all wave spawn points with IDs"))
                                    .append(Component.newline()).append(helpEntry("wavespawn delete", "<id> <spawnid>", "Delete a wave spawn point by ID"))
                                    .append(Component.newline()).append(helpEntry("wavespawn clear", "<id>", "Delete all wave spawn points of a donjon"))
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        // /donjon wavespawn add <id>
                        .then(Commands.literal("add")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "wavespawn add <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Donjon d = DonjonManager.getDonjon(id);
                                            if (d == null) {
                                                player.sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            if (!playerStandingInDonjonClaim(player, d)) {
                                                int px = player.getLocation().getChunk().getX();
                                                int pz = player.getLocation().getChunk().getZ();
                                                player.sendMessage(Component.text(
                                                        "Your current chunk [" + px + ", " + pz + "] is not inside a claim of donjon "
                                                                + d.getName() + ". Use /donjon claim first.",
                                                        NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int spawnId = d.addSpawnPoint(player.getLocation());
                                            Storage.save(DataDomain.DONJONS);
                                            player.sendMessage(Component.text(
                                                    "Wave spawn point #" + spawnId + " added to donjon " + d.getName() + ".", NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        // /donjon wavespawn list <id>
                        .then(Commands.literal("list")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "wavespawn list <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Donjon d = DonjonManager.getDonjon(id);
                                            if (d == null) {
                                                ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Map<Integer, org.bukkit.Location> spawnById = d.getSpawnPointsById();
                                            if (spawnById.isEmpty()) {
                                                ctx.getSource().getSender().sendMessage(Component.text(
                                                        "No wave spawn points defined for " + d.getName() + ".", NamedTextColor.YELLOW));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Component list = Component.text("=== " + d.getName() + " Wave Spawns ===", NamedTextColor.GOLD);
                                            for (Map.Entry<Integer, org.bukkit.Location> e : spawnById.entrySet()) {
                                                org.bukkit.Location loc = e.getValue();
                                                list = list.append(Component.newline())
                                                        .append(Component.text("  #" + e.getKey() + " ", NamedTextColor.AQUA))
                                                        .append(Component.text(
                                                                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                                                                NamedTextColor.YELLOW));
                                            }
                                            ctx.getSource().getSender().sendMessage(list);
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        // /donjon wavespawn delete <id> <spawnid>
                        .then(Commands.literal("delete")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "wavespawn delete <id> <spawnid>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> usage(ctx.getSource().getSender(), "wavespawn delete <id> <spawnid>"))
                                        .then(Commands.argument("spawnid", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    Donjon d = DonjonManager.getDonjon(id);
                                                    if (d == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    int spawnId = IntegerArgumentType.getInteger(ctx, "spawnid");
                                                    if (!d.removeSpawnPoint(spawnId)) {
                                                        ctx.getSource().getSender().sendMessage(Component.text(
                                                                "No spawn point with ID #" + spawnId + " found.", NamedTextColor.RED));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    Storage.save(DataDomain.DONJONS);
                                                    ctx.getSource().getSender().sendMessage(Component.text(
                                                            "Spawn point #" + spawnId + " removed from donjon " + d.getName() + ".", NamedTextColor.GREEN));
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                        // /donjon wavespawn clear <id>
                        .then(Commands.literal("clear")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "wavespawn clear <id>"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(DONJON_IDS)
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Donjon d = DonjonManager.getDonjon(id);
                                            if (d == null) {
                                                ctx.getSource().getSender().sendMessage(Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int removed = d.getSpawnPointsById().size();
                                            if (removed == 0) {
                                                ctx.getSource().getSender().sendMessage(Component.text(
                                                        "No wave spawn points to clear for " + d.getName() + ".", NamedTextColor.YELLOW));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            d.setSpawnPoints(java.util.Collections.emptyList());
                                            Storage.save(DataDomain.DONJONS);
                                            ctx.getSource().getSender().sendMessage(Component.text(
                                                    "Cleared " + removed + " wave spawn point(s) from donjon " + d.getName() + ".",
                                                    NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        })))))

                // /donjon give <player> key|ominouskey|creeper_egg|powered_creeper_egg|raider_diamond_pickaxe
                .then(Commands.literal("give")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "give <player> key|ominouskey|creeper_egg|powered_creeper_egg|raider_diamond_pickaxe [difficulty] [rarity]"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ONLINE_PLAYERS)
                                .executes(ctx -> usage(ctx.getSource().getSender(), "give <player> key|ominouskey|creeper_egg|powered_creeper_egg|raider_diamond_pickaxe [difficulty] [rarity]"))
                                .then(Commands.literal("creeper_egg")
                                        .executes(ctx -> doGiveKey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                ElectricalCreeperManager.createCreeperEgg(1), "Creeper Spawn Egg"))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return doGiveKey(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            ElectricalCreeperManager.createCreeperEgg(amount),
                                                            "Creeper Spawn Egg", amount);
                                                })))
                                .then(Commands.literal("powered_creeper_egg")
                                        .executes(ctx -> doGiveKey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                ElectricalCreeperManager.createElectricalCreeperEgg(), "⚡ Powered Creeper Egg"))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return doGiveKey(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            ElectricalCreeperManager.createElectricalCreeperEgg(),
                                                            "⚡ Powered Creeper Egg", amount);
                                                })))
                                .then(Commands.literal("raider_diamond_pickaxe")
                                        .executes(ctx -> doGiveKey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                RaiderPickaxe.create(), "⚔ Raider's Pickaxe"))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return doGiveKey(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            RaiderPickaxe.create(), "⚔ Raider's Pickaxe", amount);
                                                })))
                                .then(Commands.literal("key")
                                        .executes(ctx -> doGiveKey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                DonjonManager.createTrialKey(), "key"))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return doGiveKey(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            DonjonManager.createTrialKey(), "key", amount);
                                                })))
                                .then(Commands.literal("ominouskey")
                                        // /donjon give <player> ominouskey — weighted random
                                        .executes(ctx -> doGiveKey(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                DonjonManager.createOminousKey(), "ominouskey"))
                                        // /donjon give <player> ominouskey <difficulty> — fixed level, random rarity
                                        .then(Commands.argument("difficulty", IntegerArgumentType.integer(50, 99))
                                                .executes(ctx -> {
                                                    int diff = IntegerArgumentType.getInteger(ctx, "difficulty");
                                                    return doGiveKey(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            DonjonManager.buildOminousKey(diff, DonjonManager.rollOminousRarity()),
                                                            "ominouskey (lvl " + diff + ")");
                                                })
                                                // /donjon give <player> ominouskey <difficulty> <amount> — Nx fixed level, random rarity
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> {
                                                            int diff   = IntegerArgumentType.getInteger(ctx, "difficulty");
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            return doGiveKey(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    DonjonManager.buildOminousKey(diff, DonjonManager.rollOminousRarity()),
                                                                    "ominouskey (lvl " + diff + ")", amount);
                                                        }))
                                                // /donjon give <player> ominouskey <difficulty> <rarity> — fixed level + rarity
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(OMINOUS_RARITY_NAMES)
                                                        .executes(ctx -> {
                                                            int diff = IntegerArgumentType.getInteger(ctx, "difficulty");
                                                            String rarityArg = StringArgumentType.getString(ctx, "rarity");
                                                            DonjonRarity rarity;
                                                            try {
                                                                rarity = DonjonRarity.valueOf(rarityArg.toUpperCase());
                                                            } catch (IllegalArgumentException e) {
                                                                ctx.getSource().getSender().sendMessage(error(
                                                                        "Unknown rarity: " + rarityArg + ". Valid: epic, legendary, mystic, goddess"));
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                            return doGiveKey(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    DonjonManager.buildOminousKey(diff, rarity),
                                                                    "ominouskey (lvl " + diff + " " + rarity.getDisplayName() + ")");
                                                        })
                                                        // /donjon give <player> ominouskey <difficulty> <rarity> <amount>
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                                .executes(ctx -> {
                                                                    int diff   = IntegerArgumentType.getInteger(ctx, "difficulty");
                                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                                    String rarityArg = StringArgumentType.getString(ctx, "rarity");
                                                                    DonjonRarity rarity;
                                                                    try {
                                                                        rarity = DonjonRarity.valueOf(rarityArg.toUpperCase());
                                                                    } catch (IllegalArgumentException e) {
                                                                        ctx.getSource().getSender().sendMessage(error(
                                                                                "Unknown rarity: " + rarityArg + ". Valid: epic, legendary, mystic, goddess"));
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }
                                                                    return doGiveKey(ctx.getSource(),
                                                                            StringArgumentType.getString(ctx, "player"),
                                                                            DonjonManager.buildOminousKey(diff, rarity),
                                                                            "ominouskey (lvl " + diff + " " + rarity.getDisplayName() + ")", amount);
                                                                })))))))


                // /donjon npc summon smuggler
                .then(Commands.literal("npc")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "npc summon smuggler"))
                        .then(Commands.literal("summon")
                                .executes(ctx -> usage(ctx.getSource().getSender(), "npc summon smuggler"))
                                .then(Commands.literal("smuggler")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(
                                                        Component.text("Only players can use this.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            SmugglerManager.spawnSmuggler(player.getLocation());
                                            player.sendMessage(Component.text(
                                                    "Smuggler spawned at your location.", NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                .build();
    }

    private static Component infoLine(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private static void doctorCheck(net.kyori.adventure.audience.Audience audience, String label, boolean ok) {
        audience.sendMessage(ok
                ? Component.text("  ✔ " + label, NamedTextColor.GREEN)
                : Component.text("  ⚠ " + label + ": NOT SET", NamedTextColor.RED));
    }

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    private static int doGiveKey(CommandSourceStack src, String playerName,
                                 ItemStack item, String label) {
        return doGiveKey(src, playerName, item, label, 1);
    }

    private static int doGiveKey(CommandSourceStack src, String playerName,
                                 ItemStack item, String label, int amount) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            src.getSender().sendMessage(error("Player not found or offline: " + playerName));
            return Command.SINGLE_SUCCESS;
        }
        item.setAmount(amount);
        target.getInventory().addItem(item);
        src.getSender().sendMessage(success("Gave " + amount + "x " + label + " to " + target.getName() + "."));
        target.sendMessage(Component.text("You received " + amount + "x " + label + ".", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean playerStandingInDonjonClaim(Player player, Donjon d) {
        org.bukkit.Location loc = player.getLocation();
        if (!loc.getWorld().equals(d.getWorld())) return false;
        long key = org.bukkit.Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        return d.getProtectedChunkKeys().contains(key);
    }

    private static int usage(net.kyori.adventure.audience.Audience audience, String syntax) {
        audience.sendMessage(Component.text("Usage: /donjon " + syntax, NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/donjon ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
