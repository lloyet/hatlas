package org.minecraft.hatlas.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;

/** Maps one domain snapshot to its tables. {@link #replace} runs inside the caller's transaction. */
interface SqlDomain<T> {

    T load(Connection c) throws SQLException;

    /** Deletes the domain's rows and inserts {@code data}. */
    void replace(Connection c, T data) throws SQLException;
}
