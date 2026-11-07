package org.minecraft.hatlas.storage.sql;

import org.minecraft.hatlas.storage.model.MiscData.AuctionsData;
import org.minecraft.hatlas.storage.model.MiscData.Listing;
import org.minecraft.hatlas.storage.model.MiscData.OreWeight;
import org.minecraft.hatlas.storage.model.MiscData.OreZoneRow;
import org.minecraft.hatlas.storage.model.MiscData.OreZonesData;
import org.minecraft.hatlas.storage.model.ShopsData;
import org.minecraft.hatlas.storage.model.ShopsData.Item;
import org.minecraft.hatlas.storage.model.ShopsData.ShopRow;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tables of the AUCTIONS, OREZONES and SHOPS domains. */
final class EconomyTables {

    private EconomyTables() {}

    static final class Auctions implements SqlDomain<AuctionsData> {

        @Override
        public AuctionsData load(Connection c) throws SQLException {
            List<Listing> out = new ArrayList<>();
            Sql.query(c, "SELECT * FROM auction_listings ORDER BY created_at, id", rs -> {
                UUID id = Sql.uuid(rs, "id");
                UUID seller = Sql.uuid(rs, "seller_uuid");
                if (id == null || seller == null) return;
                out.add(new Listing(id, seller, rs.getString("faction"), rs.getBytes("item"), rs.getInt("price"),
                        rs.getString("description"), rs.getLong("created_at")));
            });
            return new AuctionsData(out);
        }

        @Override
        public void replace(Connection c, AuctionsData data) throws SQLException {
            Sql.deleteAll(c, "auction_listings");
            Sql.insert(c, "auction_listings", "id, seller_uuid, faction, item, price, description, created_at", 7,
                    data.listings(), (ps, l) -> {
                        Sql.setUuid(ps, 1, l.id());
                        Sql.setUuid(ps, 2, l.seller());
                        ps.setString(3, l.faction() == null ? "" : l.faction());
                        ps.setBytes(4, l.item());
                        ps.setInt(5, l.price());
                        ps.setString(6, l.description() == null ? "" : l.description());
                        ps.setLong(7, l.createdAt());
                    });
        }
    }

    static final class OreZones implements SqlDomain<OreZonesData> {

