package org.minecraft.atlas;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;

/**
 * Builds the plugin classpath before Atlas loads. SQLite needs nothing (Paper ships
 * sqlite-jdbc). The PostgreSQL driver is resolved from Maven Central (through Paper's
 * mirror) only when {@code config.yml > storage.type} is {@code postgresql}, so servers on
 * SQLite/YAML never download it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AtlasLoader implements PluginLoader {

    static final String POSTGRES_DRIVER = "org.postgresql:postgresql:42.7.7";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        File config = classpathBuilder.getContext().getDataDirectory().resolve("config.yml").toFile();
        if (!config.isFile()) return;
        String type = YamlConfiguration.loadConfiguration(config).getString("storage.type", "sqlite");
        if (!"postgresql".equalsIgnoreCase(type.trim())) return;

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact(POSTGRES_DRIVER), null));
        classpathBuilder.addLibrary(resolver);
    }
}
