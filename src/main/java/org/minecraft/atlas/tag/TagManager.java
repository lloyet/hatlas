package org.minecraft.atlas.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.storage.model.MiscData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TagManager {

    /** All registered tags, keyed by name. */
    private static final Map<String, Tag> tags = new LinkedHashMap<>();

    /** PDC key stored on each TextDisplay to link it back to its tag name. */
    public static NamespacedKey keyTagName;

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    public static void init() {
        keyTagName = new NamespacedKey(Atlas.instance, "tag_name");
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /** Stored tags in worlds that are not loaded: written back unchanged. */
    private static final List<MiscData.TagRow> unresolved = new ArrayList<>();

    public static void restore(MiscData.TagsData data) {
        tags.clear();
        unresolved.clear();
        for (MiscData.TagRow row : data.tags()) {
            World world = Bukkit.getWorld(row.location().world());
            if (world == null) {
                unresolved.add(row);
                continue;
            }
            Location loc = new Location(world, row.location().x(), row.location().y(), row.location().z());
            Tag tag = new Tag(row.name(), loc, new ArrayList<>(row.lines()));
            tag.setTextDisplayUUID(row.textDisplay());
            tags.put(row.name(), tag);
        }
    }

    public static MiscData.TagsData snapshot() {
        List<MiscData.TagRow> out = new ArrayList<>();
        for (Tag tag : tags.values()) {
            LocationData loc = LocationData.of(tag.getLocation());
            if (loc == null) continue;
            out.add(new MiscData.TagRow(tag.getName(), loc, List.copyOf(tag.getLines()), tag.getTextDisplayUUID()));
        }
        for (MiscData.TagRow row : unresolved) {
            if (!tags.containsKey(row.name())) out.add(row);
        }
        return new MiscData.TagsData(List.copyOf(out));
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public static boolean tagExists(String name) {
        return tags.containsKey(name);
    }

    public static Tag getTag(String name) {
        return tags.get(name);
    }

    public static Map<String, Tag> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Creates a new tag at {@code loc} with a single first line.
     * Returns {@code null} if a tag with that name already exists.
     */
    public static Tag createTag(String name, Location loc, String firstLine) {
        if (tags.containsKey(name)) return null;

        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        Tag tag = new Tag(name, loc, lines);
        tags.put(name, tag);
        spawnOrUpdateDisplay(tag);
        return tag;
    }

    /** Removes the tag and its in-world TextDisplay. Returns false if not found. */
    public static boolean deleteTag(String name) {
        Tag tag = tags.remove(name);
        if (tag == null) return false;
        if (tag.getTextDisplayUUID() != null) {
            Entity e = Bukkit.getEntity(tag.getTextDisplayUUID());
            if (e != null) e.remove();
        }
        return true;
    }

    /** Appends a line to an existing tag. Returns false if the tag does not exist. */
    public static boolean addLine(String name, String text) {
        Tag tag = tags.get(name);
        if (tag == null) return false;
        tag.getLines().add(text);
        spawnOrUpdateDisplay(tag);
        return true;
    }

    /**
     * Replaces the line at {@code lineNumber} (1-based) with {@code text}.
     * Returns false if the tag does not exist or the line number is out of range.
     */
    public static boolean editLine(String name, int lineNumber, String text) {
        Tag tag = tags.get(name);
        if (tag == null) return false;
        List<String> lines = tag.getLines();
        if (lineNumber < 1 || lineNumber > lines.size()) return false;
        lines.set(lineNumber - 1, text);
        spawnOrUpdateDisplay(tag);
        return true;
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /** Spawns a new TextDisplay or updates the text on an existing one. */
    public static void spawnOrUpdateDisplay(Tag tag) {
        TextDisplay display = null;
        UUID existingUUID = tag.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }

        if (display == null) {
            Location loc = tag.getLocation();
            if (!loc.isChunkLoaded()) return;
            String tagName = tag.getName();
            display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
                td.getPersistentDataContainer().set(keyTagName, PersistentDataType.STRING, tagName);
            });
            tag.setTextDisplayUUID(display.getUniqueId());
        }

        display.text(buildComponent(tag.getLines()));
    }

    /**
     * Called from {@link org.minecraft.atlas.listener.TagListener} when a TextDisplay
     * with the tag PDC key loads from disk.
     */
    public static void restoreDisplay(TextDisplay display) {
        String name = display.getPersistentDataContainer().get(keyTagName, PersistentDataType.STRING);
        if (name == null) return;

        Tag tag = tags.get(name);
        if (tag == null) {
            display.remove(); // orphaned entity from a deleted tag
            return;
        }

        tag.setTextDisplayUUID(display.getUniqueId());
        display.text(buildComponent(tag.getLines()));
    }

    private static Component buildComponent(List<String> lines) {
        if (lines.isEmpty()) return Component.empty();
        Component result = LegacyComponentSerializer.legacyAmpersand().deserialize(lines.getFirst());
        for (int i = 1; i < lines.size(); i++) {
            result = result.append(Component.newline())
                    .append(LegacyComponentSerializer.legacyAmpersand().deserialize(lines.get(i)));
        }
        return result;
    }
}
