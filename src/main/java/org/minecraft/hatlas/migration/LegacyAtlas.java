package org.minecraft.hatlas.migration;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Migration from the plugin's former name "atlas" (renamed "hatlas" on 2026-11-07).
 * Everything here is a no-op on a server that never ran the old plugin.
 * <ul>
 *   <li>{@link #migrateDataFolder}: {@code plugins/atlas} → {@code plugins/hatlas} (and a default
 *       SQLite file {@code atlas.db} → {@code hatlas.db}), run by the loader before anything reads
 *       the config;</li>
 *   <li>{@link #migrateSchemaTable}: {@code atlas_schema_version} → {@code hatlas_schema_version};</li>
 *   <li>{@link #migrate(PersistentDataContainer)}: persistent-data keys {@code atlas:*} →
 *       {@code hatlas:*} on entities (when they load), players (on join), container contents
 *       (when opened) and stored item stacks (when deserialized, see {@code ItemBytes}).</li>
 * </ul>
 * Not migrated: permission nodes ({@code atlas.*} → {@code hatlas.*}, to update in the
 * permission plugin), items inside shulker boxes or bundles, the {@code /atlas} command
 * (now {@code /hatlas}), the {@code ATLAS_DB_PASSWORD} variable (still read as a fallback).
 */
public final class LegacyAtlas {

    public static final String OLD_NAME = "atlas";
    public static final String NEW_NAMESPACE = "hatlas";

    private LegacyAtlas() {}

    // ── Data folder ──────────────────────────────────────────────────────────

    /**
     * Moves {@code <plugins>/atlas} to {@code newDir} when the new folder does not exist yet (or is
     * empty). Leaves both alone, with a warning, when both hold files. Then renames a default
     * SQLite database {@code atlas.db} (+ WAL files) when the config does not name a file.
     */
    public static void migrateDataFolder(Path newDir, Consumer<String> log) {
        Path oldDir = newDir.resolveSibling(OLD_NAME);
        try {
            if (Files.isDirectory(oldDir)) {
                if (!Files.exists(newDir) || isEmptyDir(newDir)) {
                    if (Files.exists(newDir)) Files.delete(newDir);
                    Files.move(oldDir, newDir);
                    log.accept("Migration: moved data folder " + oldDir + " to " + newDir + " (plugin renamed atlas -> hatlas)");
                } else {
                    log.accept("Migration: both " + oldDir + " and " + newDir + " exist; using " + newDir
                            + " and leaving the old folder untouched");
                }
            }
            Path oldDb = newDir.resolve("atlas.db");
            Path newDb = newDir.resolve("hatlas.db");
            if (Files.isRegularFile(oldDb) && !Files.exists(newDb) && !configNamesSqliteFile(newDir.resolve("config.yml"))) {
                for (String suffix : new String[]{"", "-wal", "-shm"}) {
                    Path from = newDir.resolve("atlas.db" + suffix);
                    if (Files.exists(from)) Files.move(from, newDir.resolve("hatlas.db" + suffix));
                }
                log.accept("Migration: renamed atlas.db to hatlas.db");
            }
        } catch (IOException e) {
            log.accept("Migration: could not move the old data folder: " + e.getMessage());
        }
    }

    private static boolean isEmptyDir(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        }
    }

    /** True if config.yml sets storage.sqlite.file explicitly (then the file name is kept as is). */
    private static boolean configNamesSqliteFile(Path config) {
        if (!Files.isRegularFile(config)) return false;
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile())
                .isSet("storage.sqlite.file");
    }

    // ── Database ─────────────────────────────────────────────────────────────

    /** Renames the old schema-version table, if present, before the migrator uses the new one. */
    public static void migrateSchemaTable(Connection c, java.util.logging.Logger log) throws SQLException {
        if (!tableExists(c, "atlas_schema_version") || tableExists(c, "hatlas_schema_version")) return;
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE atlas_schema_version RENAME TO hatlas_schema_version");
        }
        log.info("Migration: renamed table atlas_schema_version to hatlas_schema_version");
    }

    private static boolean tableExists(Connection c, String name) throws SQLException {
        for (String n : new String[]{name, name.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = c.getMetaData().getTables(null, null, n, new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    // ── Persistent data keys ─────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final PersistentDataType[] TYPES = {
            PersistentDataType.STRING, PersistentDataType.INTEGER, PersistentDataType.DOUBLE,
            PersistentDataType.LONG, PersistentDataType.BYTE, PersistentDataType.LONG_ARRAY,
            PersistentDataType.FLOAT, PersistentDataType.SHORT, PersistentDataType.BYTE_ARRAY,
            PersistentDataType.INTEGER_ARRAY, PersistentDataType.TAG_CONTAINER,
    };

    /**
     * Renames every {@code atlas:<key>} entry of {@code pdc} to {@code hatlas:<key>} (value and type
     * kept; an existing new key is not overwritten). Returns true if something changed.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean migrate(PersistentDataContainer pdc) {
        if (pdc == null || pdc.isEmpty()) return false;
        List<NamespacedKey> old = new ArrayList<>();
        for (NamespacedKey k : pdc.getKeys()) if (OLD_NAME.equals(k.getNamespace())) old.add(k);
        if (old.isEmpty()) return false;
        for (NamespacedKey k : old) {
            NamespacedKey target = new NamespacedKey(NEW_NAMESPACE, k.getKey());
            for (PersistentDataType type : TYPES) {
                if (!pdc.has(k, type)) continue;
                if (!pdc.has(target)) pdc.set(target, type, pdc.get(k, type));
                break;
            }
            pdc.remove(k);
        }
        return true;
    }

    /** Migrates an item stack's keys in place. Returns true if it changed. */
    public static boolean migrate(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!migrate(meta.getPersistentDataContainer())) return false;
        item.setItemMeta(meta);
        return true;
    }

    public static void migrate(ItemStack[] items) {
        if (items == null) return;
        for (ItemStack i : items) migrate(i);
    }

    private static void migrate(Inventory inv) {
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (ItemStack i : contents) changed |= migrate(i);
        if (changed) inv.setContents(contents);
    }

    public static void migrate(Entity e) {
        migrate(e.getPersistentDataContainer());
        if (e instanceof Item item) {
            ItemStack stack = item.getItemStack();
            if (migrate(stack)) item.setItemStack(stack);
        }
    }

    /** Migrates the entities already loaded when the plugin enables (before crystals are restored). */
    public static void migrateLoadedEntities() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) migrate(e);
        }
    }

    /** Runs before every other listener (LOWEST) so they already see the new keys. */
    public static final class EventListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onEntitiesLoad(EntitiesLoadEvent event) {
            for (Entity e : event.getEntities()) migrate(e);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onJoin(PlayerJoinEvent event) {
            Player p = event.getPlayer();
            migrate(p.getPersistentDataContainer());
            migrate(p.getInventory());
            migrate(p.getEnderChest());
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onInventoryOpen(InventoryOpenEvent event) {
            migrate(event.getInventory());
        }
    }
}
