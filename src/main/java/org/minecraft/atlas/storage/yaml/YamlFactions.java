package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.atlas.storage.ItemBytes;
import org.minecraft.atlas.storage.model.CrystalsData;
import org.minecraft.atlas.storage.model.CrystalsData.Chest;
import org.minecraft.atlas.storage.model.CrystalsData.CrystalRow;
import org.minecraft.atlas.storage.model.CrystalsData.Protection;
import org.minecraft.atlas.storage.model.FactionsData;
import org.minecraft.atlas.storage.model.FactionsData.FactionRow;
import org.minecraft.atlas.storage.model.FactionsData.Member;
import org.minecraft.atlas.storage.model.LocationData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** FACTIONS and CRYSTALS in factions-data.yml. */
final class YamlFactions {

    static final String FILE = "factions-data.yml";

    private YamlFactions() {}

    static final class Factions implements YamlDomain<FactionsData> {

        @Override
        public String file() { return FILE; }

        @Override
        public FactionsData read(YamlConfiguration cfg) {
            List<FactionRow> rows = new ArrayList<>();
            ConfigurationSection root = cfg.getConfigurationSection("factions");
            if (root == null) return new FactionsData(rows);
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) continue;
                UUID owner = YamlUtil.uuid(s.getString("owner"));
                if (owner == null) continue;

                Map<UUID, Member> members = new LinkedHashMap<>();
                ConfigurationSection roles = s.getConfigurationSection("roles");
                for (String m : s.getStringList("members")) {
                    UUID u = YamlUtil.uuid(m);
                    if (u != null) members.put(u, new Member(u, true, roles == null ? null : roles.getString(m)));
                }
                if (roles != null) {
                    for (String key : roles.getKeys(false)) {
                        UUID u = YamlUtil.uuid(key);
                        if (u != null && !members.containsKey(u)) members.put(u, new Member(u, false, roles.getString(key)));
                    }
                }

                byte[] vault = ItemBytes.fromBase64(s.getString("vault_b64"));
                if (vault == null) vault = YamlUtil.legacyItems(s.getConfigurationSection("vault_contents"), 0);

