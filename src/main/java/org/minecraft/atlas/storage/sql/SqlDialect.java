package org.minecraft.atlas.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * What differs between SQL databases. The mappers only use portable SQL
 * (DELETE, INSERT … ON CONFLICT DO NOTHING, SELECT), so a new database needs only a dialect.
 */
public interface SqlDialect {

    /** e.g. "SQLite (plugins/atlas/atlas.db)". */
    String describe();

    /** Opens a new connection with auto-commit on. */
    Connection connect() throws SQLException;

    /** Per-connection settings (pragmas, session options). */
    default void configure(Connection connection) throws SQLException {}

    /** Column type for binary data. */
    String blobType();
}
