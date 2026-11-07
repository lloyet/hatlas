package org.minecraft.hatlas.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    // Both players of an active trade are mapped to the same Trade object
    private static final Map<UUID, Trade> activeTrades = new HashMap<>();

    // Pending request: targetUUID → initiatorUUID
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>();

    // -------------------------------------------------------------------------
    // Active trades
    // -------------------------------------------------------------------------

    public static boolean hasTrade(UUID uuid) {
        return activeTrades.containsKey(uuid);
    }

    public static Trade getTrade(UUID uuid) {
        return activeTrades.get(uuid);
    }

    public static void addTrade(Trade trade) {
        activeTrades.put(trade.getInitiator().getUniqueId(), trade);
        activeTrades.put(trade.getTarget().getUniqueId(), trade);
    }

    public static void removeTrade(Trade trade) {
        activeTrades.remove(trade.getInitiator().getUniqueId());
        activeTrades.remove(trade.getTarget().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Pending requests
    // -------------------------------------------------------------------------

    public static boolean hasPendingRequest(UUID targetUUID) {
        return pendingRequests.containsKey(targetUUID);
    }

    public static UUID getPendingInitiator(UUID targetUUID) {
        return pendingRequests.get(targetUUID);
    }

    public static void addPendingRequest(UUID targetUUID, UUID initiatorUUID) {
        pendingRequests.put(targetUUID, initiatorUUID);
    }

    public static void removePendingRequest(UUID targetUUID) {
        pendingRequests.remove(targetUUID);
    }
}