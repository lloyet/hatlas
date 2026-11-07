package org.minecraft.atlas.storage.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * World-independent location: a world name plus coordinates. Storage never holds Bukkit
 * {@link Location}s (they pin a {@link World} and are mutable); managers convert at the edge.
 */
public record LocationData(String world, double x, double y, double z, float yaw, float pitch) {

    /** Returns null for a null location or one without a world. */
    public static LocationData of(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return new LocationData(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    /** Returns null when the world is not loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    /** {@code world<sep>x<sep>y<sep>z<sep>yaw<sep>pitch}, the legacy YAML encodings. */
    public String encode(char sep) {
        return world + sep + x + sep + y + sep + z + sep + yaw + sep + pitch;
    }

    /** Parses {@link #encode}; yaw and pitch are optional. Returns null on malformed input. */
    public static LocationData decode(String s, char sep) {
        if (s == null) return null;
        String[] p = s.split(java.util.regex.Pattern.quote(String.valueOf(sep)));
        if (p.length < 4) return null;
        try {
            return new LocationData(p[0],
                    Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f,
                    p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
