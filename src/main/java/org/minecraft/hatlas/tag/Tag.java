package org.minecraft.hatlas.tag;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Tag {

    private final String name;
    private final Location location;
    private final List<String> lines;
    private UUID textDisplayUUID;

    public Tag(String name, Location location, List<String> lines) {
        this.name = name;
        this.location = location.clone();
        this.lines = new ArrayList<>(lines);
    }

    public String getName() { return name; }
    public Location getLocation() { return location.clone(); }
    public List<String> getLines() { return lines; }
    public UUID getTextDisplayUUID() { return textDisplayUUID; }
    public void setTextDisplayUUID(UUID uuid) { this.textDisplayUUID = uuid; }
}
