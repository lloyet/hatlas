package org.minecraft.hatlas.util;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.minecraft.hatlas.Hatlas;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pushes the hatlas resource pack to players. Configured under the
 * {@code resourcepack} section of config.yml. Sends on PlayerJoinEvent
 * and pushes to every online player when the plugin enables (so /reload works).
 */
public class ResourcePackManager implements Listener {

    private static String url = "";
    private static String sha1 = "";
    private static boolean required = false;
    private static String promptText = "";

    public static void loadConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("resourcepack");
        if (section == null) {
            Hatlas.instance.getLogger().warning("No 'resourcepack' section in config.yml — pack will not be pushed.");
            return;
        }
        url        = section.getString("url", "");
        sha1       = section.getString("sha1", "");
        required   = section.getBoolean("required", false);
        promptText = section.getString("prompt", "");
    }

    /** Pushes the configured pack to a single player. No-op if URL is blank. */
    public static void sendTo(Player player) {
        if (url == null || url.isBlank()) return;

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            Hatlas.instance.getLogger().severe("Invalid resourcepack.url in config.yml: " + url);
            return;
        }

        UUID id = UUID.nameUUIDFromBytes((url + ":" + sha1).getBytes(StandardCharsets.UTF_8));

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                .id(id)
                .uri(uri)
                .hash(sha1 == null ? "" : sha1)
                .build();

        ResourcePackRequest.Builder builder = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(required)
                .replace(true);

        if (promptText != null && !promptText.isBlank()) {
            builder.prompt(Component.text(promptText));
        }

        player.sendResourcePacks(builder.build());
    }

    /** Pushes the pack to every online player (used on plugin enable / reload). */
    public static void sendToAll() {
        if (url == null || url.isBlank()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTo(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sendTo(event.getPlayer());
    }
}
