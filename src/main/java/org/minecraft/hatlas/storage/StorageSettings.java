package org.minecraft.hatlas.storage;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * {@code config.yml > storage}. The PostgreSQL password can also come from the environment
 * variable {@code HATLAS_DB_PASSWORD} (takes precedence), so it does not need to be in a file.
 */
public record StorageSettings(Type type, int autosaveMinutes, File sqliteFile,
                              String pgHost, int pgPort, String pgDatabase, String pgUser,
                              String pgPassword, boolean pgSsl, String pgSchema, String pgWebRole) {

    private static final java.util.regex.Pattern IDENT = java.util.regex.Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /**
     * Runtime backends. YAML files are no longer a backend: legacy {@code *-data.yml} files are
     * only read once, to import them into a fresh database.
     */
    public enum Type { SQLITE, POSTGRESQL }

    /** Backend used when {@code storage.type} is missing. Must match {@code HatlasLoader}. */
    public static final String DEFAULT_TYPE = "postgresql";

    /** True when the PostgreSQL password comes from neither the environment nor config.yml. */
    public boolean pgPasswordMissing() {
        return pgPassword == null || pgPassword.isEmpty();
    }

    public static StorageSettings from(ConfigurationSection root, File dataFolder, Logger log) throws StorageException {
        ConfigurationSection s = root.getConfigurationSection("storage");
        String rawType = s == null ? DEFAULT_TYPE : s.getString("type", DEFAULT_TYPE);
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
        String file = s == null ? "hatlas.db" : s.getString("sqlite.file", "hatlas.db");
        File sqlite = new File(file).isAbsolute() ? new File(file) : new File(dataFolder, file);

        String envPassword = System.getenv("HATLAS_DB_PASSWORD");
        if (envPassword == null) envPassword = System.getenv("ATLAS_DB_PASSWORD"); // name before the rename
        String password = envPassword != null ? envPassword : (s == null ? "" : s.getString("postgresql.password", ""));
        String schema = (s == null ? "hatlas" : s.getString("postgresql.schema", "hatlas")).trim();
        String webRole = (s == null ? "hatlas_web" : s.getString("postgresql.web_role", "hatlas_web")).trim();
        if (!IDENT.matcher(schema).matches()) {
            throw new StorageException("config.yml storage.postgresql.schema '" + schema + "' must be a lowercase identifier");
        }
        if (!webRole.isEmpty() && !IDENT.matcher(webRole).matches()) {
            throw new StorageException("config.yml storage.postgresql.web_role '" + webRole + "' must be a lowercase identifier (or empty)");
        }
        return new StorageSettings(type, autosave, sqlite,
                s == null ? "localhost" : s.getString("postgresql.host", "localhost"),
                s == null ? 5432 : s.getInt("postgresql.port", 5432),
                s == null ? "hatlas" : s.getString("postgresql.database", "hatlas"),
                s == null ? "hatlas" : s.getString("postgresql.username", "hatlas"),
                password,
                s != null && s.getBoolean("postgresql.ssl", false),
                schema, webRole);
    }
}
