package org.minecraft.hatlas.storage.sql;

import org.minecraft.hatlas.storage.model.ChunkPos;
import org.minecraft.hatlas.storage.model.CrystalsData;
import org.minecraft.hatlas.storage.model.CrystalsData.Chest;
import org.minecraft.hatlas.storage.model.CrystalsData.CrystalRow;
import org.minecraft.hatlas.storage.model.CrystalsData.Protection;
import org.minecraft.hatlas.storage.model.FactionsData;
import org.minecraft.hatlas.storage.model.FactionsData.FactionRow;
import org.minecraft.hatlas.storage.model.FactionsData.Member;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tables of the FACTIONS and CRYSTALS domains. */
final class FactionTables {

    private FactionTables() {}

    static final class Factions implements SqlDomain<FactionsData> {

        @Override
        public FactionsData load(Connection c) throws SQLException {
            Map<String, Builder> byName = new LinkedHashMap<>();
            Sql.query(c, "SELECT * FROM factions ORDER BY name", rs -> {
                Builder b = new Builder();
                b.name = rs.getString("name");
                b.owner = Sql.uuid(rs, "owner_uuid");
                b.color = rs.getString("color");
                b.description = rs.getString("description");
                b.level = rs.getInt("level");
                b.exp = rs.getInt("exp");
                b.skillPoints = rs.getInt("skill_points");
                b.outpost = rs.getBoolean("outpost_unlocked");
                b.vaultSize = Sql.intOrNull(rs, "vault_size");
                b.downgradeMul = rs.getInt("downgrade_mul");
                b.downgradeEnd = rs.getLong("downgrade_window_end");
                b.vault = rs.getBytes("vault_contents");
                if (b.owner != null) byName.put(b.name, b);
            });
            Sql.query(c, "SELECT * FROM faction_members ORDER BY faction, player_uuid", rs -> {
                Builder b = byName.get(rs.getString("faction"));
                UUID uuid = Sql.uuid(rs, "player_uuid");
                if (b != null && uuid != null) {
                    b.members.add(new Member(uuid, rs.getBoolean("is_member"), rs.getString("role")));
                }
            });
            Sql.query(c, "SELECT * FROM faction_allies ORDER BY faction, ally", rs -> {
                Builder b = byName.get(rs.getString("faction"));
                if (b != null) b.allies.add(rs.getString("ally"));
            });
            Sql.query(c, "SELECT * FROM faction_skill_tiers ORDER BY faction, skill, tier_index", rs -> {
                Builder b = byName.get(rs.getString("faction"));
                if (b == null) return;
                int idx = rs.getInt("tier_index");
                switch (rs.getString("skill")) {
                    case "homes" -> b.homeTiers.add(idx);
                    case "vault" -> b.vaultTiers.add(idx);
                    default -> { }
                }
            });
            List<FactionRow> rows = new ArrayList<>();
            for (Builder b : byName.values()) rows.add(b.build());
            return new FactionsData(rows);
        }

