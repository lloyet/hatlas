package org.minecraft.atlas.storage.sql;

import org.minecraft.atlas.storage.model.LocationData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small JDBC helpers shared by the domain mappers. Only portable SQL lives here. */
final class Sql {

    private Sql() {}

    @FunctionalInterface
    interface Binder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }

    @FunctionalInterface
    interface RowMapper {
        void map(ResultSet rs) throws SQLException;
    }

    /** Deletes every row of the given tables, in order (children first). */
    static void deleteAll(Connection c, String... tables) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String t : tables) st.executeUpdate("DELETE FROM " + t);
        }
    }

    /** Batch insert. {@code ON CONFLICT DO NOTHING} is valid on SQLite 3.24+ and PostgreSQL. */
    static <T> void insert(Connection c, String table, String columns, int count,
                           Iterable<T> rows, Binder<T> binder) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(count, "?"));
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int pending = 0;
            for (T row : rows) {
                binder.bind(ps, row);
                ps.addBatch();
                if (++pending >= 500) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) ps.executeBatch();
        }
    }

    static void query(Connection c, String sql, RowMapper mapper) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) mapper.map(rs);
        }
    }

    // ── Setters (nullable-aware) ─────────────────────────────────────────────

    static void setUuid(PreparedStatement ps, int i, UUID v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v.toString());
    }

    static void setString(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v);
    }

    static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }

    static void setDouble(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DOUBLE); else ps.setDouble(i, v);
    }

    static void setFloat(PreparedStatement ps, int i, Float v) throws SQLException {
        if (v == null) ps.setNull(i, Types.REAL); else ps.setFloat(i, v);
    }

    static void setBytes(PreparedStatement ps, int i, byte[] v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BINARY); else ps.setBytes(i, v);
    }

    /** Binds 6 columns (world, x, y, z, yaw, pitch) starting at {@code i}; all null for null. */
    static void setLocation(PreparedStatement ps, int i, LocationData loc) throws SQLException {
        setString(ps, i, loc == null ? null : loc.world());
        setDouble(ps, i + 1, loc == null ? null : loc.x());
        setDouble(ps, i + 2, loc == null ? null : loc.y());
        setDouble(ps, i + 3, loc == null ? null : loc.z());
        setFloat(ps, i + 4, loc == null ? null : loc.yaw());
        setFloat(ps, i + 5, loc == null ? null : loc.pitch());
    }

    // ── Getters (nullable-aware) ─────────────────────────────────────────────

    static UUID uuid(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Long longOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static Double doubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    /** Reads {@code <prefix>world, <prefix>x … <prefix>pitch}; null if the world is null. */
    static LocationData location(ResultSet rs, String prefix) throws SQLException {
        String world = rs.getString(prefix + "world");
        if (world == null) return null;
        return new LocationData(world, rs.getDouble(prefix + "x"), rs.getDouble(prefix + "y"),
                rs.getDouble(prefix + "z"), rs.getFloat(prefix + "yaw"), rs.getFloat(prefix + "pitch"));
    }

    // ── Compact list encodings for leaf values ──────────────────────────────

    static String joinList(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) if (!part.isEmpty()) out.add(part);
        return out;
    }
}
