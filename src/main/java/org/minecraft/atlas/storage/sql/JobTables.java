package org.minecraft.atlas.storage.sql;

import org.minecraft.atlas.storage.model.JobsData;
import org.minecraft.atlas.storage.model.JobsData.Jokeyrini;
import org.minecraft.atlas.storage.model.JobsData.PlayerJob;
import org.minecraft.atlas.storage.model.JobsData.Quest;
import org.minecraft.atlas.storage.model.JobsData.Reward;
import org.minecraft.atlas.storage.model.JobsData.Task;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tables of the JOBS domain: jobs, cooldowns, daily quests and Jokeyrini quests. */
final class JobTables implements SqlDomain<JobsData> {

    private static final String JOB_ACTIVE = "JOB_ACTIVE";
    private static final String JOB_OFFER = "JOB_OFFER";
    private static final String JOK_ACTIVE = "JOKEYRINI_ACTIVE";
    private static final String JOK_OFFER = "JOKEYRINI_OFFER";

    /** Primary key of a quest row. */
    private record QuestKey(UUID player, String source, int slot) {}

    /** A quest row being rebuilt from the tables. */
    private static final class QuestBuilder {
        String questId;
        long expiresAt;
        final List<Task> tasks = new ArrayList<>();
        final Map<String, Integer> progress = new HashMap<>();

        Quest build() {
            return new Quest(questId, expiresAt, List.copyOf(tasks), Map.copyOf(progress));
        }
    }

    @Override
    public JobsData load(Connection c) throws SQLException {
        Map<QuestKey, QuestBuilder> quests = new LinkedHashMap<>();
        Sql.query(c, "SELECT * FROM player_quests ORDER BY player_uuid, source, slot", rs -> {
            UUID player = Sql.uuid(rs, "player_uuid");
            if (player == null) return;
            QuestBuilder q = new QuestBuilder();
            q.questId = rs.getString("quest_id");
            q.expiresAt = rs.getLong("expires_at");
            quests.put(new QuestKey(player, rs.getString("source"), rs.getInt("slot")), q);
        });
        Sql.query(c, "SELECT * FROM player_quest_tasks ORDER BY player_uuid, source, slot, position", rs -> {
            QuestBuilder q = quests.get(new QuestKey(Sql.uuid(rs, "player_uuid"), rs.getString("source"), rs.getInt("slot")));
            if (q == null) return;
            List<Reward> rewards = new ArrayList<>();
            for (String r : Sql.splitList(rs.getString("rewards"))) {
                int colon = r.lastIndexOf(':');
                if (colon <= 0) continue;
                try {
                    rewards.add(new Reward(r.substring(0, colon), Integer.parseInt(r.substring(colon + 1))));
                } catch (NumberFormatException ignored) {}
            }
            Task t = new Task(rs.getString("task_id"), rs.getString("name"), rs.getString("description"),
                    rs.getString("action_type"), Sql.splitList(rs.getString("targets")),
                    rs.getInt("difficulty"), rs.getInt("amount"), rs.getLong("time_limit_ms"),
                    rs.getDouble("exp_multiplier"), List.copyOf(rewards));
            q.tasks.add(t);
            int progress = rs.getInt("progress");
            if (progress > 0) q.progress.put(t.taskId(), progress);
        });

        // Group quests per player and source.
        Map<UUID, Map<String, List<Quest>>> byPlayer = new HashMap<>();
        for (Map.Entry<QuestKey, QuestBuilder> e : quests.entrySet()) {
            byPlayer.computeIfAbsent(e.getKey().player(), k -> new HashMap<>())
                    .computeIfAbsent(e.getKey().source(), k -> new ArrayList<>())
                    .add(e.getValue().build());
        }

        Map<UUID, List<String>> selected = new HashMap<>();
        Sql.query(c, "SELECT * FROM job_selected_quests ORDER BY player_uuid, position", rs -> {
            UUID p = Sql.uuid(rs, "player_uuid");
            if (p != null) selected.computeIfAbsent(p, k -> new ArrayList<>()).add(rs.getString("quest_id"));
        });

        List<PlayerJob> players = new ArrayList<>();
        Sql.query(c, "SELECT * FROM player_jobs ORDER BY player_uuid", rs -> {
            UUID p = Sql.uuid(rs, "player_uuid");
            if (p == null) return;
            Map<String, List<Quest>> q = byPlayer.getOrDefault(p, Map.of());
            players.add(new PlayerJob(p, rs.getString("job"), rs.getInt("level"), rs.getInt("progress"),
                    rs.getLong("last_daily_reset"),
                    List.copyOf(q.getOrDefault(JOB_ACTIVE, List.of())),
                    List.copyOf(q.getOrDefault(JOB_OFFER, List.of())),
                    List.copyOf(selected.getOrDefault(p, List.of()))));
        });

        Map<UUID, Long> cooldowns = new HashMap<>();
        Sql.query(c, "SELECT * FROM job_cooldowns", rs -> {
            UUID p = Sql.uuid(rs, "player_uuid");
            if (p != null) cooldowns.put(p, rs.getLong("until_ms"));
        });

        // Jokeyrini: a player may have a reset time, an offer and/or an active quest.
        Map<UUID, Long> jokResets = new HashMap<>();
        Sql.query(c, "SELECT * FROM jokeyrini_players", rs -> {
            UUID p = Sql.uuid(rs, "player_uuid");
            if (p != null) jokResets.put(p, rs.getLong("last_daily_reset"));
        });
        Set<UUID> jokPlayers = new LinkedHashSet<>(jokResets.keySet());
        for (Map.Entry<UUID, Map<String, List<Quest>>> e : byPlayer.entrySet()) {
            if (e.getValue().containsKey(JOK_ACTIVE) || e.getValue().containsKey(JOK_OFFER)) jokPlayers.add(e.getKey());
        }
        List<Jokeyrini> jokeyrini = new ArrayList<>();
        for (UUID p : jokPlayers) {
            Map<String, List<Quest>> q = byPlayer.getOrDefault(p, Map.of());
            List<Quest> offer = q.getOrDefault(JOK_OFFER, List.of());
            List<Quest> active = q.getOrDefault(JOK_ACTIVE, List.of());
            jokeyrini.add(new Jokeyrini(p, jokResets.getOrDefault(p, 0L),
                    offer.isEmpty() ? List.of() : offer.getFirst().tasks(),
                    active.isEmpty() ? null : active.getFirst()));
        }
        return new JobsData(List.copyOf(players), Map.copyOf(cooldowns), List.copyOf(jokeyrini));
    }