        @Override
        public OreZonesData load(Connection c) throws SQLException {
            Map<String, OreZoneRow> zones = new LinkedHashMap<>();
            Map<String, List<OreWeight>> weights = new HashMap<>();
            Sql.query(c, "SELECT * FROM ore_zones ORDER BY name", rs -> zones.put(rs.getString("name"),
                    new OreZoneRow(rs.getString("name"), rs.getString("world"),
                            rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"), null)));
            Sql.query(c, "SELECT * FROM ore_zone_weights ORDER BY zone, position", rs ->
                    weights.computeIfAbsent(rs.getString("zone"), k -> new ArrayList<>())
                            .add(new OreWeight(rs.getString("ore"), rs.getInt("weight"))));
            List<OreZoneRow> out = new ArrayList<>();
            for (OreZoneRow z : zones.values()) {
                out.add(new OreZoneRow(z.name(), z.world(), z.minX(), z.minY(), z.minZ(), z.maxX(), z.maxY(), z.maxZ(),
                        List.copyOf(weights.getOrDefault(z.name(), List.of()))));
            }
            return new OreZonesData(out);
        }

        @Override
        public void replace(Connection c, OreZonesData data) throws SQLException {
            Sql.deleteAll(c, "ore_zone_weights", "ore_zones");
            Sql.insert(c, "ore_zones", "name, world, min_x, min_y, min_z, max_x, max_y, max_z", 8, data.zones(), (ps, z) -> {
                ps.setString(1, z.name());
                Sql.setString(ps, 2, z.world());
                ps.setInt(3, z.minX());
                ps.setInt(4, z.minY());
                ps.setInt(5, z.minZ());
                ps.setInt(6, z.maxX());
                ps.setInt(7, z.maxY());
                ps.setInt(8, z.maxZ());
            });
            List<Object[]> rows = new ArrayList<>();
            for (OreZoneRow z : data.zones()) {
                for (int i = 0; i < z.weights().size(); i++) rows.add(new Object[]{z.name(), i, z.weights().get(i)});
            }
            Sql.insert(c, "ore_zone_weights", "zone, ore, weight, position", 4, rows, (ps, r) -> {
                OreWeight w = (OreWeight) r[2];
                ps.setString(1, (String) r[0]);
                ps.setString(2, w.ore());
                ps.setInt(3, w.weight());
                ps.setInt(4, (Integer) r[1]);
            });
        }
    }

    static final class Shops implements SqlDomain<ShopsData> {

        private static final String LAST_RESET = "shops.last_reset";

        @Override
        public ShopsData load(Connection c) throws SQLException {
            Map<String, ShopRow> shops = new LinkedHashMap<>();
            Map<String, List<Item>> items = new HashMap<>();
            Sql.query(c, "SELECT * FROM shops ORDER BY name", rs -> shops.put(rs.getString("name"),
                    new ShopRow(rs.getString("name"), rs.getString("job"), Sql.location(rs, "npc_"),
                            Sql.uuid(rs, "npc_uuid"), null)));
            Sql.query(c, "SELECT * FROM shop_items ORDER BY shop, tier, position", rs ->
                    items.computeIfAbsent(rs.getString("shop"), k -> new ArrayList<>())
                            .add(new Item(rs.getString("tier"), rs.getString("item_id"), rs.getString("name"),
                                    rs.getBytes("item"), rs.getInt("price"), rs.getInt("weight"))));
            Map<String, Map<String, List<String>>> rotation = new LinkedHashMap<>();
            Sql.query(c, "SELECT * FROM shop_rotation ORDER BY shop, tier, position", rs ->
                    rotation.computeIfAbsent(rs.getString("shop"), k -> new LinkedHashMap<>())
                            .computeIfAbsent(rs.getString("tier"), k -> new ArrayList<>())
                            .add(rs.getString("item_id")));
            List<ShopRow> out = new ArrayList<>();
            for (ShopRow s : shops.values()) {
                out.add(new ShopRow(s.name(), s.job(), s.npc(), s.npcUuid(), List.copyOf(items.getOrDefault(s.name(), List.of()))));
            }
            return new ShopsData(out, rotation, WorldTables.getStateLong(c, LAST_RESET));
        }

        @Override
        public void replace(Connection c, ShopsData data) throws SQLException {
            Sql.deleteAll(c, "shop_rotation", "shop_items", "shops");
            Sql.insert(c, "shops", "name, job, npc_world, npc_x, npc_y, npc_z, npc_yaw, npc_pitch, npc_uuid", 9,
                    data.shops(), (ps, s) -> {
                        ps.setString(1, s.name());
                        ps.setString(2, s.job());
                        Sql.setLocation(ps, 3, s.npc());
                        Sql.setUuid(ps, 9, s.npcUuid());
                    });
            List<Object[]> itemRows = new ArrayList<>();
            for (ShopRow s : data.shops()) {
                Map<String, Integer> pos = new HashMap<>();
                for (Item it : s.items()) itemRows.add(new Object[]{s.name(), it, pos.merge(it.tier(), 1, Integer::sum) - 1});
            }
            Sql.insert(c, "shop_items", "shop, tier, item_id, name, item, price, weight, position", 8, itemRows, (ps, r) -> {
                Item it = (Item) r[1];
                ps.setString(1, (String) r[0]);
                ps.setString(2, it.tier());
                ps.setString(3, it.id());
                ps.setString(4, it.name());
                ps.setBytes(5, it.item());
                ps.setInt(6, it.price());
                ps.setInt(7, it.weight());
                ps.setInt(8, (Integer) r[2]);
            });
            List<Object[]> rotationRows = new ArrayList<>();
            data.rotation().forEach((shop, tiers) -> tiers.forEach((tier, ids) -> {
                for (int i = 0; i < ids.size(); i++) rotationRows.add(new Object[]{shop, tier, i, ids.get(i)});
            }));
            Sql.insert(c, "shop_rotation", "shop, tier, position, item_id", 4, rotationRows, (ps, r) -> {
                ps.setString(1, (String) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setInt(3, (Integer) r[2]);
                ps.setString(4, (String) r[3]);
            });
            WorldTables.setState(c, LAST_RESET, String.valueOf(data.lastReset()));
        }
    }
}
