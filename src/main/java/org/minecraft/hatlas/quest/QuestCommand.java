package org.minecraft.hatlas.quest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.gui.JobMainGui;
import org.minecraft.hatlas.job.JobManager;

public class QuestCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("quest")
                .requires(src -> src.getSender().hasPermission("hatlas.quest"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!JobManager.hasJob(player.getUniqueId())) {
                        player.sendMessage(Component.text(
                                "You don't have a job yet. Visit a job NPC to get started.",
                                NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    new JobMainGui(player).open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
