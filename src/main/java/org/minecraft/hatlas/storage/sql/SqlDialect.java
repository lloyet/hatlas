package org.minecraft.hatlas.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * What differs between SQL databases. The mappers only use portable SQL
 * (DELETE, INSERT … ON CONFLICT DO NOTHING, SELECT), so a new database needs only a dialect.
 */
public interface SqlDialect {

    /** e.g. "SQLite (plugins/hatlas/hatlas.db)". */
    String describe();

    /** Opens a new connection with auto-commit on. */
    Connection connect() throws SQLException;

    /** Per-connection settings (pragmas, session options). */
    default void configure(Connection connection) throws SQLException {}

    /** Runs once after the schema migrations (e.g. grants for the website's read-only role). */
    default void afterMigrate(Connection connection, java.util.logging.Logger log) throws SQLException {}

    /** Column type for binary data. */
    String blobType();

    /** Column definition of an auto-increment primary key (placeholder {@code ${autoid}}). */
    String autoIdColumn();
}
