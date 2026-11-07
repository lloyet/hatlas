package org.minecraft.hatlas.storage;

/**
 * Persistence backend (the adapter). Runtime backend: {@code SqlDataStore} (SQLite or
 * PostgreSQL through {@code SqlDialect}). {@code YamlDataStore} only reads the legacy
 * *-data.yml files for the one-time import into a fresh database.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #open} and {@link #load} run on the main thread during onEnable.</li>
 *   <li>{@link #write} runs on the single storage writer thread (or on the main thread during
 *       shutdown, after the writer stopped). Calls are never concurrent.</li>
 *   <li>{@link #write} replaces every domain present in the set and must be atomic: either
 *       all of them are stored or none.</li>
 *   <li>Snapshots are immutable, so a backend may read them from any thread.</li>
 * </ul>
 */
public interface DataStore {

    /** Short human description for logs, e.g. "SQLite (plugins/hatlas/hatlas.db)". */
    String describe();

    void open() throws StorageException;

    /** Loads every domain; a domain with no data comes back empty. */
    DataSet load() throws StorageException;

    void write(DataSet data) throws StorageException;

    /**
     * True if this backend has never been initialized (no data, no import done yet).
     * Used to run the one-time YAML import.
     */
    default boolean isFresh() throws StorageException { return false; }

    /** Records that the backend is initialized; {@link #isFresh} returns false afterwards. */
    default void markInitialized(String source) throws StorageException {}

    void close();
}
