package org.minecraft.hatlas.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.DataSet;
import org.minecraft.hatlas.storage.StorageException;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only reader of the historical {@code *-data.yml} files in the plugin folder, used only
 * for the one-time import into a fresh database ({@code Storage.importYaml}). YAML is not a
 * storage backend any more. Reads every legacy layout; item stacks are Paper bytes in Base64
 * ({@code *_b64} keys) or legacy Bukkit-serialized items.
 */
public final class YamlDataStore {

    private final File folder;
    private final Map<DataDomain, YamlDomain<?>> domains = new EnumMap<>(DataDomain.class);
    private final Map<String, YamlConfiguration> files = new HashMap<>();

    public YamlDataStore(File folder) {
        this.folder = folder;
        domains.put(DataDomain.FACTIONS, new YamlFactions.Factions());
        domains.put(DataDomain.CRYSTALS, new YamlFactions.Crystals());
        domains.put(DataDomain.JOBS, new YamlJobs());
        domains.put(DataDomain.DONJONS, new YamlWorld.Donjons());
        domains.put(DataDomain.HOMES, new YamlWorld.Homes());
        domains.put(DataDomain.SAFEZONES, new YamlWorld.SafeZones());
        domains.put(DataDomain.COMBAT, new YamlWorld.Combat());
        domains.put(DataDomain.BLOCKS, new YamlWorld.Blocks());
        domains.put(DataDomain.AUCTIONS, new YamlEconomy.Auctions());
        domains.put(DataDomain.OREZONES, new YamlEconomy.OreZones());
        domains.put(DataDomain.SHOPS, new YamlEconomy.Shops());
        domains.put(DataDomain.TAGS, new YamlWorld.Tags());
        domains.put(DataDomain.SPAWN, new YamlWorld.Spawn(new File(folder, "config.yml")));
        for (DataDomain d : DataDomain.values()) {
            if (!domains.containsKey(d)) throw new IllegalStateException("No YAML mapper for " + d);
        }
    }

    public String describe() {
        return "YAML (" + folder.getPath() + "/*-data.yml)";
    }

    public void open() {
        if (!folder.exists()) folder.mkdirs();
    }

    /** The data files this store reads or writes (existing or not). */
    public List<File> dataFiles() {
        Set<String> names = new LinkedHashSet<>();
        for (YamlDomain<?> d : domains.values()) names.add(d.file());
        List<File> out = new ArrayList<>();
        for (String n : names) out.add(new File(folder, n));
        return out;
    }

    /** True if at least one data file exists (something to import). */
    public boolean hasData() {
        for (File f : dataFiles()) if (f.isFile() && f.length() > 0) return true;
        return false;
    }

    private YamlConfiguration file(String name) {
        return files.computeIfAbsent(name, n -> YamlConfiguration.loadConfiguration(new File(folder, n)));
    }

    public DataSet load() throws StorageException {
        DataSet set = new DataSet();
        for (Map.Entry<DataDomain, YamlDomain<?>> e : domains.entrySet()) {
            try {
                set.put(e.getKey(), e.getValue().read(file(e.getValue().file())));
            } catch (RuntimeException ex) {
                throw new StorageException("Cannot read " + e.getValue().file() + " (" + e.getKey() + "): " + ex.getMessage(), ex);
            }
        }
        return set;
    }

    public void close() {
        files.clear();
    }
}
