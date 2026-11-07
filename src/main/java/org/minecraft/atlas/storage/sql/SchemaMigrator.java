package org.minecraft.atlas.storage.sql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applies the versioned schema files {@code /db/V<n>__<name>.sql} in order, each in its own
 * transaction, and records them in {@code atlas_schema_version}. To change the schema, add a
 * new file and append it to {@link #MIGRATIONS}; never edit an applied one.
 * Placeholder {@code ${blob}} is replaced by the dialect's binary type.
 */
final class SchemaMigrator {

    static final List<String> MIGRATIONS = List.of(
            "V1__initial_schema.sql"
    );

    private SchemaMigrator() {}

    static void migrate(Connection c, SqlDialect dialect, Logger log) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS atlas_schema_version ("
                    + "version INTEGER PRIMARY KEY, script TEXT NOT NULL, applied_at BIGINT NOT NULL)");
        }
        int current = 0;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(version) FROM atlas_schema_version")) {
            if (rs.next()) current = rs.getInt(1);
        }
        if (current > MIGRATIONS.size()) {
            throw new SQLException("Database schema version " + current
                    + " is newer than this plugin (" + MIGRATIONS.size() + "). Update the plugin.");
        }

        for (int version = current + 1; version <= MIGRATIONS.size(); version++) {
            String script = MIGRATIONS.get(version - 1);
            List<String> statements = parse(read(script).replace("${blob}", dialect.blobType()));
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String sql : statements) st.execute(sql);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO atlas_schema_version (version, script, applied_at) VALUES (?, ?, ?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, script);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                log.info("Storage: applied schema " + script);
            } catch (SQLException e) {
                c.rollback();
                throw new SQLException("Schema migration " + script + " failed: " + e.getMessage(), e);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private static String read(String script) throws SQLException {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream("/db/" + script)) {
            if (in == null) throw new SQLException("Missing schema resource /db/" + script);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Cannot read /db/" + script, e);
        }
    }

    /** Strips {@code --} comments and splits on {@code ;}. Scripts must not use ';' in literals. */
    static List<String> parse(String script) {
        StringBuilder clean = new StringBuilder();
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("--");
            clean.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String part : clean.toString().split(";")) {
            String sql = part.trim();
            if (!sql.isEmpty()) out.add(sql);
        }
        return out;
    }
}
