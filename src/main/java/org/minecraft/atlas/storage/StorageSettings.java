package org.minecraft.atlas.storage;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * {@code config.yml > storage}. The PostgreSQL password can also come from the environment
 * variable {@code ATLAS_DB_PASSWORD} (takes precedence), so it does not need to be in a file.
 */
public record StorageSettings(Type type, int autosaveMinutes, File sqliteFile,
                              String pgHost, int pgPort, String pgDatabase, String pgUser,
                              String pgPassword, boolean pgSsl) {

    /**
     * Runtime backends. YAML files are no longer a backend: legacy {@code *-data.yml} files are
     * only read once, to import them into a fresh database.
     */
    public enum Type { SQLITE, POSTGRESQL }

    public static StorageSettings from(ConfigurationSection root, File dataFolder, Logger log) throws StorageException {
        ConfigurationSection s = root.getConfigurationSection("storage");
        String rawType = s == null ? "sqlite" : s.getString("type", "sqlite");
        Type type;
        if (rawType.trim().equalsIgnoreCase("yaml")) {
            // Old setting from before YAML storage was removed: use SQLite, which imports the
            // *-data.yml files on its first start.
            log.warning("Storage: storage.type 'yaml' is no longer supported, using sqlite. "
                    + "Existing *-data.yml files are imported on the first start. Set storage.type: sqlite.");
            type = Type.SQLITE;
        } else {
            try {
                type = Type.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new StorageException("config.yml storage.type '" + rawType + "' is invalid (sqlite, postgresql)");
            }
        }
        int autosave = s == null ? 5 : Math.max(1, s.getInt("autosave_minutes", 5));
        String file = s == null ? "atlas.db" : s.getString("sqlite.file", "atlas.db");
        File sqlite = new File(file).isAbsolute() ? new File(file) : new File(dataFolder, file);

        String envPassword = System.getenv("ATLAS_DB_PASSWORD");
        String password = envPassword != null ? envPassword : (s == null ? "" : s.getString("postgresql.password", ""));
        return new StorageSettings(type, autosave, sqlite,
                s == null ? "localhost" : s.getString("postgresql.host", "localhost"),
                s == null ? 5432 : s.getInt("postgresql.port", 5432),
                s == null ? "atlas" : s.getString("postgresql.database", "atlas"),
                s == null ? "atlas" : s.getString("postgresql.username", "atlas"),
                password,
                s != null && s.getBoolean("postgresql.ssl", false));
    }
}