        @Override
        public void replace(Connection c, FactionsData data) throws SQLException {
            Sql.deleteAll(c, "faction_skill_tiers", "faction_allies", "faction_members", "factions");
            Sql.insert(c, "factions",
                    "name, owner_uuid, color, description, level, exp, skill_points, outpost_unlocked, "
                            + "vault_size, downgrade_mul, downgrade_window_end, vault_contents", 12,
                    data.factions(), (ps, f) -> {
                        ps.setString(1, f.name());
                        Sql.setUuid(ps, 2, f.owner());
                        ps.setString(3, f.color());
                        ps.setString(4, f.description());
                        ps.setInt(5, f.level());
                        ps.setInt(6, f.exp());
                        ps.setInt(7, f.skillPoints());
                        ps.setBoolean(8, f.outpostUnlocked());
                        Sql.setInt(ps, 9, f.vaultSize());
                        ps.setInt(10, f.downgradeMul());
                        ps.setLong(11, f.downgradeWindowEnd());
                        Sql.setBytes(ps, 12, f.vaultContents());
                    });
            List<SimpleEntry<String, Member>> members = new ArrayList<>();
            List<String[]> allies = new ArrayList<>();
            List<Object[]> tiers = new ArrayList<>();
            for (FactionRow f : data.factions()) {
                for (Member m : f.members()) members.add(new SimpleEntry<>(f.name(), m));
                for (String a : f.allies()) allies.add(new String[]{f.name(), a});
                for (int t : f.homeTiers()) tiers.add(new Object[]{f.name(), "homes", t});
                for (int t : f.vaultTiers()) tiers.add(new Object[]{f.name(), "vault", t});
            }
            Sql.insert(c, "faction_members", "faction, player_uuid, is_member, role", 4, members, (ps, e) -> {
                ps.setString(1, e.getKey());
                Sql.setUuid(ps, 2, e.getValue().uuid());
                ps.setBoolean(3, e.getValue().member());
                Sql.setString(ps, 4, e.getValue().role());
            });
            Sql.insert(c, "faction_allies", "faction, ally", 2, allies, (ps, a) -> {
                ps.setString(1, a[0]);
                ps.setString(2, a[1]);
            });
            Sql.insert(c, "faction_skill_tiers", "faction, skill, tier_index", 3, tiers, (ps, t) -> {
                ps.setString(1, (String) t[0]);
                ps.setString(2, (String) t[1]);
                ps.setInt(3, (Integer) t[2]);
            });
        }

        private static final class Builder {
            String name, color, description;
            UUID owner;
            int level, exp, skillPoints, downgradeMul;
            boolean outpost;
            Integer vaultSize;
            long downgradeEnd;
            byte[] vault;
            final List<Member> members = new ArrayList<>();
            final List<String> allies = new ArrayList<>();
            final List<Integer> homeTiers = new ArrayList<>();
            final List<Integer> vaultTiers = new ArrayList<>();

            FactionRow build() {
                return new FactionRow(name, owner, color, description, level, exp, skillPoints, outpost,
                        vaultSize, downgradeMul, downgradeEnd, List.copyOf(members), List.copyOf(allies),
                        List.copyOf(homeTiers), List.copyOf(vaultTiers), vault);
            }
        }
    }

    static final class Crystals implements SqlDomain<CrystalsData> {

