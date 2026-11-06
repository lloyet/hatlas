package org.minecraft.atlas.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraft.atlas.storage.model.JobsData;
import org.minecraft.atlas.storage.model.JobsData.Jokeyrini;
import org.minecraft.atlas.storage.model.JobsData.PlayerJob;
import org.minecraft.atlas.storage.model.JobsData.Quest;
import org.minecraft.atlas.storage.model.JobsData.Reward;
import org.minecraft.atlas.storage.model.JobsData.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JOBS in jobs-data.yml: {@code jobs}, {@code job_cooldowns}, {@code jokeyrini_player_data}. */
final class YamlJobs implements YamlDomain<JobsData> {

    @Override
    public String file() { return "jobs-data.yml"; }

    @Override
    public JobsData read(YamlConfiguration cfg) {
        Map<UUID, Long> cooldowns = new HashMap<>();
        ConfigurationSection cd = cfg.getConfigurationSection("job_cooldowns");
        if (cd != null) {
            for (String k : cd.getKeys(false)) {
                UUID u = YamlUtil.uuid(k);
                if (u != null) cooldowns.put(u, cd.getLong(k));
            }
        }

        List<PlayerJob> players = new ArrayList<>();
        ConfigurationSection jobs = cfg.getConfigurationSection("jobs");
        if (jobs != null) {
            for (String k : jobs.getKeys(false)) {
                ConfigurationSection s = jobs.getConfigurationSection(k);
                UUID u = YamlUtil.uuid(k);
                if (s == null || u == null || s.getString("job") == null) continue;
                List<Quest> active = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("active_quests")) {
                    Quest q = readQuest(m);
                    if (q != null) active.add(q);
                }
                List<Quest> offered = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("daily_offered_quests")) {
                    Object id = m.get("quest_id");
                    if (id != null) offered.add(new Quest(id.toString(), 0L, readTasks(m.get("tasks")), Map.of()));
                }
                players.add(new PlayerJob(u, s.getString("job"), s.getInt("level", 1), s.getInt("progress", 0),
                        s.getLong("last_daily_reset_ms", 0L), active, offered, s.getStringList("daily_selected")));
            }
        }

        List<Jokeyrini> jokeyrini = new ArrayList<>();
        ConfigurationSection jok = cfg.getConfigurationSection("jokeyrini_player_data");
        if (jok != null) {
            for (String k : jok.getKeys(false)) {
                ConfigurationSection s = jok.getConfigurationSection(k);
                UUID u = YamlUtil.uuid(k);
                if (s == null || u == null) continue;
                Quest active = null;
                ConfigurationSection aq = s.getConfigurationSection("active_quest");
                if (aq != null) active = readQuest(toMap(aq));
                else if (s.get("active_quest") instanceof Map<?, ?> m) active = readQuest(m);
                jokeyrini.add(new Jokeyrini(u, s.getLong("last_daily_reset_ms", 0L),
                        readTasks(s.getList("daily_offer")), active));
            }
        }
        return new JobsData(players, cooldowns, jokeyrini);
    }

    @Override
    public void write(YamlConfiguration cfg, JobsData data) {
        cfg.set("job_cooldowns", null);
        ConfigurationSection cd = cfg.createSection("job_cooldowns");
        data.cooldowns().forEach((u, until) -> cd.set(u.toString(), until));

        cfg.set("jobs", null);
        ConfigurationSection jobs = cfg.createSection("jobs");
        for (PlayerJob p : data.players()) {
            ConfigurationSection s = jobs.createSection(p.uuid().toString());
            s.set("job", p.job());
            s.set("level", p.level());
            s.set("progress", p.progress());
            List<Map<String, Object>> active = new ArrayList<>();
            for (Quest q : p.active()) active.add(writeQuest(q));
            s.set("active_quests", active);
            s.set("last_daily_reset_ms", p.lastDailyReset());
            List<Map<String, Object>> offered = new ArrayList<>();
            for (Quest q : p.offered()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("quest_id", q.questId());
                m.put("tasks", writeTasks(q.tasks()));
                offered.add(m);
            }
            s.set("daily_offered_quests", offered);
            s.set("daily_selected", new ArrayList<>(p.selected()));
        }

        cfg.set("jokeyrini_player_data", null);
        ConfigurationSection jok = cfg.createSection("jokeyrini_player_data");
        for (Jokeyrini j : data.jokeyrini()) {
            ConfigurationSection s = jok.createSection(j.uuid().toString());
            if (j.lastDailyReset() > 0) s.set("last_daily_reset_ms", j.lastDailyReset());
            if (j.offer() != null && !j.offer().isEmpty()) s.set("daily_offer", writeTasks(j.offer()));
            if (j.active() != null) s.set("active_quest", writeQuest(j.active()));
        }
    }

    // ── Quest / task maps (same keys as GeneratedTask.serialize) ─────────────

    /** A map saved as a value is reloaded as a section; turn it back into nested maps. */
    private static Map<String, Object> toMap(ConfigurationSection sec) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Object v = sec.get(key);
            out.put(key, v instanceof ConfigurationSection child ? toMap(child) : v);
        }
        return out;
    }

    private static Quest readQuest(Map<?, ?> m) {
        Object id = m.get("quest_id");
        Object exp = m.get("expires_at");
        if (id == null || !(exp instanceof Number n)) return null;
        Map<String, Integer> progress = new HashMap<>();
        if (m.get("task_progress") instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Number v) progress.put(e.getKey().toString(), v.intValue());
            }
        }
        return new Quest(id.toString(), n.longValue(), readTasks(m.get("tasks")), progress);
    }

    private static Map<String, Object> writeQuest(Quest q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quest_id", q.questId());
        m.put("tasks", writeTasks(q.tasks()));
        m.put("expires_at", q.expiresAt());
        m.put("task_progress", new HashMap<>(q.progress()));
        return m;
    }

    static List<Task> readTasks(Object raw) {
        List<Task> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            try {
                List<String> targets = new ArrayList<>();
                if (m.get("targets") instanceof List<?> tl) for (Object t : tl) if (t != null) targets.add(t.toString());
                List<Reward> rewards = new ArrayList<>();
                if (m.get("rewards") instanceof List<?> rl) {
                    for (Object r : rl) {
                        if (r instanceof Map<?, ?> rm && rm.get("material") != null) {
                            int amount = rm.get("amount") instanceof Number n ? n.intValue() : 1;
                            rewards.add(new Reward(rm.get("material").toString(), amount));
                        }
                    }
                }
                out.add(new Task(m.get("task_id").toString(), m.get("name").toString(),
                        m.get("description") != null ? m.get("description").toString() : "",
                        m.get("action_type").toString(), targets,
                        ((Number) m.get("difficulty")).intValue(), ((Number) m.get("amount")).intValue(),
                        ((Number) m.get("time_limit_ms")).longValue(),
                        m.get("exp_multiplier") instanceof Number n ? n.doubleValue() : 1.0, rewards));
            } catch (RuntimeException ignored) {
                // malformed task entry: skipped, like the legacy loader did
            }
        }
        return out;
    }

    private static List<Map<String, Object>> writeTasks(List<Task> tasks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("task_id", t.taskId());
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("action_type", t.actionType());
            m.put("targets", new ArrayList<>(t.targets()));
            m.put("difficulty", t.difficulty());
            m.put("amount", t.amount());
            m.put("time_limit_ms", t.timeLimitMs());
            m.put("exp_multiplier", t.expMultiplier());
            List<Map<String, Object>> rewards = new ArrayList<>();
            for (Reward r : t.rewards()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("material", r.material());
                rm.put("amount", r.amount());
                rewards.add(rm);
            }
            m.put("rewards", rewards);
            out.add(m);
        }
        return out;
    }
}
