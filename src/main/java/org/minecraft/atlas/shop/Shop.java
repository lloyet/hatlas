package org.minecraft.atlas.shop;

import org.bukkit.Location;
import org.minecraft.atlas.job.Job;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A job shop: a named NPC selling a rotating selection of items from two weighted pools
 * (common and rare). Pools come from {@code shops.yml}; the current selection is runtime
 * state persisted to {@code shops-data.yml}.
 */
public class Shop {

    private final String name;
    private final Job job;
    private Location npcLocation;
    private UUID npcUuid;

    // tier -> (entry id -> item), insertion-ordered to keep the YAML order stable
    private final Map<ShopTier, LinkedHashMap<String, ShopItem>> pools = new EnumMap<>(ShopTier.class);
    // tier -> entry ids currently on sale
    private final Map<ShopTier, List<String>> current = new EnumMap<>(ShopTier.class);

    public Shop(String name, Job job) {
        this.name = name;
        this.job  = job;
        for (ShopTier tier : ShopTier.values()) {
            pools.put(tier, new LinkedHashMap<>());
            current.put(tier, new ArrayList<>());
        }
    }

    public String   getName()        { return name; }
    public Job      getJob()         { return job; }
    public Location getNpcLocation() { return npcLocation; }
    public UUID     getNpcUuid()     { return npcUuid; }

    public void setNpcLocation(Location npcLocation) { this.npcLocation = npcLocation; }
    public void setNpcUuid(UUID npcUuid)             { this.npcUuid = npcUuid; }

    /** Mutable pool for a tier (entry id -> item). */
    public LinkedHashMap<String, ShopItem> getPool(ShopTier tier) { return pools.get(tier); }

    /** Mutable list of entry ids currently on sale for a tier. */
    public List<String> getCurrentIds(ShopTier tier) { return current.get(tier); }

    /** Items currently on sale for a tier, skipping ids whose entry was removed from the pool. */
    public List<ShopItem> getCurrentItems(ShopTier tier) {
        List<ShopItem> out = new ArrayList<>();
        Map<String, ShopItem> pool = pools.get(tier);
        for (String id : current.get(tier)) {
            ShopItem item = pool.get(id);
            if (item != null) out.add(item);
        }
        return out;
    }
}
