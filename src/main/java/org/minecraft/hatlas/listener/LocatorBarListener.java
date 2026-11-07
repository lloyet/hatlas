package org.minecraft.hatlas.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint;
import com.github.retrooper.packetevents.util.Either;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.minecraft.hatlas.Hatlas;
import org.minecraft.hatlas.faction.FactionManager;

import java.util.Collection;
import java.util.UUID;

/**
 * Restricts the 1.21.6+ locator bar so a player only ever sees markers for members of
 * their own faction. Vanilla broadcasts every player's waypoint to everyone in the same
 * dimension; there is no per-viewer Bukkit API, so we intercept the clientbound
 * {@code WAYPOINT} packet (via packetevents) and cancel it whenever the transmitter is
 * not in the viewer's faction.
 *
 * <p>Only TRACK/UPDATE operations are filtered; UNTRACK always passes. Cancelling them
 * means a disallowed marker is never shown on the receiving client. Because we only filter outbound packets, faction membership changes
 * must call {@link #refresh(Collection)} so markers that were already sent (or cancelled)
 * are re-evaluated against the new faction state.
 */
public class LocatorBarListener extends PacketListenerAbstract {

    private static final NamespacedKey REFRESH_KEY = new NamespacedKey("hatlas", "locator_bar_refresh");
    /** Ticks the transmitter stays muted — long enough for the attribute change to be processed. */
    private static final long REFRESH_DELAY_TICKS = 2L;

    public LocatorBarListener() {
        super(PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.WAYPOINT) return;
        if (!(event.getPlayer() instanceof Player viewer)) return;

        WrapperPlayServerWaypoint wrapper = new WrapperPlayServerWaypoint(event);
        // UNTRACK only ever removes a marker, so it must always reach the client — otherwise
        // a player who just left the viewer's faction stays frozen on their locator bar.
        if (wrapper.getOperation() == WrapperPlayServerWaypoint.Operation.UNTRACK) return;

        TrackedWaypoint waypoint = wrapper.getWaypoint();
        if (waypoint == null) return;

        // The locator bar identifies player transmitters by UUID (the left side of the
        // Either). Non-UUID waypoints are not player markers, so we leave them untouched.
        Either<UUID, String> identifier = waypoint.getIdentifier();
        if (identifier == null || !identifier.isLeft()) return;

        UUID targetUuid = identifier.getLeft();
        if (targetUuid == null || targetUuid.equals(viewer.getUniqueId())) return;

        if (!sameFaction(viewer.getUniqueId(), targetUuid)) {
            event.setCancelled(true);
        }
    }

    /**
     * Re-broadcasts the locator-bar waypoints of the given players so every viewer's HUD
     * matches current faction membership. Pass every player whose visibility may have
     * changed (the joining/leaving player plus the members of the affected factions).
     *
     * <p>Each online player's waypoint transmit range is forced to 0 for a couple of ticks:
     * vanilla then UNTRACKs their marker for all viewers, and when the modifier is removed it
     * TRACKs it again — those new TRACK packets go through {@link #onPacketSend} and are
     * filtered with the updated faction data. Refreshing both sides of a relationship fixes
     * both directions (A seeing B and B seeing A).
     */
    public static void refresh(Collection<UUID> players) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            AttributeInstance range = player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (range == null) continue;
            if (range.getModifier(REFRESH_KEY) == null) {
                range.addTransientModifier(new AttributeModifier(
                        REFRESH_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
            Bukkit.getScheduler().runTaskLater(Hatlas.instance, () -> {
                if (!player.isOnline()) return;
                AttributeInstance current = player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
                if (current != null) current.removeModifier(REFRESH_KEY);
            }, REFRESH_DELAY_TICKS);
        }
    }

    /** True only when both players are in the same (non-null) faction. */
    private static boolean sameFaction(UUID viewer, UUID target) {
        String viewerFaction = FactionManager.getPlayerFaction(viewer);
        if (viewerFaction == null) return false;
        return viewerFaction.equals(FactionManager.getPlayerFaction(target));
    }
}
