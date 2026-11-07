package org.minecraft.hatlas;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.minecraft.hatlas.migration.LegacyAtlas;
import org.minecraft.hatlas.storage.StorageSettings;

import java.io.File;

/**
 * Builds the plugin classpath before Hatlas loads. SQLite needs nothing (Paper ships
 * sqlite-jdbc). The PostgreSQL driver is resolved from Maven Central (through Paper's
 * mirror) when {@code config.yml > storage.type} is {@code postgresql}, the default: also
 * on a first start, before config.yml exists. Only servers set to SQLite never download it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HatlasLoader implements PluginLoader {

    static final String POSTGRES_DRIVER = "org.postgresql:postgresql:42.7.7";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        // Plugin renamed atlas -> hatlas: move plugins/atlas before anything reads the config.
        LegacyAtlas.migrateDataFolder(classpathBuilder.getContext().getDataDirectory(),
                msg -> classpathBuilder.getContext().getLogger().info(msg));
        File config = classpathBuilder.getContext().getDataDirectory().resolve("config.yml").toFile();
        // No config.yml yet (first start): the shipped default is PostgreSQL, so load the driver.
        String type = config.isFile()
                ? YamlConfiguration.loadConfiguration(config).getString("storage.type", StorageSettings.DEFAULT_TYPE)
                : StorageSettings.DEFAULT_TYPE;
        if (!"postgresql".equalsIgnoreCase(type.trim())) return;

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact(POSTGRES_DRIVER), null));
        classpathBuilder.addLibrary(resolver);
    }
}
