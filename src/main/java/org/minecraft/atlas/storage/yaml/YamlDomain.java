package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Maps one domain to sections of a data file. Several domains may share a file
 * (FACTIONS and CRYSTALS both live in factions-data.yml); each one only touches its own keys.
 */
interface YamlDomain<T> {

    /** File name in the plugin data folder, e.g. "factions-data.yml". */
    String file();

    /** Reads the domain, accepting legacy layouts. Runs on the main thread. */
    T read(YamlConfiguration cfg);

    /** Clears the domain's keys and writes {@code data}. May run off the main thread. */
    void write(YamlConfiguration cfg, T data);
}
