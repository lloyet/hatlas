package org.minecraft.hatlas.access;

import org.bukkit.configuration.ConfigurationSection;

import java.util.regex.Pattern;

/**
 * {@code config.yml > access}: Hatlas's own ticket check at login (defence in depth behind the
 * {@code hatlas-proxy} Velocity plugin, which does the real gate). See {@code docs/access.md}.
 *
 * @param requireTicket refuse a login without a valid row in {@code <webSchema>.tickets}
 * @param webSchema     schema of the website that holds the {@code tickets} table
 * @param renewUrl      shown in the refusal message
 * @param opsBypass     server operators always get in (so an admin is never locked out)
 */
public record AccessSettings(boolean requireTicket, String webSchema, String renewUrl, boolean opsBypass) {

    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    public static AccessSettings from(ConfigurationSection root) {
        ConfigurationSection s = root.getConfigurationSection("access");
        boolean require = s != null && s.getBoolean("require_ticket", false);
        String schema = (s == null ? "web" : s.getString("web_schema", "web")).trim();
        if (!IDENT.matcher(schema).matches()) {
            throw new IllegalArgumentException("config.yml access.web_schema '" + schema + "' must be a lowercase identifier");
        }
        String url = s == null ? "" : s.getString("renew_url", "").trim();
        boolean ops = s == null || s.getBoolean("ops_bypass", true);
        return new AccessSettings(require, schema, url, ops);
    }
}
