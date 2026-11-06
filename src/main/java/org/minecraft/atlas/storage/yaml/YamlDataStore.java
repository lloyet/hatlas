package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.atlas.storage.DataDomain;
import org.minecraft.atlas.storage.DataSet;
import org.minecraft.atlas.storage.DataStore;
import org.minecraft.atlas.storage.StorageException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * YAML backend: the historical {@code *-data.yml} files in the plugin folder. Reads every
 * legacy layout. Item stacks are written as Paper bytes in Base64 ({@code *_b64} keys).
 * Each file is written to a temp file and moved into place, so a crash never leaves a
 * half-written file. Domains sharing a file (factions + crystals) are written together.
 */
public final class YamlDataStore implements DataStore {

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

    @Override
    public String describe() {
        return "YAML (" + folder.getPath() + "/*-data.yml)";
    }

    @Override
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

    @Override
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

    @Override
    public void write(DataSet data) throws StorageException {
        Map<String, List<DataDomain>> byFile = new LinkedHashMap<>();
        for (DataDomain d : data.domains()) byFile.computeIfAbsent(domains.get(d).file(), k -> new ArrayList<>()).add(d);
        for (Map.Entry<String, List<DataDomain>> e : byFile.entrySet()) {
            YamlConfiguration cfg = file(e.getKey());
            for (DataDomain d : e.getValue()) writeDomain(cfg, d, data.raw(d));
            save(cfg, new File(folder, e.getKey()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeDomain(YamlConfiguration cfg, DataDomain d, Object value) {
        ((YamlDomain<T>) domains.get(d)).write(cfg, (T) value);
    }

    private static void save(YamlConfiguration cfg, File target) throws StorageException {
        Path dest = target.toPath();
        Path tmp = dest.resolveSibling(target.getName() + ".tmp");
        try {
            Files.writeString(tmp, cfg.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Cannot save " + target.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        files.clear();
    }
}
