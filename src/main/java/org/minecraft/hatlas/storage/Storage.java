package org.minecraft.hatlas.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.minecraft.hatlas.storage.sql.PostgresDialect;
import org.minecraft.hatlas.storage.sql.SqlDataStore;
import org.minecraft.hatlas.storage.sql.SqliteDialect;
import org.minecraft.hatlas.storage.yaml.YamlDataStore;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistence facade used by the managers.
 *
 * <p>Managers keep their state in memory (unchanged model). For each domain a manager
 * registers a snapshot supplier. {@link #save} takes the snapshots on the main thread and
 * hands them to a single writer thread, which stores them in one transaction. Every domain
 * is also saved by the autosave timer and synchronously on shutdown.
 *
 * <p>Safety: nothing is written before {@link #markReady} (i.e. before the data has been
 * loaded into the managers), so a failed startup can never overwrite stored data with
 * empty state.
 */
public final class Storage {

    private static final Map<DataDomain, Supplier<?>> snapshotters = new EnumMap<>(DataDomain.class);
    private static final Set<DataDomain> pendingBeforeReady = EnumSet.noneOf(DataDomain.class);

    private static Plugin plugin;
    private static Logger log;
    private static DataStore store;
    private static StorageSettings settings;
    private static ExecutorService writer;
    private static volatile boolean ready;

    private Storage() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the configured backend (importing the YAML data files on first use of a
     * database) and loads every domain. Throws if anything fails: the caller must then
     * disable the plugin.
     */
    public static DataSet start(Plugin owner, StorageSettings config) throws StorageException {
        plugin = owner;
        log = owner.getLogger();
        settings = config;
        File folder = owner.getDataFolder();

        store = switch (config.type()) {
            case SQLITE -> new SqlDataStore(new SqliteDialect(config.sqliteFile()), log);
            case POSTGRESQL -> new SqlDataStore(new PostgresDialect(config.pgHost(), config.pgPort(),
                    config.pgDatabase(), config.pgUser(), config.pgPassword(), config.pgSsl(),
                    config.pgSchema(), config.pgWebRole()), log);
        };
        try {
            store.open();
        } catch (StorageException e) {
            if (config.type() != StorageSettings.Type.POSTGRESQL) throw e;
            throw new StorageException(postgresHelp(config, e), e);
        }
        log.info("Storage: using " + store.describe());

        if (store.isFresh()) importYaml(folder);

        DataSet data = store.load();
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Hatlas-Storage");
            t.setDaemon(true);
            return t;
        });
        return data;
    }

    /** What an admin needs to know when the PostgreSQL database cannot be opened. */
    private static String postgresHelp(StorageSettings c, StorageException e) {
        String where = c.pgUser() + "@" + c.pgHost() + ":" + c.pgPort() + "/" + c.pgDatabase();
        StringBuilder msg = new StringBuilder("Cannot open the PostgreSQL database " + where + ".\n")
                .append("  Cause: ").append(rootMessage(e)).append('\n')
                .append("  Hatlas needs its database to start. Check that:\n")
                .append("   - PostgreSQL is running and reachable at ").append(c.pgHost()).append(':').append(c.pgPort()).append(";\n")
                .append("   - database '").append(c.pgDatabase()).append("' and user '").append(c.pgUser())
                .append("' exist (config.yml > storage.postgresql);\n")
                .append("   - the password is set in the HATLAS_DB_PASSWORD environment variable or storage.postgresql.password");
        if (c.pgPasswordMissing()) msg.append(" (currently: NOT SET)");
        msg.append(".\n  For a local development server without PostgreSQL, set storage.type: sqlite in config.yml.");
        return msg.toString();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String m = root.getMessage();
        return root.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    /**
     * One-time import of the legacy *-data.yml files into a fresh database, then renames them.
     * This is the only use of YAML data: it is not a storage backend any more.
     */
    private static void importYaml(File folder) throws StorageException {
        YamlDataStore yaml = new YamlDataStore(folder);
        if (!yaml.hasData()) {
            store.markInitialized("empty");
            return;
        }
        log.info("Storage: first start on " + store.describe() + ", importing *-data.yml…");
        yaml.open();
        DataSet legacy = yaml.load();
        store.write(legacy);
        store.markInitialized("yaml-import");
        yaml.close();
        for (File f : yaml.dataFiles()) {
            if (!f.isFile()) continue;
            File backup = new File(f.getParentFile(), f.getName() + ".migrated");
            if (!f.renameTo(backup)) log.warning("Storage: imported but could not rename " + f.getName());
        }
        log.info("Storage: YAML import done; old files renamed to *.yml.migrated (kept as backup).");
    }

    public static void register(DataDomain domain, Supplier<?> snapshotter) {
        snapshotters.put(domain, snapshotter);
    }

    /** Enables writes, flushes saves requested during startup and starts the autosave timer. */
    public static void markReady() {
        for (DataDomain d : DataDomain.values()) {
            if (!snapshotters.containsKey(d)) throw new IllegalStateException("No snapshot supplier for " + d);
        }
        ready = true;
        if (!pendingBeforeReady.isEmpty()) {
            save(pendingBeforeReady.toArray(new DataDomain[0]));
            pendingBeforeReady.clear();
        }
        long period = settings.autosaveMinutes() * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, Storage::saveAll, period, period);
    }

    public static boolean isReady() {
        return ready;
    }

    public static String describe() {
        return store == null ? "not started" : store.describe();
    }

    /**
     * A new, unconfigured connection to the storage database (schema-qualify every name), for
     * readers outside the storage writer such as the login ticket check. The caller closes it.
     */
    public static java.sql.Connection newConnection() throws java.sql.SQLException {
        if (!(store instanceof SqlDataStore sql)) throw new java.sql.SQLException("storage is not started");
        return sql.newConnection();
    }

    /** Stops the writer, then writes every domain synchronously and closes the backend. */
    public static void shutdown() {
        if (store == null) return;
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.severe("Storage: writer did not finish within 30 s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ready) {
            try {
                store.write(snapshot(DataDomain.values()));
                log.info("Storage: all data saved to " + store.describe());
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Storage: FINAL SAVE FAILED, recent changes may be lost", e);
            }
        }
        ready = false;
        store.close();
        store = null;
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    /** Saves the given domains asynchronously, in one transaction. Call from any thread. */
    public static void save(DataDomain... domains) {
        if (domains.length == 0) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> save(domains));
            return;
        }
        if (!ready) {
            pendingBeforeReady.addAll(java.util.List.of(domains));
            return;
        }
        DataSet set;
        try {
            set = snapshot(domains);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Storage: cannot snapshot " + java.util.Arrays.toString(domains), e);
            return;
        }
        writer.execute(() -> {
            try {
                store.write(set);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Storage: save failed, will retry at next autosave", e);
            }
        });
    }

    /**
     * Appends ruby-ledger rows on the writer thread (after any pending save). Call from the main
     * thread. On failure the rows are handed to {@code onFailure} (to be retried), never dropped
     * silently.
     */
    public static void appendLedger(java.util.List<org.minecraft.hatlas.storage.model.LedgerRow> rows,
                                    java.util.function.Consumer<java.util.List<org.minecraft.hatlas.storage.model.LedgerRow>> onFailure) {
        if (rows.isEmpty()) return;
        if (!ready || writer == null || writer.isShutdown()) {
            onFailure.accept(rows);
            return;
        }
        writer.execute(() -> {
            try {
                store.appendLedger(rows);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Storage: ruby ledger append failed, will retry", e);
                onFailure.accept(rows);
            }
        });
    }

    /**
     * Runs a read on the storage writer thread, after every save and ledger append queued
     * before it, and hands the result (or the error) to {@code reply} on the main thread.
     */
    public static <T> void readAsync(StoreReader<T> read, java.util.function.BiConsumer<T, String> reply) {
        if (!ready || writer == null || writer.isShutdown()) {
            reply.accept(null, "storage is not ready");
            return;
        }
        writer.execute(() -> {
            T value = null;
            String error = null;
            try {
                value = read.read(store);
            } catch (StorageException | RuntimeException e) {
                error = e.getMessage();
            }
            T v = value;
            String err = error;
            Bukkit.getScheduler().runTask(plugin, () -> reply.accept(v, err));
        });
    }

    @FunctionalInterface
    public interface StoreReader<T> {
        T read(DataStore store) throws StorageException;
    }

    public static void saveAll() {
        save(DataDomain.values());
    }

    /**
     * Writes every domain now and waits until it is stored (main thread only). The write is
     * queued after any pending async save, so the database is complete when this returns.
     * Used by the backup script ({@code /hatlas storage save}) so a database dump taken right
     * after matches the world saved by {@code save-all flush}. Returns null on success, else the error.
     */
    public static String flush() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Storage.flush must run on the main thread");
        if (!ready) return "storage is not ready";
        DataSet set;
        try {
            set = snapshot(DataDomain.values());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Storage: cannot snapshot for flush", e);
            return "snapshot failed: " + e.getMessage();
        }
        java.util.concurrent.Future<?> done = writer.submit(() -> {
            store.write(set);
            return null;
        });
        try {
            done.get(60, TimeUnit.SECONDS);
            log.info("Storage: all data flushed to " + store.describe());
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            log.log(Level.SEVERE, "Storage: flush failed", e.getCause());
            return "write failed: " + e.getCause().getMessage();
        } catch (java.util.concurrent.TimeoutException e) {
            return "write did not finish within 60 s";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private static DataSet snapshot(DataDomain... domains) {
        DataSet set = new DataSet();
        for (DataDomain d : domains) set.put(d, snapshotters.get(d).get());
        return set;
    }
}
