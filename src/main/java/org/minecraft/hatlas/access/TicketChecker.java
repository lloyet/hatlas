package org.minecraft.hatlas.access;

import org.minecraft.hatlas.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads the website's ticket table. Contract (docs/access.md): {@code <schema>.tickets} has at
 * least {@code uuid uuid} and {@code valid_until timestamptz}, any number of rows per player
 * (one per purchase, or one updated row); a player has access while the latest
 * {@code valid_until} is in the future. The comparison uses the database clock.
 *
 * <p>Blocking: call off the main thread (login runs on an async thread). One connection,
 * separate from the storage writer's, shared under a lock and re-opened if it dropped.
 */
public final class TicketChecker {

    public enum Status { VALID, EXPIRED, NONE, ERROR }

    /** {@code validUntil} is null for NONE / ERROR; {@code error} is set for ERROR only. */
    public record Result(Status status, Instant validUntil, String error) {
        static Result error(String message) { return new Result(Status.ERROR, null, message); }
    }

    private final String schema;
    private final Logger log;
    private final Object lock = new Object();
    private Connection connection;

    public TicketChecker(String schema, Logger log) {
        this.schema = schema;
        this.log = log;
    }

    public Result check(UUID player) {
        synchronized (lock) {
            try {
                return query(connection(), player);
            } catch (SQLException e) {
                closeQuietly();
                return Result.error(e.getMessage());
            }
        }
    }

    private Result query(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT max(valid_until), now() FROM " + schema + ".tickets WHERE uuid = ?")) {
            ps.setObject(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Timestamp until = rs.getTimestamp(1);
                Timestamp now = rs.getTimestamp(2);
                if (until == null) return new Result(Status.NONE, null, null);
                return new Result(until.after(now) ? Status.VALID : Status.EXPIRED, until.toInstant(), null);
            }
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            try {
                if (connection.isValid(3)) return connection;
            } catch (SQLException ignored) {}
            closeQuietly();
            log.warning("Access: ticket-check connection lost, reconnecting");
        }
        connection = Storage.newConnection();
        connection.setAutoCommit(true);
        return connection;
    }

    public void close() {
        synchronized (lock) {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {}
        connection = null;
    }
}
