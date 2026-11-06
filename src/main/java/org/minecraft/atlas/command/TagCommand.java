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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.tag.Tag;
import org.minecraft.atlas.tag.TagManager;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class TagCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    /** Suggests all currently registered tag names. */
    private static final SuggestionProvider<CommandSourceStack> TAG_NAMES =
            (ctx, builder) -> {
                TagManager.getTags().keySet().forEach(builder::suggest);
                return builder.buildFuture();
            };

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tag")
                .requires(src -> src.getSender().hasPermission("atlas.tag.admin"))

                // /tag — show help
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Tag Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("create", "<name> <text>", "Create a tag at your location (+1y)"))
                                    .append(Component.newline()).append(helpEntry("delete", "<name>",        "Delete a tag from the world"))
                                    .append(Component.newline()).append(helpEntry("add",    "<name> <text>", "Append a new line to a tag"))
                                    .append(Component.newline()).append(helpEntry("edit",   "<name> <line> <text>", "Replace a line by its number"))
                                    .append(Component.newline()).append(helpEntry("list",   "",              "List all registered tags"))
                    );
                    return Command.SINGLE_SUCCESS;
                })

                // /tag create <name> <text>
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(
                                                        error("Only players can use this."));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String name = StringArgumentType.getString(ctx, "name");
                                            String text = StringArgumentType.getString(ctx, "text");

                                            if (TagManager.tagExists(name)) {
                                                player.sendMessage(error("A tag named '" + name + "' already exists."));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            // Spawn 1 block above player's feet
                                            org.bukkit.Location spawnLoc = player.getLocation().add(0, 1, 0);
                                            Tag tag = TagManager.createTag(name, spawnLoc, text);

                                            Storage.save(DataDomain.TAGS);

                                            player.sendMessage(success("Tag '" + name + "' created."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                // /tag delete <name>
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TAG_NAMES)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");

                                    if (!TagManager.deleteTag(name)) {
                                        ctx.getSource().getSender().sendMessage(error("Tag not found: " + name));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Storage.save(DataDomain.TAGS);

                                    ctx.getSource().getSender().sendMessage(success("Tag '" + name + "' deleted."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /tag add <name> <text>
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TAG_NAMES)
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String text = StringArgumentType.getString(ctx, "text");

                                            if (!TagManager.addLine(name, text)) {
                                                ctx.getSource().getSender().sendMessage(error("Tag not found: " + name));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            Storage.save(DataDomain.TAGS);

                                            Tag tag = TagManager.getTag(name);
                                            ctx.getSource().getSender().sendMessage(success(
                                                    "Line " + tag.getLines().size() + " added to tag '" + name + "'."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                // /tag edit <name> <line-number> <text>
                .then(Commands.literal("edit")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TAG_NAMES)
                                .then(Commands.argument("line", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    int    line = IntegerArgumentType.getInteger(ctx, "line");
                                                    String text = StringArgumentType.getString(ctx, "text");

                                                    Tag tag = TagManager.getTag(name);
                                                    if (tag == null) {
                                                        ctx.getSource().getSender().sendMessage(error("Tag not found: " + name));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    if (!TagManager.editLine(name, line, text)) {
                                                        ctx.getSource().getSender().sendMessage(error(
                                                                "Line " + line + " does not exist on tag '" + name
                                                                        + "' (has " + tag.getLines().size() + " lines)."));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Storage.save(DataDomain.TAGS);

                                                    ctx.getSource().getSender().sendMessage(success(
                                                            "Line " + line + " of tag '" + name + "' updated."));
                                                    return Command.SINGLE_SUCCESS;
                                                })))))

                // /tag list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            if (TagManager.getTags().isEmpty()) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("No tags registered.", NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }

                            ctx.getSource().getSender().sendMessage(
                                    Component.text("=== Tags ===", NamedTextColor.GOLD));

                            for (Tag tag : TagManager.getTags().values()) {
                                org.bukkit.Location loc = tag.getLocation();
                                List<String> lines = tag.getLines();
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("[" + tag.getName() + "] ", NamedTextColor.AQUA)
                                                .append(Component.text(
                                                        loc.getWorld().getName()
                                                                + " " + (int) loc.getX()
                                                                + "," + (int) loc.getY()
                                                                + "," + (int) loc.getZ()
                                                                + " — " + lines.size() + " line(s): "
                                                                + (lines.isEmpty() ? "" : lines.get(0))
                                                                + (lines.size() > 1 ? " ..." : ""),
                                                        NamedTextColor.YELLOW)));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/tag ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
