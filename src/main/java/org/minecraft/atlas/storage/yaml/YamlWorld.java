package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.atlas.storage.model.BlockPos;
import org.minecraft.atlas.storage.model.DonjonsData;
import org.minecraft.atlas.storage.model.DonjonsData.DonjonRow;
import org.minecraft.atlas.storage.model.HomesData;
import org.minecraft.atlas.storage.model.LocationData;
import org.minecraft.atlas.storage.model.MiscData.BlocksData;
import org.minecraft.atlas.storage.model.MiscData.CombatData;
import org.minecraft.atlas.storage.model.MiscData.CustomBlock;
import org.minecraft.atlas.storage.model.MiscData.SpawnData;
import org.minecraft.atlas.storage.model.MiscData.TagRow;
import org.minecraft.atlas.storage.model.MiscData.TagsData;
import org.minecraft.atlas.storage.model.SafeZonesData;
import org.minecraft.atlas.storage.model.SafeZonesData.Zone;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** DONJONS, HOMES, SAFEZONES, COMBAT, BLOCKS, TAGS and SPAWN in their data files. */
final class YamlWorld {

    private YamlWorld() {}

    private static Map<UUID, Set<String>> readVisits(ConfigurationSection sec) {
        Map<UUID, Set<String>> out = new HashMap<>();
        if (sec == null) return out;
        for (String k : sec.getKeys(false)) {
            UUID u = YamlUtil.uuid(k);
            List<String> ids = sec.getStringList(k);
            if (u != null && !ids.isEmpty()) out.put(u, new LinkedHashSet<>(ids));
        }
        return out;
    }

    private static void writeVisits(YamlConfiguration cfg, String key, Map<UUID, Set<String>> visits) {
        cfg.set(key, null);
        if (visits.isEmpty()) return;
        ConfigurationSection sec = cfg.createSection(key);
        visits.forEach((u, ids) -> {
            if (!ids.isEmpty()) sec.set(u.toString(), new ArrayList<>(ids));
        });
    }

    // ── DONJONS ───────────────────────────────────────────────────────────────

    static final class Donjons implements YamlDomain<DonjonsData> {

        @Override
        public String file() { return "donjons-data.yml"; }

