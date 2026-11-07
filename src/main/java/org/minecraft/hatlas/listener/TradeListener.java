package org.minecraft.hatlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.trade.Trade;
import org.minecraft.hatlas.trade.TradeManager;

public class TradeListener implements Listener {

    private static final long REQUEST_EXPIRY_TICKS = 20L * 60; // 60 seconds

    // -------------------------------------------------------------------------
    // Right-click on a player → send trade request
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only main hand, only when right-clicking another player
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player initiator = event.getPlayer();
        if (initiator.equals(target)) return;

        if (TradeManager.hasTrade(initiator.getUniqueId())) {
            initiator.sendMessage(Component.text("You are already in a trade.", NamedTextColor.RED));
            return;
        }
        if (TradeManager.hasTrade(target.getUniqueId())) {
            initiator.sendMessage(Component.text(target.getName() + " is already in a trade.", NamedTextColor.RED));
            return;
        }

        // Prevent spamming duplicate requests
        if (TradeManager.hasPendingRequest(target.getUniqueId())
                && initiator.getUniqueId().equals(TradeManager.getPendingInitiator(target.getUniqueId()))) {
            initiator.sendMessage(Component.text(
                    "You already sent a trade request to " + target.getName() + ".", NamedTextColor.GOLD));
            return;
        }

        TradeManager.addPendingRequest(target.getUniqueId(), initiator.getUniqueId());

        initiator.sendMessage(Component.text(
                "Trade request sent to " + target.getName() + ".", NamedTextColor.GOLD));

        target.sendMessage(
                Component.text(initiator.getName() + " wants to trade with you.  ", NamedTextColor.GOLD)
                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/trade accept " + initiator.getName())))
                        .append(Component.text("  "))
                        .append(Component.text("[Decline]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/trade decline " + initiator.getName())))
        );

        // Auto-expire the request after 60 s
        Bukkit.getScheduler().runTaskLater(Hatlas.instance, () -> {
            if (TradeManager.hasPendingRequest(target.getUniqueId())
                    && initiator.getUniqueId().equals(TradeManager.getPendingInitiator(target.getUniqueId()))) {
                TradeManager.removePendingRequest(target.getUniqueId());
                if (initiator.isOnline()) initiator.sendMessage(Component.text(
                        "Trade request to " + target.getName() + " expired.", NamedTextColor.GRAY));
                if (target.isOnline()) target.sendMessage(Component.text(
                        "Trade request from " + initiator.getName() + " expired.", NamedTextColor.GRAY));
            }
        }, REQUEST_EXPIRY_TICKS);
    }

    // -------------------------------------------------------------------------
    // Player quits or dies mid-trade
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelTradeFor(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cancelTradeFor(event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private void cancelTradeFor(Player player) {
        Trade trade = TradeManager.getTrade(player.getUniqueId());
        if (trade != null && !trade.isFinished()) trade.cancel(player);
    }
}