                rows.add(new FactionRow(name, owner, s.getString("color", "white"), s.getString("description", ""),
                        s.getInt("level", 0), s.getInt("exp", 0), s.getInt("skill_points", 0),
                        s.getBoolean("outpost_unlocked", false),
                        s.contains("vault_size") ? s.getInt("vault_size") : null,
                        s.getInt("downgrade_mul", 1), s.getLong("downgrade_window_end", 0L),
                        List.copyOf(members.values()), s.getStringList("allies"),
                        s.getIntegerList("purchased_home_tiers"), s.getIntegerList("purchased_vault_tiers"),
                        vault));
            }
            return new FactionsData(rows);
        }

        @Override
        public void write(YamlConfiguration cfg, FactionsData data) {
            cfg.set("factions", null);
            ConfigurationSection root = cfg.createSection("factions");
            for (FactionRow f : data.factions()) {
                ConfigurationSection s = root.createSection(f.name());
                s.set("owner", f.owner().toString());
                List<String> members = new ArrayList<>();
                ConfigurationSection roles = null;
                for (Member m : f.members()) {
                    if (m.member()) members.add(m.uuid().toString());
                    if (m.role() != null) {
                        if (roles == null) roles = s.createSection("roles");
                        roles.set(m.uuid().toString(), m.role());
                    }
                }
                s.set("members", members);
                s.set("color", f.color());
                s.set("description", f.description());
                s.set("level", f.level());
                s.set("exp", f.exp());
                if (f.skillPoints() > 0) s.set("skill_points", f.skillPoints());
                if (!f.allies().isEmpty()) s.set("allies", new ArrayList<>(f.allies()));
                if (f.downgradeMul() != 1) s.set("downgrade_mul", f.downgradeMul());
                if (f.downgradeWindowEnd() != 0L) s.set("downgrade_window_end", f.downgradeWindowEnd());
                if (f.outpostUnlocked()) s.set("outpost_unlocked", true);
                if (!f.homeTiers().isEmpty()) s.set("purchased_home_tiers", new ArrayList<>(f.homeTiers()));
                if (!f.vaultTiers().isEmpty()) s.set("purchased_vault_tiers", new ArrayList<>(f.vaultTiers()));
                if (f.vaultSize() != null) s.set("vault_size", f.vaultSize());
                if (f.vaultContents() != null) s.set("vault_b64", ItemBytes.toBase64(f.vaultContents()));
            }
        }
    }

    static final class Crystals implements YamlDomain<CrystalsData> {

        @Override
        public String file() { return FILE; }

        @Override
        public CrystalsData read(YamlConfiguration cfg) {
            ConfigurationSection root = cfg.getConfigurationSection("crystals");
            boolean legacy = root == null;
            if (legacy) root = cfg.getConfigurationSection("crystal_data");

            // Very old files: homes in a separate "crystal_homes" section.
            Map<UUID, LocationData> legacyHomes = new LinkedHashMap<>();
            Map<UUID, String> legacyHomeFactions = new LinkedHashMap<>();
            ConfigurationSection homeSec = legacy ? cfg.getConfigurationSection("crystal_homes") : null;
            if (homeSec != null) {
                for (String key : homeSec.getKeys(false)) {
                    ConfigurationSection e = homeSec.getConfigurationSection(key);
                    UUID u = YamlUtil.uuid(key);
                    if (e == null || u == null) continue;
                    LocationData home = LocationData.decode(e.getString("home"), ',');
                    String faction = e.getString("faction");
                    if (home != null && faction != null) {
                        legacyHomes.put(u, home);
                        legacyHomeFactions.put(u, faction);
                    }
                }
            }

            List<CrystalRow> rows = new ArrayList<>();
            Set<UUID> seen = new LinkedHashSet<>();
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection cs = root.getConfigurationSection(key);
                    UUID id = YamlUtil.uuid(key);
                    if (cs == null || id == null) continue;
                    String faction = cs.getString("faction");
                    if (faction == null) continue;

                    LocationData home = LocationData.decode(cs.getString("home"), ',');
                    if (home == null) home = legacyHomes.get(id);

                    List<Integer> sizes = cs.getIntegerList("chest_sizes");
                    Map<Integer, Chest> chests = new TreeMap<>();
                    for (int i = 0; i < sizes.size(); i++) chests.put(i, new Chest(i, sizes.get(i), null));
                    ConfigurationSection b64 = cs.getConfigurationSection("chests_b64");
                    ConfigurationSection legacyChests = cs.getConfigurationSection("chests");
                    Set<String> indexes = new LinkedHashSet<>();
                    if (b64 != null) indexes.addAll(b64.getKeys(false));
                    if (legacyChests != null) indexes.addAll(legacyChests.getKeys(false));
                    for (String idxStr : indexes) {
                        int idx;
                        try {
                            idx = Integer.parseInt(idxStr);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        Integer size = idx < sizes.size() ? sizes.get(idx) : null;
                        byte[] contents = b64 == null ? null : ItemBytes.fromBase64(b64.getString(idxStr));
                        if (contents == null && legacyChests != null) {
                            contents = YamlUtil.legacyItems(legacyChests.getConfigurationSection(idxStr), size == null ? 27 : size);
                        }
                        chests.put(idx, new Chest(idx, size, contents));
                    }

                    Map<Long, Protection> prot = new TreeMap<>();
                    for (long d : cs.getLongList("purchased_protections")) prot.put(d, new Protection(d, true, null));
                    ConfigurationSection broken = cs.getConfigurationSection("broken_protections");
                    if (broken != null) {
                        for (String k : broken.getKeys(false)) {
                            try {
                                long d = Long.parseLong(k);
                                long at = broken.getLong(k, 0L);
                                if (at <= 0) continue;
                                Protection p = prot.get(d);
                                prot.put(d, new Protection(d, p != null && p.purchased(), at));
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    rows.add(new CrystalRow(id, faction, cs.getString("name", ""),
                            cs.getDouble("hp", 50.0), cs.getDouble("max_hp", 50.0), cs.getBoolean("outpost", false), home,
                            cs.contains("hp_bonus") ? cs.getDouble("hp_bonus") : null,
                            cs.contains("regen_bonus") ? cs.getDouble("regen_bonus") : null,
                            cs.contains("claim_capacity") ? cs.getInt("claim_capacity") : null,
                            cs.contains("spent_skill_points") ? cs.getInt("spent_skill_points") : null,
                            cs.contains("immune_until") ? cs.getLong("immune_until") : null,
                            YamlUtil.readChunks(cs, "claimed_chunks"),
                            List.copyOf(chests.values()), List.copyOf(prot.values())));
                    seen.add(id);
                }
            }
            // Legacy backfill: a home without crystal data still gets a stub.
            for (Map.Entry<UUID, LocationData> e : legacyHomes.entrySet()) {
                if (seen.contains(e.getKey())) continue;
                rows.add(new CrystalRow(e.getKey(), legacyHomeFactions.get(e.getKey()), "", 50.0, 50.0, false,
                        e.getValue(), null, null, null, null, null, List.of(), List.of(), List.of()));
            }
            return new CrystalsData(rows);
        }

        @Override
        public void write(YamlConfiguration cfg, CrystalsData data) {
            cfg.set("crystal_homes", null);
            cfg.set("crystal_data", null);
            cfg.set("claims", null);
            cfg.set("crystals", null);
            if (data.crystals().isEmpty()) return;
            ConfigurationSection root = cfg.createSection("crystals");
            for (CrystalRow r : data.crystals()) {
                ConfigurationSection cs = root.createSection(r.uuid().toString());
                cs.set("faction", r.faction());
                if (r.name() != null && !r.name().isEmpty()) cs.set("name", r.name());
                cs.set("hp", r.hp());
                cs.set("max_hp", r.maxHp());
                if (r.outpost()) cs.set("outpost", true);
                if (r.home() != null) cs.set("home", r.home().encode(','));
                if (r.hpBonus() != null) cs.set("hp_bonus", r.hpBonus());
                if (r.regenBonus() != null) cs.set("regen_bonus", r.regenBonus());
                if (r.claimCapacity() != null) cs.set("claim_capacity", r.claimCapacity());
                if (r.spentSkillPoints() != null) cs.set("spent_skill_points", r.spentSkillPoints());
                if (r.immuneUntil() != null) cs.set("immune_until", r.immuneUntil());
                YamlUtil.writeChunks(cs, "claimed_chunks", r.claims());

                List<Integer> sizes = new ArrayList<>();
                ConfigurationSection chests = null;
                for (Chest ch : r.chests()) {
                    if (ch.size() != null) sizes.add(ch.size());
                    if (ch.contents() != null) {
                        if (chests == null) chests = cs.createSection("chests_b64");
                        chests.set(String.valueOf(ch.index()), ItemBytes.toBase64(ch.contents()));
                    }
                }
                if (!sizes.isEmpty()) cs.set("chest_sizes", sizes);

                List<Long> purchased = new ArrayList<>();
                ConfigurationSection broken = null;
                for (Protection p : r.protections()) {
                    if (p.purchased()) purchased.add(p.durationMs());
                    if (p.brokenAt() != null) {
                        if (broken == null) broken = cs.createSection("broken_protections");
                        broken.set(String.valueOf(p.durationMs()), p.brokenAt());
                    }
                }
                if (!purchased.isEmpty()) cs.set("purchased_protections", purchased);
            }
        }
    }
}