        @Override
        public DonjonsData read(YamlConfiguration cfg) {
            List<DonjonRow> rows = new ArrayList<>();
            ConfigurationSection inst = cfg.getConfigurationSection("instances");
            if (inst != null) {
                for (String id : inst.getKeys(false)) {
                    ConfigurationSection s = inst.getConfigurationSection(id);
                    if (s == null) continue;
                    String world = s.getString("world");
                    if (world == null) continue;
                    BlockPos nametag = s.contains("nametag_x")
                            ? new BlockPos(world, s.getInt("nametag_x"), s.getInt("nametag_y"), s.getInt("nametag_z")) : null;
                    BlockPos vault = s.contains("vault_x")
                            ? new BlockPos(world, s.getInt("vault_x"), s.getInt("vault_y"), s.getInt("vault_z")) : null;
                    LocationData tp = s.contains("teleport_spawn_x")
                            ? new LocationData(world, s.getDouble("teleport_spawn_x"), s.getDouble("teleport_spawn_y"),
                            s.getDouble("teleport_spawn_z"), (float) s.getDouble("teleport_spawn_yaw"),
                            (float) s.getDouble("teleport_spawn_pitch")) : null;

                    // spawn_points: "id,x,y,z" (current) or "x,y,z" (legacy, ids assigned in order)
                    Map<Integer, BlockPos> spawns = new LinkedHashMap<>();
                    int nextId = 0;
                    for (String enc : s.getStringList("spawn_points")) {
                        String[] p = enc.split(",");
                        try {
                            if (p.length == 4) {
                                int sid = Integer.parseInt(p[0].trim());
                                spawns.put(sid, new BlockPos(world, Integer.parseInt(p[1].trim()),
                                        Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())));
                                nextId = Math.max(nextId, sid + 1);
                            } else if (p.length == 3) {
                                spawns.put(nextId++, new BlockPos(world, Integer.parseInt(p[0].trim()),
                                        Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    List<Long> chunks = new ArrayList<>();
                    for (String k : s.getStringList("protected_chunks")) {
                        try {
                            chunks.add(Long.parseLong(k));
                        } catch (NumberFormatException ignored) {}
                    }
                    rows.add(new DonjonRow(id, s.getString("type", ""), s.getString("name", "Unknown"),
                            s.getInt("level", 0), s.getString("rarity", "COMMON"), s.getString("status", "IDLE"),
                            s.getLong("activation_time", 0L), s.getBoolean("timeout_warned", false), world,
                            nametag, vault, tp, YamlUtil.uuid(s.getString("text_display_uuid")), spawns, chunks));
                }
            }
            return new DonjonsData(rows, readVisits(cfg.getConfigurationSection("visited_donjons")),
                    cfg.getLong("last_activation_time", 0L));
        }

        @Override
        public void write(YamlConfiguration cfg, DonjonsData data) {
            cfg.set("last_activation_time", data.lastActivation());
            writeVisits(cfg, "visited_donjons", data.visits());
            cfg.set("instances", null);
            if (data.donjons().isEmpty()) return;
            ConfigurationSection inst = cfg.createSection("instances");
            for (DonjonRow d : data.donjons()) {
                ConfigurationSection s = inst.createSection(d.id());
                s.set("type", d.type());
                s.set("name", d.name());
                s.set("level", d.level());
                s.set("rarity", d.rarity());
                s.set("status", d.status());
                s.set("activation_time", d.activationTime());
                s.set("timeout_warned", d.timeoutWarned());
                s.set("world", d.world());
                if (d.nametag() != null) {
                    s.set("nametag_x", d.nametag().x());
                    s.set("nametag_y", d.nametag().y());
                    s.set("nametag_z", d.nametag().z());
                }
                if (!d.spawnPoints().isEmpty()) {
                    List<String> enc = new ArrayList<>();
                    d.spawnPoints().forEach((id, p) -> enc.add(id + "," + p.x() + "," + p.y() + "," + p.z()));
                    s.set("spawn_points", enc);
                }
                if (!d.protectedChunks().isEmpty()) {
                    List<String> keys = new ArrayList<>();
                    for (long k : d.protectedChunks()) keys.add(String.valueOf(k));
                    s.set("protected_chunks", keys);
                }
                if (d.textDisplay() != null) s.set("text_display_uuid", d.textDisplay().toString());
                if (d.vault() != null) {
                    s.set("vault_x", d.vault().x());
                    s.set("vault_y", d.vault().y());
                    s.set("vault_z", d.vault().z());
                }
                LocationData tp = d.teleportSpawn();
                if (tp != null) {
                    s.set("teleport_spawn_x", tp.x());
                    s.set("teleport_spawn_y", tp.y());
                    s.set("teleport_spawn_z", tp.z());
                    s.set("teleport_spawn_yaw", (double) tp.yaw());
                    s.set("teleport_spawn_pitch", (double) tp.pitch());
                }
            }
        }
    }

    // ── HOMES ─────────────────────────────────────────────────────────────────

    static final class Homes implements YamlDomain<HomesData> {

        @Override
        public String file() { return "homes-data.yml"; }

        @Override
        public HomesData read(YamlConfiguration cfg) {
            List<HomesData.Home> homes = new ArrayList<>();
            ConfigurationSection sec = cfg.getConfigurationSection("player_homes");
            if (sec == null) return new HomesData(homes);
            for (String k : sec.getKeys(false)) {
                UUID u = YamlUtil.uuid(k);
                ConfigurationSection ps = sec.getConfigurationSection(k);
                if (u == null || ps == null) continue;
                if (ps.contains("world")) {
                    // Legacy single-home format
                    LocationData loc = YamlUtil.readLocation(ps);
                    if (loc != null) homes.add(new HomesData.Home(u, ps.getString("name", "home"), loc));
                    continue;
                }
                for (String name : ps.getKeys(false)) {
                    LocationData loc = YamlUtil.readLocation(ps.getConfigurationSection(name));
                    if (loc != null) homes.add(new HomesData.Home(u, name, loc));
                }
            }
            return new HomesData(homes);
        }

        @Override
        public void write(YamlConfiguration cfg, HomesData data) {
            cfg.set("player_homes", null);
            if (data.homes().isEmpty()) return;
            ConfigurationSection sec = cfg.createSection("player_homes");
            for (HomesData.Home h : data.homes()) {
                if (h.location() == null) continue;
                ConfigurationSection ps = sec.getConfigurationSection(h.player().toString());
                if (ps == null) ps = sec.createSection(h.player().toString());
                YamlUtil.writeLocation(ps.createSection(h.name()), h.location());
            }
        }
    }

    // ── SAFEZONES ─────────────────────────────────────────────────────────────

    static final class SafeZones implements YamlDomain<SafeZonesData> {

        @Override
        public String file() { return "safezones-data.yml"; }

        @Override
        public SafeZonesData read(YamlConfiguration cfg) {
            List<Zone> zones = new ArrayList<>();
            ConfigurationSection root = cfg.getConfigurationSection("safezones");
            if (root != null) {
                for (String name : root.getKeys(false)) {
                    ConfigurationSection s = root.getConfigurationSection(name);
                    if (s == null) {
                        zones.add(new Zone(name.toLowerCase(), "", null, List.of()));
                        continue;
                    }
                    zones.add(new Zone(name.toLowerCase(), s.getString("description", ""),
                            LocationData.decode(s.getString("spawn_point"), ':'), YamlUtil.readChunks(s, "chunks")));
                }
            }
            return new SafeZonesData(zones, readVisits(cfg.getConfigurationSection("safezone_visits")));
        }

        @Override
        public void write(YamlConfiguration cfg, SafeZonesData data) {
            cfg.set("safezones", null);
            if (!data.zones().isEmpty()) {
                ConfigurationSection root = cfg.createSection("safezones");
                for (Zone z : data.zones()) {
                    ConfigurationSection s = root.createSection(z.name());
                    YamlUtil.writeChunks(s, "chunks", z.chunks());
                    if (z.spawn() != null) s.set("spawn_point", z.spawn().encode(':'));
                    if (z.description() != null && !z.description().isEmpty()) s.set("description", z.description());
                }
            }
            writeVisits(cfg, "safezone_visits", data.visits());
        }
    }

    // ── COMBAT ────────────────────────────────────────────────────────────────

    static final class Combat implements YamlDomain<CombatData> {

        @Override
        public String file() { return "combats-data.yml"; }

        @Override
        public CombatData read(YamlConfiguration cfg) {
            Map<UUID, Long> out = new HashMap<>();
            ConfigurationSection sec = cfg.getConfigurationSection("combat_log.in_combat");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    UUID u = YamlUtil.uuid(k);
                    if (u != null) out.put(u, sec.getLong(k));
                }
            }
            return new CombatData(out);
        }

        @Override
        public void write(YamlConfiguration cfg, CombatData data) {
            cfg.set("combat_log.in_combat", null);
            data.inCombatUntil().forEach((u, until) -> cfg.set("combat_log.in_combat." + u, until));
        }
    }

    // ── BLOCKS ────────────────────────────────────────────────────────────────

    static final class Blocks implements YamlDomain<BlocksData> {

        @Override
        public String file() { return "blocks-data.yml"; }

        @Override
        public BlocksData read(YamlConfiguration cfg) {
            List<CustomBlock> out = new ArrayList<>();
            ConfigurationSection sec = cfg.getConfigurationSection("custom-blocks");
            if (sec == null) return new BlocksData(out);
            for (String k : sec.getKeys(false)) {
                ConfigurationSection b = sec.getConfigurationSection(k);
                if (b == null || b.getString("world") == null || b.getString("id") == null) continue;
                out.add(new CustomBlock(new BlockPos(b.getString("world"), b.getInt("x"), b.getInt("y"), b.getInt("z")),
                        b.getString("id")));
            }
            return new BlocksData(out);
        }

        @Override
        public void write(YamlConfiguration cfg, BlocksData data) {
            cfg.set("custom-blocks", null);
            int i = 0;
            for (CustomBlock b : data.blocks()) {
                String path = "custom-blocks." + (i++);
                cfg.set(path + ".id", b.blockId());
                cfg.set(path + ".world", b.pos().world());
                cfg.set(path + ".x", b.pos().x());
                cfg.set(path + ".y", b.pos().y());
                cfg.set(path + ".z", b.pos().z());
            }
        }
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    static final class Tags implements YamlDomain<TagsData> {

        @Override
        public String file() { return "tags-data.yml"; }

        @Override
        public TagsData read(YamlConfiguration cfg) {
            List<TagRow> out = new ArrayList<>();
            ConfigurationSection sec = cfg.getConfigurationSection("tags");
            if (sec == null) return new TagsData(out);
            for (String name : sec.getKeys(false)) {
                ConfigurationSection t = sec.getConfigurationSection(name);
                if (t == null || t.getString("world") == null) continue;
                out.add(new TagRow(name, new LocationData(t.getString("world"), t.getDouble("x"), t.getDouble("y"),
                        t.getDouble("z"), 0f, 0f), t.getStringList("lines"), YamlUtil.uuid(t.getString("text_display_uuid"))));
            }
            return new TagsData(out);
        }

        @Override
        public void write(YamlConfiguration cfg, TagsData data) {
            cfg.set("tags", null);
            if (data.tags().isEmpty()) return;
            ConfigurationSection sec = cfg.createSection("tags");
            for (TagRow t : data.tags()) {
                ConfigurationSection s = sec.createSection(t.name());
                s.set("world", t.location().world());
                s.set("x", t.location().x());
                s.set("y", t.location().y());
                s.set("z", t.location().z());
                s.set("lines", new ArrayList<>(t.lines()));
                if (t.textDisplay() != null) s.set("text_display_uuid", t.textDisplay().toString());
            }
        }
    }

    // ── SPAWN ─────────────────────────────────────────────────────────────────

    /** server-data.yml {@code spawn}; falls back to the legacy {@code spawn.*} keys of config.yml. */
    static final class Spawn implements YamlDomain<SpawnData> {

        private final File legacyConfig;

        Spawn(File legacyConfig) {
            this.legacyConfig = legacyConfig;
        }

        @Override
        public String file() { return "server-data.yml"; }

        @Override
        public SpawnData read(YamlConfiguration cfg) {
            LocationData spawn = YamlUtil.readLocation(cfg.getConfigurationSection("spawn"));
            if (spawn == null && legacyConfig.exists()) {
                spawn = YamlUtil.readLocation(YamlConfiguration.loadConfiguration(legacyConfig).getConfigurationSection("spawn"));
            }
            return new SpawnData(spawn);
        }

        @Override
        public void write(YamlConfiguration cfg, SpawnData data) {
            cfg.set("spawn", null);
            if (data.spawn() != null) YamlUtil.writeLocation(cfg.createSection("spawn"), data.spawn());
        }
    }
}
