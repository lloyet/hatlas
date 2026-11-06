package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.atlas.storage.ItemBytes;
import org.minecraft.atlas.storage.model.MiscData.AuctionsData;
import org.minecraft.atlas.storage.model.MiscData.Listing;
import org.minecraft.atlas.storage.model.MiscData.OreWeight;
import org.minecraft.atlas.storage.model.MiscData.OreZoneRow;
import org.minecraft.atlas.storage.model.MiscData.OreZonesData;
import org.minecraft.atlas.storage.model.ShopsData;
import org.minecraft.atlas.storage.model.ShopsData.Item;
import org.minecraft.atlas.storage.model.ShopsData.ShopRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** AUCTIONS, OREZONES and SHOPS in their data files. */
final class YamlEconomy {

    private YamlEconomy() {}

    static final class Auctions implements YamlDomain<AuctionsData> {

        @Override
        public String file() { return "auctions-data.yml"; }

        @Override
        public AuctionsData read(YamlConfiguration cfg) {
            List<Listing> out = new ArrayList<>();
            ConfigurationSection root = cfg.getConfigurationSection("listings");
            if (root == null) return new AuctionsData(out);
            for (String k : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(k);
                UUID id = YamlUtil.uuid(k);
                if (s == null || id == null) continue;
                UUID seller = YamlUtil.uuid(s.getString("seller"));
                byte[] item = ItemBytes.fromBase64(s.getString("item_b64"));
                if (item == null) item = ItemBytes.fromItem(s.getItemStack("item"));
                if (seller == null || item == null) continue;
                out.add(new Listing(id, seller, s.getString("faction", ""), item, s.getInt("price", 0),
                        s.getString("description", ""), s.getLong("created_at", System.currentTimeMillis())));
            }
            return new AuctionsData(out);
        }

        @Override
        public void write(YamlConfiguration cfg, AuctionsData data) {
            cfg.set("listings", null);
            if (data.listings().isEmpty()) return;
            ConfigurationSection root = cfg.createSection("listings");
            for (Listing l : data.listings()) {
                ConfigurationSection s = root.createSection(l.id().toString());
                s.set("seller", l.seller().toString());
                if (l.faction() != null && !l.faction().isEmpty()) s.set("faction", l.faction());
                s.set("item_b64", ItemBytes.toBase64(l.item()));
                s.set("price", l.price());
                if (l.description() != null && !l.description().isEmpty()) s.set("description", l.description());
                s.set("created_at", l.createdAt());
            }
        }
    }

    static final class OreZones implements YamlDomain<OreZonesData> {

        @Override
        public String file() { return "orezones-data.yml"; }

