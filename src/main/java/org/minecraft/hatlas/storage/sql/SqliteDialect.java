package org.minecraft.hatlas.storage.sql;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * SQLite through the {@code org.xerial:sqlite-jdbc} driver that Paper ships on the server
 * classpath (no extra dependency). One file, WAL journal, foreign keys on.
 * A {@linkplain #portable portable} database (exports) uses a rollback journal instead of WAL,
 * so the closed file is self-contained (no {@code -wal}/{@code -shm} next to it).
 */
public final class SqliteDialect implements SqlDialect {

    private final File file;
    private final boolean wal;

    public SqliteDialect(File file) {
        this(file, true);
    }

    private SqliteDialect(File file, boolean wal) {
        this.file = file;
        this.wal = wal;
    }

    /** A single-file database for exports (journal_mode DELETE). */
    public static SqliteDialect portable(File file) {
        return new SqliteDialect(file, false);
    }

    @Override
    public String describe() {
        return "SQLite (" + file.getPath() + ")";
    }

    @Override
    public Connection connect() throws SQLException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create directory " + parent);
        }
        Driver driver = DriverLoader.load("org.sqlite.JDBC");
        Connection c = driver.connect("jdbc:sqlite:" + file.getAbsolutePath(), new Properties());
        if (c == null) throw new SQLException("SQLite driver refused the URL");
        return c;
    }

    @Override
    public void configure(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute(wal ? "PRAGMA journal_mode = WAL" : "PRAGMA journal_mode = DELETE");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }
    }

    @Override
    public String blobType() {
        return "BLOB";
    }
}
