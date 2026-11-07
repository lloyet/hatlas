package org.minecraft.hatlas.storage.sql;

import org.minecraft.hatlas.storage.model.LedgerRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** Append-only {@code ruby_ledger} (V3). Not a snapshot domain: rows are only ever inserted. */
final class LedgerTable {

    private LedgerTable() {}

    static void insert(Connection c, List<LedgerRow> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ruby_ledger (at, kind, amount, "
                + "sender_type, sender_id, sender_name, sender_faction, "
                + "receiver_type, receiver_id, receiver_name, receiver_faction, "
                + "actor_uuid, actor_name, ref, world, x, y, z) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (LedgerRow r : rows) {
                int i = 1;
                ps.setLong(i++, r.at());
                ps.setString(i++, r.kind());
                ps.setInt(i++, r.amount());
                ps.setString(i++, r.senderType());
                ps.setString(i++, r.senderId());
                ps.setString(i++, r.senderName());
                ps.setString(i++, r.senderFaction());
                ps.setString(i++, r.receiverType());
                ps.setString(i++, r.receiverId());
                ps.setString(i++, r.receiverName());
                ps.setString(i++, r.receiverFaction());
                ps.setString(i++, r.actorUuid());
                ps.setString(i++, r.actorName());
                ps.setString(i++, r.ref());
                ps.setString(i++, r.world());
                setInt(ps, i++, r.x());
                setInt(ps, i++, r.y());
                setInt(ps, i, r.z());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    static List<LedgerRow> read(Connection c, String partyId, int limit) throws SQLException {
        List<LedgerRow> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT at, kind, amount, "
                + "sender_type, sender_id, sender_name, sender_faction, "
                + "receiver_type, receiver_id, receiver_name, receiver_faction, "
                + "actor_uuid, actor_name, ref, world, x, y, z FROM ruby_ledger "
                + "WHERE sender_id = ? OR receiver_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, partyId);
            ps.setString(2, partyId);
            ps.setInt(3, limit);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LedgerRow(rs.getLong(1), rs.getString(2), rs.getInt(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
                            rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15),
                            rs.getObject(16) == null ? null : rs.getInt(16),
                            rs.getObject(17) == null ? null : rs.getInt(17),
                            rs.getObject(18) == null ? null : rs.getInt(18)));
                }
            }
        }
        return out;
    }

    static long[] totals(Connection c) throws SQLException {
        try (java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT "
                     + "COALESCE(SUM(CASE WHEN sender_type IS NULL THEN amount ELSE 0 END), 0), "
                     + "COALESCE(SUM(CASE WHEN receiver_type IS NULL THEN amount ELSE 0 END), 0), "
                     + "COUNT(*) FROM ruby_ledger")) {
            rs.next();
            return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