        @Override
        public OreZonesData read(YamlConfiguration cfg) {
            List<OreZoneRow> out = new ArrayList<>();
            ConfigurationSection root = cfg.getConfigurationSection("orezones");
            if (root == null) return new OreZonesData(out);
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) continue;
                List<OreWeight> weights = new ArrayList<>();
                ConfigurationSection w = s.getConfigurationSection("ore_weights");
                if (w != null) for (String ore : w.getKeys(false)) weights.add(new OreWeight(ore.toLowerCase(), w.getInt(ore, 0)));
                boolean bounded = s.getString("world") != null && s.contains("min_x");
                out.add(new OreZoneRow(name.toLowerCase(), bounded ? s.getString("world") : null,
                        s.getInt("min_x"), s.getInt("min_y"), s.getInt("min_z"),
                        s.getInt("max_x"), s.getInt("max_y"), s.getInt("max_z"), weights));
            }
            return new OreZonesData(out);
        }

        @Override
        public void write(YamlConfiguration cfg, OreZonesData data) {
            cfg.set("orezones", null);
            if (data.zones().isEmpty()) return;
            ConfigurationSection root = cfg.createSection("orezones");
            for (OreZoneRow z : data.zones()) {
                ConfigurationSection s = root.createSection(z.name());
                if (z.world() != null) {
                    s.set("world", z.world());
                    s.set("min_x", z.minX());
                    s.set("min_y", z.minY());
                    s.set("min_z", z.minZ());
                    s.set("max_x", z.maxX());
                    s.set("max_y", z.maxY());
                    s.set("max_z", z.maxZ());
                }
                ConfigurationSection w = s.createSection("ore_weights");
                for (OreWeight ow : z.weights()) w.set(ow.ore(), ow.weight());
            }
        }
    }

    /**
     * shops-data.yml: {@code last_reset}, {@code rotation.<shop>.<tier>} (legacy key
     * {@code shops.<shop>.<tier>}) and {@code definitions.<shop>} (shops created by /shop).
     * Shops still hand-written in shops.yml are imported by ShopManager, not here.
     */
    static final class Shops implements YamlDomain<ShopsData> {

        @Override
        public String file() { return "shops-data.yml"; }

        @Override
        public ShopsData read(YamlConfiguration cfg) {
            Map<String, Map<String, List<String>>> rotation = new LinkedHashMap<>();
            ConfigurationSection rot = cfg.getConfigurationSection("rotation");
            if (rot == null) rot = cfg.getConfigurationSection("shops");
            if (rot != null) {
                for (String shop : rot.getKeys(false)) {
                    ConfigurationSection s = rot.getConfigurationSection(shop);
                    if (s == null) continue;
                    Map<String, List<String>> tiers = new LinkedHashMap<>();
                    for (String tier : s.getKeys(false)) tiers.put(tier, s.getStringList(tier));
                    rotation.put(shop, tiers);
                }
            }

            List<ShopRow> shops = new ArrayList<>();
            ConfigurationSection defs = cfg.getConfigurationSection("definitions");
            if (defs != null) {
                for (String name : defs.getKeys(false)) {
                    ConfigurationSection s = defs.getConfigurationSection(name);
                    if (s == null) continue;
                    ConfigurationSection npc = s.getConfigurationSection("npc");
                    List<Item> items = new ArrayList<>();
                    ConfigurationSection pools = s.getConfigurationSection("items");
                    if (pools != null) {
                        for (String tier : pools.getKeys(false)) {
                            ConfigurationSection pool = pools.getConfigurationSection(tier);
                            if (pool == null) continue;
                            for (String id : pool.getKeys(false)) {
                                ConfigurationSection e = pool.getConfigurationSection(id);
                                byte[] item = e == null ? null : ItemBytes.fromBase64(e.getString("item_b64"));
                                if (item == null) continue;
                                items.add(new Item(tier, id, e.getString("name", id), item,
                                        e.getInt("price", 1), e.getInt("weight", 1)));
                            }
                        }
                    }
                    shops.add(new ShopRow(name, s.getString("job", ""), YamlUtil.readLocation(npc),
                            npc == null ? null : YamlUtil.uuid(npc.getString("uuid")), items));
                }
            }
            return new ShopsData(shops, rotation, cfg.getLong("last_reset", 0L));
        }

        @Override
        public void write(YamlConfiguration cfg, ShopsData data) {
            cfg.set("last_reset", data.lastReset());
            cfg.set("shops", null);
            cfg.set("rotation", null);
            ConfigurationSection rot = cfg.createSection("rotation");
            data.rotation().forEach((shop, tiers) -> tiers.forEach((tier, ids) ->
                    rot.set(shop + "." + tier, new ArrayList<>(ids))));

            cfg.set("definitions", null);
            if (data.shops().isEmpty()) return;
            ConfigurationSection defs = cfg.createSection("definitions");
            for (ShopRow shop : data.shops()) {
                ConfigurationSection s = defs.createSection(shop.name());
                s.set("job", shop.job());
                if (shop.npc() != null || shop.npcUuid() != null) {
                    ConfigurationSection npc = s.createSection("npc");
                    if (shop.npc() != null) YamlUtil.writeLocation(npc, shop.npc());
                    if (shop.npcUuid() != null) npc.set("uuid", shop.npcUuid().toString());
                }
                for (Item it : shop.items()) {
                    ConfigurationSection e = s.createSection("items." + it.tier() + "." + it.id());
                    e.set("name", it.name());
                    e.set("item_b64", ItemBytes.toBase64(it.item()));
                    e.set("price", it.price());
                    e.set("weight", it.weight());
                }
            }
        }
    }
}