    @Override
    public void replace(Connection c, JobsData data) throws SQLException {
        Sql.deleteAll(c, "player_quest_tasks", "player_quests", "job_selected_quests",
                "jokeyrini_players", "job_cooldowns", "player_jobs");

        Sql.insert(c, "player_jobs", "player_uuid, job, level, progress, last_daily_reset", 5,
                data.players(), (ps, p) -> {
                    Sql.setUuid(ps, 1, p.uuid());
                    ps.setString(2, p.job());
                    ps.setInt(3, p.level());
                    ps.setInt(4, p.progress());
                    ps.setLong(5, p.lastDailyReset());
                });
        Sql.insert(c, "job_cooldowns", "player_uuid, until_ms", 2, data.cooldowns().entrySet(), (ps, e) -> {
            Sql.setUuid(ps, 1, e.getKey());
            ps.setLong(2, e.getValue());
        });

        List<Object[]> selected = new ArrayList<>();
        List<Object[]> questRows = new ArrayList<>();   // player, source, slot, quest
        for (PlayerJob p : data.players()) {
            for (int i = 0; i < p.selected().size(); i++) selected.add(new Object[]{p.uuid(), i, p.selected().get(i)});
            for (int i = 0; i < p.active().size(); i++) questRows.add(new Object[]{p.uuid(), JOB_ACTIVE, i, p.active().get(i)});
            for (int i = 0; i < p.offered().size(); i++) questRows.add(new Object[]{p.uuid(), JOB_OFFER, i, p.offered().get(i)});
        }
        for (Jokeyrini j : data.jokeyrini()) {
            if (j.offer() != null && !j.offer().isEmpty()) {
                questRows.add(new Object[]{j.uuid(), JOK_OFFER, 0, new Quest("jokeyrini_offer", 0L, j.offer(), Map.of())});
            }
            if (j.active() != null) questRows.add(new Object[]{j.uuid(), JOK_ACTIVE, 0, j.active()});
        }
        Sql.insert(c, "job_selected_quests", "player_uuid, position, quest_id", 3, selected, (ps, r) -> {
            Sql.setUuid(ps, 1, (UUID) r[0]);
            ps.setInt(2, (Integer) r[1]);
            ps.setString(3, (String) r[2]);
        });
        Sql.insert(c, "jokeyrini_players", "player_uuid, last_daily_reset", 2, data.jokeyrini(), (ps, j) -> {
            Sql.setUuid(ps, 1, j.uuid());
            ps.setLong(2, j.lastDailyReset());
        });
        Sql.insert(c, "player_quests", "player_uuid, source, slot, quest_id, expires_at", 5, questRows, (ps, r) -> {
            Quest q = (Quest) r[3];
            Sql.setUuid(ps, 1, (UUID) r[0]);
            ps.setString(2, (String) r[1]);
            ps.setInt(3, (Integer) r[2]);
            ps.setString(4, q.questId());
            ps.setLong(5, q.expiresAt());
        });

        List<Object[]> taskRows = new ArrayList<>();   // player, source, slot, position, task, progress
        for (Object[] r : questRows) {
            Quest q = (Quest) r[3];
            for (int i = 0; i < q.tasks().size(); i++) {
                Task t = q.tasks().get(i);
                taskRows.add(new Object[]{r[0], r[1], r[2], i, t, q.progress().getOrDefault(t.taskId(), 0)});
            }
        }
        Sql.insert(c, "player_quest_tasks",
                "player_uuid, source, slot, position, task_id, name, description, action_type, targets, "
                        + "difficulty, amount, time_limit_ms, exp_multiplier, rewards, progress", 15,
                taskRows, (ps, r) -> {
                    Task t = (Task) r[4];
                    Sql.setUuid(ps, 1, (UUID) r[0]);
                    ps.setString(2, (String) r[1]);
                    ps.setInt(3, (Integer) r[2]);
                    ps.setInt(4, (Integer) r[3]);
                    ps.setString(5, t.taskId());
                    ps.setString(6, t.name());
                    ps.setString(7, t.description() == null ? "" : t.description());
                    ps.setString(8, t.actionType());
                    ps.setString(9, Sql.joinList(t.targets()));
                    ps.setInt(10, t.difficulty());
                    ps.setInt(11, t.amount());
                    ps.setLong(12, t.timeLimitMs());
                    ps.setDouble(13, t.expMultiplier());
                    List<String> rewards = new ArrayList<>();
                    for (Reward rw : t.rewards()) rewards.add(rw.material() + ":" + rw.amount());
                    ps.setString(14, Sql.joinList(rewards));
                    ps.setInt(15, (Integer) r[5]);
                });
    }
}
