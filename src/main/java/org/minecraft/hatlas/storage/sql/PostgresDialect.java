package org.minecraft.hatlas.storage.sql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL. The driver ({@code org.postgresql:postgresql}) is downloaded at startup by
 * {@code HatlasLoader} only when {@code storage.type: postgresql}.
 *
 * <p>Hatlas owns one schema ({@code storage.postgresql.schema}, default {@code hatlas}); every
 * connection uses it as its {@code search_path}, so the mappers stay unqualified. Tables of an
 * older install that lived in {@code public} are moved into it on the first start. Other
 * schemas (e.g. the website's {@code web}) are never touched.
 *
 * <p>If the read-only role {@code storage.postgresql.web_role} exists, it is granted
 * {@code USAGE} on the schema and {@code SELECT} on the {@code web_*} views only (re-applied
 * on every start, so views recreated by a migration keep their grant).
 */
public final class PostgresDialect implements SqlDialect {

    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final String schema;
    private final String webRole;

    public PostgresDialect(String host, int port, String database, String username, String password, boolean ssl,
                           String schema, String webRole) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
        this.schema = checkIdent(schema, "storage.postgresql.schema");
        this.webRole = webRole == null || webRole.isBlank() ? null : checkIdent(webRole, "storage.postgresql.web_role");
    }

    private static String checkIdent(String value, String key) {
        String v = value == null ? "" : value.trim();
        if (!IDENT.matcher(v).matches()) {
            throw new IllegalArgumentException(key + " '" + value + "' must be a lowercase identifier ([a-z_][a-z0-9_]*)");
        }
        return v;
    }

    @Override
    public String describe() {
        return "PostgreSQL (" + username + "@" + host + ":" + port + "/" + database + ")";
    }

    @Override
    public Connection connect() throws SQLException {
        Driver driver = DriverLoader.load("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("ssl", String.valueOf(ssl));
        props.setProperty("ApplicationName", "hatlas");
        Connection c = driver.connect("jdbc:postgresql://" + host + ":" + port + "/" + database, props);
        if (c == null) throw new SQLException("PostgreSQL driver refused the URL");
        return c;
    }

    @Override
    public void configure(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        }
        moveLegacyTables(connection);
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + schema);
        }
    }

    /**
     * Before 2026-11-07 the tables lived in {@code public}. If {@code public} still holds the
     * schema-version table and the Hatlas schema does not, moves every Hatlas table (names read
     * from the schema files) into the Hatlas schema, in one transaction.
     */
    private void moveLegacyTables(Connection c) throws SQLException {
        if ("public".equals(schema)) return;
        boolean legacy = tableIn(c, "public", "hatlas_schema_version") || tableIn(c, "public", "atlas_schema_version");
        boolean current = tableIn(c, schema, "hatlas_schema_version") || tableIn(c, schema, "atlas_schema_version");
        if (!legacy || current) return;

        Set<String> names = new LinkedHashSet<>(List.of("hatlas_schema_version", "atlas_schema_version"));
        for (String script : SchemaMigrator.MIGRATIONS) names.addAll(tablesCreatedBy(script));
        List<String> moved = new ArrayList<>();
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            for (String t : names) {
                if (!tableIn(c, "public", t)) continue;
                st.execute("ALTER TABLE public." + t + " SET SCHEMA " + schema);
                moved.add(t);
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SQLException("Moving the Hatlas tables from schema public to " + schema + " failed: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(autoCommit);
        }
        Logger.getLogger("hatlas").info("Storage: moved " + moved.size() + " tables from schema public to " + schema);
    }

    private static boolean tableIn(Connection c, String schemaName, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schemaName);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<String> tablesCreatedBy(String script) throws SQLException {
        try (InputStream in = PostgresDialect.class.getResourceAsStream("/db/" + script)) {
            if (in == null) throw new SQLException("Missing schema resource /db/" + script);
            Matcher m = CREATE_TABLE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> out = new ArrayList<>();
            while (m.find()) out.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
            return out;
        } catch (IOException e) {
            throw new SQLException("Cannot read /db/" + script, e);
        }
    }

    @Override
    public void afterMigrate(Connection c, Logger log) throws SQLException {
        if (webRole == null) return;
        boolean exists;
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, webRole);
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }
        if (!exists) {
            log.info("Storage: read-only role '" + webRole + "' does not exist; the website cannot read the "
                    + "web_* views until it is created (docs/storage.md, \"Website read model\").");
            return;
        }
        List<String> views = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT table_name FROM information_schema.views WHERE table_schema = ? AND table_name LIKE 'web\\_%' ORDER BY table_name")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) views.add(rs.getString(1));
            }
        }
        try (Statement st = c.createStatement()) {
            st.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + webRole);
            st.execute("REVOKE ALL ON ALL TABLES IN SCHEMA " + schema + " FROM " + webRole);
            for (String v : views) st.execute("GRANT SELECT ON " + schema + "." + v + " TO " + webRole);
        }
        log.info("Storage: role '" + webRole + "' may read " + views.size() + " web_* views in schema " + schema);
    }

    @Override
    public String blobType() {
        return "BYTEA";
    }
}
