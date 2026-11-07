package org.minecraft.hatlas.storage.sql;

import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.DataSet;
import org.minecraft.hatlas.storage.DataStore;
import org.minecraft.hatlas.storage.StorageException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SQL backend. Database-specific behaviour lives in {@link SqlDialect}; the mappers
 * ({@code *Tables}) only use portable SQL. One connection, used by one thread at a time
 * (main thread while loading, then the storage writer thread); re-opened if it dropped.
 */
public final class SqlDataStore implements DataStore {

    private static final String INITIALIZED_KEY = "storage.initialized";

    private final SqlDialect dialect;
    private final Logger log;
    private final Map<DataDomain, SqlDomain<?>> domains = new EnumMap<>(DataDomain.class);
    private Connection connection;

    public SqlDataStore(SqlDialect dialect, Logger log) {
        this.dialect = dialect;
        this.log = log;
        domains.put(DataDomain.FACTIONS, new FactionTables.Factions());
        domains.put(DataDomain.CRYSTALS, new FactionTables.Crystals());
        domains.put(DataDomain.JOBS, new JobTables());
        domains.put(DataDomain.DONJONS, new WorldTables.Donjons());
        domains.put(DataDomain.HOMES, new WorldTables.Homes());
        domains.put(DataDomain.SAFEZONES, new WorldTables.SafeZones());
        domains.put(DataDomain.COMBAT, new WorldTables.Combat());
        domains.put(DataDomain.BLOCKS, new WorldTables.Blocks());
        domains.put(DataDomain.AUCTIONS, new EconomyTables.Auctions());
        domains.put(DataDomain.OREZONES, new EconomyTables.OreZones());
        domains.put(DataDomain.SHOPS, new EconomyTables.Shops());
        domains.put(DataDomain.TAGS, new WorldTables.Tags());
        domains.put(DataDomain.SPAWN, new WorldTables.Spawn());
        for (DataDomain d : DataDomain.values()) {
            if (!domains.containsKey(d)) throw new IllegalStateException("No SQL mapper for " + d);
        }
    }

    @Override
    public String describe() {
        return dialect.describe();
    }

    @Override
    public void open() throws StorageException {
        try {
            Connection c = connection();
            SchemaMigrator.migrate(c, dialect, log);
            dialect.afterMigrate(c, log);
        } catch (SQLException e) {
            throw new StorageException("Cannot open " + describe() + ": " + e.getMessage(), e);
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            try {
                if (connection.isValid(5)) return connection;
            } catch (SQLException ignored) {}
            closeQuietly();
            log.warning("Storage: connection lost, reconnecting to " + describe());
        }
        connection = dialect.connect();
        connection.setAutoCommit(true);
        dialect.configure(connection);
        return connection;
    }

    @Override
    public DataSet load() throws StorageException {
        DataSet set = new DataSet();
        try {
            Connection c = connection();
            for (Map.Entry<DataDomain, SqlDomain<?>> e : domains.entrySet()) {
                set.put(e.getKey(), e.getValue().load(c));
            }
        } catch (SQLException | RuntimeException e) {
            throw new StorageException("Cannot load data from " + describe() + ": " + e.getMessage(), e);
        }
        return set;
    }

    @Override
    public void write(DataSet data) throws StorageException {
        if (data.isEmpty()) return;
        Connection c;
        try {
            c = connection();
            c.setAutoCommit(false);
        } catch (SQLException e) {
            throw new StorageException("Cannot write to " + describe() + ": " + e.getMessage(), e);
        }
        try {
            for (DataDomain d : data.domains()) {
                replace(c, d, data.raw(d));
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw new StorageException("Write of " + data.domains() + " failed (rolled back): " + e.getMessage(), e);
        } finally {
            try {
                c.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void replace(Connection c, DataDomain d, Object value) throws SQLException {
        ((SqlDomain<T>) domains.get(d)).replace(c, (T) value);
    }

    @Override
    public boolean isFresh() throws StorageException {
        try {
            return WorldTables.getState(connection(), INITIALIZED_KEY) == null;
        } catch (SQLException e) {
            throw new StorageException("Cannot read storage state: " + e.getMessage(), e);
        }
    }

    @Override
    public void markInitialized(String source) throws StorageException {
        try {
            WorldTables.setState(connection(), INITIALIZED_KEY, System.currentTimeMillis() + " " + source);
        } catch (SQLException e) {
            throw new StorageException("Cannot write storage state: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {}
        connection = null;
    }
}
