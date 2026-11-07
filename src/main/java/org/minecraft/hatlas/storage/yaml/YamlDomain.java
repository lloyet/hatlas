package org.minecraft.hatlas.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads one domain from sections of a legacy data file (one-time import only). Several domains may share a file
 * (FACTIONS and CRYSTALS both live in factions-data.yml); each one only touches its own keys.
 */
interface YamlDomain<T> {

    /** File name in the plugin data folder, e.g. "factions-data.yml". */
    String file();

    /** Reads the domain, accepting legacy layouts. Runs on the main thread. */
    T read(YamlConfiguration cfg);
}
