package org.minecraft.atlas.storage.sql;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * PostgreSQL. The driver ({@code org.postgresql:postgresql}) is downloaded at startup by
 * {@code AtlasLoader} only when {@code storage.type: postgresql}.
 */
public final class PostgresDialect implements SqlDialect {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;

    public PostgresDialect(String host, int port, String database, String username, String password, boolean ssl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
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
        props.setProperty("ApplicationName", "atlas");
        Connection c = driver.connect("jdbc:postgresql://" + host + ":" + port + "/" + database, props);
        if (c == null) throw new SQLException("PostgreSQL driver refused the URL");
        return c;
    }

    @Override
    public String blobType() {
        return "BYTEA";
    }
}
