package org.minecraft.hatlas.storage.sql;

import org.minecraft.hatlas.storage.model.BlockPos;
import org.minecraft.hatlas.storage.model.ChunkPos;
import org.minecraft.hatlas.storage.model.DonjonsData;
import org.minecraft.hatlas.storage.model.DonjonsData.DonjonRow;
import org.minecraft.hatlas.storage.model.HomesData;
import org.minecraft.hatlas.storage.model.LocationData;
import org.minecraft.hatlas.storage.model.MiscData.BlocksData;
import org.minecraft.hatlas.storage.model.MiscData.CombatData;
import org.minecraft.hatlas.storage.model.MiscData.CustomBlock;
import org.minecraft.hatlas.storage.model.MiscData.SpawnData;
import org.minecraft.hatlas.storage.model.MiscData.TagRow;
import org.minecraft.hatlas.storage.model.MiscData.TagsData;
import org.minecraft.hatlas.storage.model.SafeZonesData;
import org.minecraft.hatlas.storage.model.SafeZonesData.Zone;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Tables of the DONJONS, HOMES, SAFEZONES, COMBAT, BLOCKS, TAGS and SPAWN domains. */
final class WorldTables {

    private WorldTables() {}

    // ── server_state helpers ──────────────────────────────────────────────────

    static String getState(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT state_value FROM server_state WHERE state_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    static void setState(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM server_state WHERE state_key = ?")) {
            del.setString(1, key);
            del.executeUpdate();
        }
        if (value == null) return;
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO server_state (state_key, state_value) VALUES (?, ?)")) {
            ins.setString(1, key);
            ins.setString(2, value);
            ins.executeUpdate();
        }
    }

    static long getStateLong(Connection c, String key) throws SQLException {
        String v = getState(c, key);
        if (v == null) return 0L;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Map<UUID, Set<String>> loadVisits(Connection c, String table, String column) throws SQLException {
        Map<UUID, Set<String>> visits = new HashMap<>();
        Sql.query(c, "SELECT * FROM " + table + " ORDER BY player_uuid, " + column, rs -> {
            UUID p = Sql.uuid(rs, "player_uuid");
            if (p != null) visits.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(rs.getString(column));
        });
        return visits;
    }

    private static void insertVisits(Connection c, String table, String column, Map<UUID, Set<String>> visits) throws SQLException {
        List<SimpleEntry<UUID, String>> rows = new ArrayList<>();
        visits.forEach((p, set) -> set.forEach(v -> rows.add(new SimpleEntry<>(p, v))));
        Sql.insert(c, table, "player_uuid, " + column, 2, rows, (ps, e) -> {
            Sql.setUuid(ps, 1, e.getKey());
            ps.setString(2, e.getValue());
        });
    }

    // ── DONJONS ───────────────────────────────────────────────────────────────

    static final class Donjons implements SqlDomain<DonjonsData> {

        private static final String LAST_ACTIVATION = "donjon.last_activation";

        @Override
        public DonjonsData load(Connection c) throws SQLException {
            Map<String, DonjonRow> rows = new LinkedHashMap<>();
            Map<String, Map<Integer, BlockPos>> spawns = new HashMap<>();
            Map<String, List<Long>> chunks = new HashMap<>();
            Sql.query(c, "SELECT * FROM donjons ORDER BY id", rs -> {
                String world = rs.getString("world");
                Integer nx = Sql.intOrNull(rs, "nametag_x");
                Integer vx = Sql.intOrNull(rs, "vault_x");
                Double tx = Sql.doubleOrNull(rs, "tp_x");
                rows.put(rs.getString("id"), new DonjonRow(rs.getString("id"), rs.getString("type"),
                        rs.getString("name"), rs.getInt("level"), rs.getString("rarity"), rs.getString("status"),
                        rs.getLong("activation_time"), rs.getBoolean("timeout_warned"), world,
                        nx == null ? null : new BlockPos(world, nx, rs.getInt("nametag_y"), rs.getInt("nametag_z")),
                        vx == null ? null : new BlockPos(world, vx, rs.getInt("vault_y"), rs.getInt("vault_z")),
                        tx == null ? null : new LocationData(world, tx, rs.getDouble("tp_y"), rs.getDouble("tp_z"),
                                rs.getFloat("tp_yaw"), rs.getFloat("tp_pitch")),
                        Sql.uuid(rs, "text_display_uuid"), null, null));
            });
            Sql.query(c, "SELECT * FROM donjon_spawn_points ORDER BY donjon_id, point_id", rs -> {
                String id = rs.getString("donjon_id");
                DonjonRow d = rows.get(id);
                if (d != null) spawns.computeIfAbsent(id, k -> new TreeMap<>()).put(rs.getInt("point_id"),
                        new BlockPos(d.world(), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
            });
            Sql.query(c, "SELECT * FROM donjon_chunks ORDER BY donjon_id, chunk_x, chunk_z", rs ->
                    chunks.computeIfAbsent(rs.getString("donjon_id"), k -> new ArrayList<>())
                            .add(ChunkPos.packKey(rs.getInt("chunk_x"), rs.getInt("chunk_z"))));
            List<DonjonRow> out = new ArrayList<>();
            for (DonjonRow d : rows.values()) {
                out.add(new DonjonRow(d.id(), d.type(), d.name(), d.level(), d.rarity(), d.status(),
                        d.activationTime(), d.timeoutWarned(), d.world(), d.nametag(), d.vault(),
                        d.teleportSpawn(), d.textDisplay(),
                        Map.copyOf(spawns.getOrDefault(d.id(), Map.of())),
                        List.copyOf(chunks.getOrDefault(d.id(), List.of()))));
            }
            return new DonjonsData(out, loadVisits(c, "donjon_visits", "donjon_id"), getStateLong(c, LAST_ACTIVATION));
        }

        @Override
        public void replace(Connection c, DonjonsData data) throws SQLException {
            Sql.deleteAll(c, "donjon_visits", "donjon_chunks", "donjon_spawn_points", "donjons");
            Sql.insert(c, "donjons",
                    "id, type, name, level, rarity, status, activation_time, timeout_warned, world, "
                            + "nametag_x, nametag_y, nametag_z, vault_x, vault_y, vault_z, "
                            + "tp_x, tp_y, tp_z, tp_yaw, tp_pitch, text_display_uuid", 21,
                    data.donjons(), (ps, d) -> {
                        ps.setString(1, d.id());
                        ps.setString(2, d.type());
                        ps.setString(3, d.name());
                        ps.setInt(4, d.level());
                        ps.setString(5, d.rarity());
                        ps.setString(6, d.status());
                        ps.setLong(7, d.activationTime());
                        ps.setBoolean(8, d.timeoutWarned());
                        ps.setString(9, d.world());
                        BlockPos n = d.nametag(), v = d.vault();
                        Sql.setInt(ps, 10, n == null ? null : n.x());
                        Sql.setInt(ps, 11, n == null ? null : n.y());
                        Sql.setInt(ps, 12, n == null ? null : n.z());
                        Sql.setInt(ps, 13, v == null ? null : v.x());
                        Sql.setInt(ps, 14, v == null ? null : v.y());
                        Sql.setInt(ps, 15, v == null ? null : v.z());
                        LocationData t = d.teleportSpawn();
                        Sql.setDouble(ps, 16, t == null ? null : t.x());
                        Sql.setDouble(ps, 17, t == null ? null : t.y());
                        Sql.setDouble(ps, 18, t == null ? null : t.z());
                        Sql.setFloat(ps, 19, t == null ? null : t.yaw());
                        Sql.setFloat(ps, 20, t == null ? null : t.pitch());
                        Sql.setUuid(ps, 21, d.textDisplay());
                    });
            List<Object[]> spawnRows = new ArrayList<>();
            List<Object[]> chunkRows = new ArrayList<>();
            for (DonjonRow d : data.donjons()) {
                d.spawnPoints().forEach((id, p) -> spawnRows.add(new Object[]{d.id(), id, p}));
                for (long key : d.protectedChunks()) chunkRows.add(new Object[]{d.id(), key});
            }
            Sql.insert(c, "donjon_spawn_points", "donjon_id, point_id, x, y, z", 5, spawnRows, (ps, r) -> {
                BlockPos p = (BlockPos) r[2];
                ps.setString(1, (String) r[0]);
                ps.setInt(2, (Integer) r[1]);
                ps.setInt(3, p.x());
                ps.setInt(4, p.y());
                ps.setInt(5, p.z());
            });
            Sql.insert(c, "donjon_chunks", "donjon_id, chunk_x, chunk_z", 3, chunkRows, (ps, r) -> {
                long key = (Long) r[1];
                ps.setString(1, (String) r[0]);
                ps.setInt(2, ChunkPos.unpackX(key));
                ps.setInt(3, ChunkPos.unpackZ(key));
            });
            insertVisits(c, "donjon_visits", "donjon_id", data.visits());
            setState(c, LAST_ACTIVATION, String.valueOf(data.lastActivation()));
        }
    }

    // ── HOMES ─────────────────────────────────────────────────────────────────

    static final class Homes implements SqlDomain<HomesData> {

        @Override
        public HomesData load(Connection c) throws SQLException {
            List<HomesData.Home> homes = new ArrayList<>();
            Sql.query(c, "SELECT * FROM player_homes ORDER BY player_uuid, position", rs -> {
                UUID p = Sql.uuid(rs, "player_uuid");
                if (p != null) homes.add(new HomesData.Home(p, rs.getString("name"), Sql.location(rs, "")));
            });
            return new HomesData(homes);
        }

        @Override
        public void replace(Connection c, HomesData data) throws SQLException {
            Sql.deleteAll(c, "player_homes");
            Map<UUID, Integer> positions = new HashMap<>();
            List<Object[]> rows = new ArrayList<>();
            for (HomesData.Home h : data.homes()) {
                if (h.location() == null) continue;
                rows.add(new Object[]{h, positions.merge(h.player(), 1, Integer::sum) - 1});
            }
            Sql.insert(c, "player_homes", "player_uuid, name, position, world, x, y, z, yaw, pitch", 9, rows, (ps, r) -> {
                HomesData.Home h = (HomesData.Home) r[0];
                Sql.setUuid(ps, 1, h.player());
                ps.setString(2, h.name());
                ps.setInt(3, (Integer) r[1]);
                Sql.setLocation(ps, 4, h.location());
            });
        }
    }

    // ── SAFEZONES ─────────────────────────────────────────────────────────────

    static final class SafeZones implements SqlDomain<SafeZonesData> {

        @Override
        public SafeZonesData load(Connection c) throws SQLException {
            Map<String, Zone> zones = new LinkedHashMap<>();
            Map<String, List<ChunkPos>> chunks = new HashMap<>();
            Sql.query(c, "SELECT * FROM safezones ORDER BY name", rs ->
                    zones.put(rs.getString("name"), new Zone(rs.getString("name"), rs.getString("description"),
                            Sql.location(rs, "spawn_"), null)));
            Sql.query(c, "SELECT * FROM safezone_chunks ORDER BY safezone, world, chunk_x, chunk_z", rs ->
                    chunks.computeIfAbsent(rs.getString("safezone"), k -> new ArrayList<>())
                            .add(new ChunkPos(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z"))));
            List<Zone> out = new ArrayList<>();
            for (Zone z : zones.values()) {
                out.add(new Zone(z.name(), z.description(), z.spawn(), List.copyOf(chunks.getOrDefault(z.name(), List.of()))));
            }
            return new SafeZonesData(out, loadVisits(c, "safezone_visits", "safezone"));
        }

        @Override
        public void replace(Connection c, SafeZonesData data) throws SQLException {
            Sql.deleteAll(c, "safezone_visits", "safezone_chunks", "safezones");
            Sql.insert(c, "safezones", "name, description, spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch", 8,
                    data.zones(), (ps, z) -> {
                        ps.setString(1, z.name());
                        ps.setString(2, z.description() == null ? "" : z.description());
                        Sql.setLocation(ps, 3, z.spawn());
                    });
            List<SimpleEntry<String, ChunkPos>> rows = new ArrayList<>();
            for (Zone z : data.zones()) for (ChunkPos p : z.chunks()) rows.add(new SimpleEntry<>(z.name(), p));
            Sql.insert(c, "safezone_chunks", "safezone, world, chunk_x, chunk_z", 4, rows, (ps, e) -> {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue().world());
                ps.setInt(3, e.getValue().x());
                ps.setInt(4, e.getValue().z());
            });
            insertVisits(c, "safezone_visits", "safezone", data.visits());
        }
    }

    // ── COMBAT ────────────────────────────────────────────────────────────────

    static final class Combat implements SqlDomain<CombatData> {

        @Override
        public CombatData load(Connection c) throws SQLException {
            Map<UUID, Long> out = new HashMap<>();
            Sql.query(c, "SELECT * FROM combat_timers", rs -> {
                UUID p = Sql.uuid(rs, "player_uuid");
                if (p != null) out.put(p, rs.getLong("until_ms"));
            });
            return new CombatData(out);
        }

        @Override
        public void replace(Connection c, CombatData data) throws SQLException {
            Sql.deleteAll(c, "combat_timers");
            Sql.insert(c, "combat_timers", "player_uuid, until_ms", 2, data.inCombatUntil().entrySet(), (ps, e) -> {
                Sql.setUuid(ps, 1, e.getKey());
                ps.setLong(2, e.getValue());
            });
        }
    }

    // ── BLOCKS ────────────────────────────────────────────────────────────────

    static final class Blocks implements SqlDomain<BlocksData> {

        @Override
        public BlocksData load(Connection c) throws SQLException {
            List<CustomBlock> out = new ArrayList<>();
            Sql.query(c, "SELECT * FROM custom_blocks ORDER BY world, x, y, z", rs ->
                    out.add(new CustomBlock(new BlockPos(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                            rs.getString("block_id"))));
            return new BlocksData(out);
        }

        @Override
        public void replace(Connection c, BlocksData data) throws SQLException {
            Sql.deleteAll(c, "custom_blocks");
            Sql.insert(c, "custom_blocks", "world, x, y, z, block_id", 5, data.blocks(), (ps, b) -> {
                ps.setString(1, b.pos().world());
                ps.setInt(2, b.pos().x());
                ps.setInt(3, b.pos().y());
                ps.setInt(4, b.pos().z());
                ps.setString(5, b.blockId());
            });
        }
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    static final class Tags implements SqlDomain<TagsData> {

        @Override
        public TagsData load(Connection c) throws SQLException {
            Map<String, TagRow> tags = new LinkedHashMap<>();
            Map<String, List<String>> lines = new HashMap<>();
            Sql.query(c, "SELECT * FROM tags ORDER BY name", rs -> tags.put(rs.getString("name"),
                    new TagRow(rs.getString("name"),
                            new LocationData(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), 0f, 0f),
                            null, Sql.uuid(rs, "text_display_uuid"))));
            Sql.query(c, "SELECT * FROM tag_lines ORDER BY tag, line_no", rs ->
                    lines.computeIfAbsent(rs.getString("tag"), k -> new ArrayList<>()).add(rs.getString("text")));
            List<TagRow> out = new ArrayList<>();
            for (TagRow t : tags.values()) {
                out.add(new TagRow(t.name(), t.location(), List.copyOf(lines.getOrDefault(t.name(), List.of())), t.textDisplay()));
            }
            return new TagsData(out);
        }

        @Override
        public void replace(Connection c, TagsData data) throws SQLException {
            Sql.deleteAll(c, "tag_lines", "tags");
            Sql.insert(c, "tags", "name, world, x, y, z, text_display_uuid", 6, data.tags(), (ps, t) -> {
                ps.setString(1, t.name());
                ps.setString(2, t.location().world());
                ps.setDouble(3, t.location().x());
                ps.setDouble(4, t.location().y());
                ps.setDouble(5, t.location().z());
                Sql.setUuid(ps, 6, t.textDisplay());
            });
            List<Object[]> rows = new ArrayList<>();
            for (TagRow t : data.tags()) for (int i = 0; i < t.lines().size(); i++) rows.add(new Object[]{t.name(), i, t.lines().get(i)});
            Sql.insert(c, "tag_lines", "tag, line_no, text", 3, rows, (ps, r) -> {
                ps.setString(1, (String) r[0]);
                ps.setInt(2, (Integer) r[1]);
                ps.setString(3, (String) r[2]);
            });
        }
    }

    // ── SPAWN ─────────────────────────────────────────────────────────────────

    static final class Spawn implements SqlDomain<SpawnData> {

        private static final String KEY = "spawn.location";

        @Override
        public SpawnData load(Connection c) throws SQLException {
            return new SpawnData(LocationData.decode(getState(c, KEY), ','));
        }

        @Override
        public void replace(Connection c, SpawnData data) throws SQLException {
            setState(c, KEY, data.spawn() == null ? null : data.spawn().encode(','));
        }
    }
}