        @Override
        public CrystalsData load(Connection c) throws SQLException {
            Map<UUID, Builder> byId = new LinkedHashMap<>();
            Sql.query(c, "SELECT * FROM crystals ORDER BY faction, uuid", rs -> {
                UUID id = Sql.uuid(rs, "uuid");
                if (id == null) return;
                Builder b = new Builder();
                b.row = new CrystalRow(id, rs.getString("faction"), rs.getString("name"),
                        rs.getDouble("hp"), rs.getDouble("max_hp"), rs.getBoolean("outpost"),
                        Sql.location(rs, "home_"),
                        Sql.doubleOrNull(rs, "hp_bonus"), Sql.doubleOrNull(rs, "regen_bonus"),
                        Sql.intOrNull(rs, "claim_capacity"), Sql.intOrNull(rs, "spent_skill_points"),
                        Sql.longOrNull(rs, "immune_until"), null, null, null);
                byId.put(id, b);
            });
            Sql.query(c, "SELECT * FROM crystal_claims ORDER BY crystal_uuid, world, chunk_x, chunk_z", rs -> {
                Builder b = byId.get(Sql.uuid(rs, "crystal_uuid"));
                if (b != null) b.claims.add(new ChunkPos(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z")));
            });
            Sql.query(c, "SELECT * FROM crystal_chests ORDER BY crystal_uuid, chest_index", rs -> {
                Builder b = byId.get(Sql.uuid(rs, "crystal_uuid"));
                if (b != null) b.chests.add(new Chest(rs.getInt("chest_index"), Sql.intOrNull(rs, "size"), rs.getBytes("contents")));
            });
            Sql.query(c, "SELECT * FROM crystal_protections ORDER BY crystal_uuid, duration_ms", rs -> {
                Builder b = byId.get(Sql.uuid(rs, "crystal_uuid"));
                if (b != null) b.protections.add(new Protection(rs.getLong("duration_ms"),
                        rs.getBoolean("purchased"), Sql.longOrNull(rs, "broken_at")));
            });
            List<CrystalRow> rows = new ArrayList<>();
            for (Builder b : byId.values()) rows.add(b.build());
            return new CrystalsData(rows);
        }

        @Override
        public void replace(Connection c, CrystalsData data) throws SQLException {
            Sql.deleteAll(c, "crystal_protections", "crystal_chests", "crystal_claims", "crystals");
            Sql.insert(c, "crystals",
                    "uuid, faction, name, hp, max_hp, outpost, home_world, home_x, home_y, home_z, home_yaw, home_pitch, "
                            + "hp_bonus, regen_bonus, claim_capacity, spent_skill_points, immune_until", 17,
                    data.crystals(), (ps, r) -> {
                        Sql.setUuid(ps, 1, r.uuid());
                        ps.setString(2, r.faction());
                        ps.setString(3, r.name() == null ? "" : r.name());
                        ps.setDouble(4, r.hp());
                        ps.setDouble(5, r.maxHp());
                        ps.setBoolean(6, r.outpost());
                        Sql.setLocation(ps, 7, r.home());
                        Sql.setDouble(ps, 13, r.hpBonus());
                        Sql.setDouble(ps, 14, r.regenBonus());
                        Sql.setInt(ps, 15, r.claimCapacity());
                        Sql.setInt(ps, 16, r.spentSkillPoints());
                        Sql.setLong(ps, 17, r.immuneUntil());
                    });
            List<SimpleEntry<UUID, ChunkPos>> claims = new ArrayList<>();
            List<SimpleEntry<UUID, Chest>> chests = new ArrayList<>();
            List<SimpleEntry<UUID, Protection>> protections = new ArrayList<>();
            for (CrystalRow r : data.crystals()) {
                for (ChunkPos p : r.claims()) claims.add(new SimpleEntry<>(r.uuid(), p));
                for (Chest ch : r.chests()) chests.add(new SimpleEntry<>(r.uuid(), ch));
                for (Protection p : r.protections()) protections.add(new SimpleEntry<>(r.uuid(), p));
            }
            Sql.insert(c, "crystal_claims", "crystal_uuid, world, chunk_x, chunk_z", 4, claims, (ps, e) -> {
                Sql.setUuid(ps, 1, e.getKey());
                ps.setString(2, e.getValue().world());
                ps.setInt(3, e.getValue().x());
                ps.setInt(4, e.getValue().z());
            });
            Sql.insert(c, "crystal_chests", "crystal_uuid, chest_index, size, contents", 4, chests, (ps, e) -> {
                Sql.setUuid(ps, 1, e.getKey());
                ps.setInt(2, e.getValue().index());
                Sql.setInt(ps, 3, e.getValue().size());
                Sql.setBytes(ps, 4, e.getValue().contents());
            });
            Sql.insert(c, "crystal_protections", "crystal_uuid, duration_ms, purchased, broken_at", 4, protections, (ps, e) -> {
                Sql.setUuid(ps, 1, e.getKey());
                ps.setLong(2, e.getValue().durationMs());
                ps.setBoolean(3, e.getValue().purchased());
                Sql.setLong(ps, 4, e.getValue().brokenAt());
            });
        }

        private static final class Builder {
            CrystalRow row;
            final List<ChunkPos> claims = new ArrayList<>();
            final List<Chest> chests = new ArrayList<>();
            final List<Protection> protections = new ArrayList<>();

            CrystalRow build() {
                return new CrystalRow(row.uuid(), row.faction(), row.name(), row.hp(), row.maxHp(), row.outpost(),
                        row.home(), row.hpBonus(), row.regenBonus(), row.claimCapacity(), row.spentSkillPoints(),
                        row.immuneUntil(), List.copyOf(claims), List.copyOf(chests), List.copyOf(protections));
            }
        }
    }
}
