package org.minecraft.atlas.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.minecraft.atlas.storage.sql.PostgresDialect;
import org.minecraft.atlas.storage.sql.SqlDataStore;
import org.minecraft.atlas.storage.sql.SqliteDialect;
import org.minecraft.atlas.storage.yaml.YamlDataStore;

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
                    config.pgDatabase(), config.pgUser(), config.pgPassword(), config.pgSsl()), log);
        };
        store.open();
        log.info("Storage: using " + store.describe());

        if (store.isFresh()) importYaml(folder);

        DataSet data = store.load();
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Atlas-Storage");
            t.setDaemon(true);
            return t;
        });
        return data;
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

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Writes a full backup of the current data to a new, portable SQLite file (whatever the
     * backend). Snapshots are taken now on the main thread (so the export includes changes
     * not saved yet); the file is written on the storage thread, after any pending save.
     * {@code done} runs on the main thread with null on success or the error message.
     * Restoring = stop the server and use the file as {@code atlas.db} (SQLite backend).
     */
    public static void export(File target, java.util.function.Consumer<String> done) {
        if (!ready) {
            done.accept("storage is not ready");
            return;
        }
        if (target.exists()) {
            done.accept("file already exists: " + target.getName());
            return;
        }
        DataSet set;
        try {
            set = snapshot(DataDomain.values());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Storage: cannot snapshot for export", e);
            done.accept("snapshot failed: " + e.getMessage());
            return;
        }
        writer.execute(() -> {
            String error = null;
            SqlDataStore out = new SqlDataStore(SqliteDialect.portable(target), log);
            try {
                out.open();
                out.write(set);
                out.markInitialized("export from " + store.describe());
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Storage: export to " + target + " failed", e);
                error = e.getMessage();
            } finally {
                out.close();
            }
            if (error != null && target.exists() && !target.delete()) {
                log.warning("Storage: could not delete incomplete export " + target);
            }
            String result = error;
            if (result == null) log.info("Storage: exported all data to " + target.getPath());
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
        });
    }

    public static void saveAll() {
        save(DataDomain.values());
    }

    private static DataSet snapshot(DataDomain... domains) {
        DataSet set = new DataSet();
        for (DataDomain d : domains) set.put(d, snapshotters.get(d).get());
        return set;
    }
}
