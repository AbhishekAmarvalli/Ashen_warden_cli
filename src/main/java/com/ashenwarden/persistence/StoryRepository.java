package com.ashenwarden.persistence;

import com.ashenwarden.quest.QuestProgress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL for slot-scoped progress tables: discovered_locations, unlocked_paths,
 * quest_progress, story_flags, acquired_memories, defeated_bosses.
 * Collection values are stored in documented delimited text (codes are
 * validated against content before they ever reach these methods).
 */
public final class StoryRepository {

    // --- discovered locations ----------------------------------------------
    public void replaceLocations(Connection c, int saveId, Set<String> codes) throws SQLException {
        replaceStringSet(c, "discovered_locations", "location_code", saveId, codes);
    }

    public Set<String> loadLocations(Connection c, int saveId) throws SQLException {
        return loadStringSet(c, "discovered_locations", "location_code", saveId);
    }

    // --- unlocked paths / abilities ----------------------------------------
    public void replacePaths(Connection c, int saveId, Set<String> codes) throws SQLException {
        replaceStringSet(c, "unlocked_paths", "path_code", saveId, codes);
    }

    public Set<String> loadPaths(Connection c, int saveId) throws SQLException {
        return loadStringSet(c, "unlocked_paths", "path_code", saveId);
    }

    // --- memories -----------------------------------------------------------
    public void replaceMemories(Connection c, int saveId, Set<String> codes) throws SQLException {
        replaceStringSet(c, "acquired_memories", "memory_code", saveId, codes);
    }

    public Set<String> loadMemories(Connection c, int saveId) throws SQLException {
        return loadStringSet(c, "acquired_memories", "memory_code", saveId);
    }

    // --- defeated bosses ----------------------------------------------------
    public void replaceBosses(Connection c, int saveId, Set<String> codes) throws SQLException {
        replaceStringSet(c, "defeated_bosses", "boss_code", saveId, codes);
    }

    public Set<String> loadBosses(Connection c, int saveId) throws SQLException {
        return loadStringSet(c, "defeated_bosses", "boss_code", saveId);
    }

    private void replaceStringSet(Connection c, String table, String column, int saveId,
                                  Set<String> codes) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM " + table + " WHERE save_id = ?")) {
            delete.setInt(1, saveId);
            delete.executeUpdate();
        }
        if (codes == null || codes.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + " (save_id, " + column + ") VALUES (?, ?)")) {
            for (String code : codes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                ps.setInt(1, saveId);
                ps.setString(2, code);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Set<String> loadStringSet(Connection c, String table, String column, int saveId) throws SQLException {
        Set<String> codes = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM " + table + " WHERE save_id = ?")) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
            }
        }
        return codes;
    }

    // --- story flags --------------------------------------------------------
    public void replaceFlags(Connection c, int saveId, Map<String, String> flags) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM story_flags WHERE save_id = ?")) {
            delete.setInt(1, saveId);
            delete.executeUpdate();
        }
        if (flags == null || flags.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO story_flags (save_id, flag_code, flag_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : flags.entrySet()) {
                ps.setInt(1, saveId);
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue() == null ? "" : entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Map<String, String> loadFlags(Connection c, int saveId) throws SQLException {
        Map<String, String> flags = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT flag_code, flag_value FROM story_flags WHERE save_id = ?")) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    flags.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return flags;
    }

    // --- quests -------------------------------------------------------------
    public void replaceQuests(Connection c, int saveId, Map<String, QuestProgress> quests) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM quest_progress WHERE save_id = ?")) {
            delete.setInt(1, saveId);
            delete.executeUpdate();
        }
        if (quests == null || quests.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO quest_progress (save_id, quest_code, status, objectives_done, counters, reward_claimed)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (QuestProgress quest : quests.values()) {
                ps.setInt(1, saveId);
                ps.setString(2, quest.getQuestCode());
                ps.setString(3, quest.getStatus().name());
                ps.setString(4, String.join(",", quest.getObjectivesDone()));
                ps.setString(5, encodeCounters(quest.getCounters()));
                ps.setInt(6, quest.isRewardClaimed() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Map<String, QuestProgress> loadQuests(Connection c, int saveId) throws SQLException {
        Map<String, QuestProgress> quests = new java.util.LinkedHashMap<>();
        String sql = "SELECT quest_code, status, objectives_done, counters, reward_claimed"
                + " FROM quest_progress WHERE save_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("quest_code");
                    QuestProgress progress = new QuestProgress(code);
                    progress.setStatus(QuestProgress.Status.valueOf(rs.getString("status")));
                    String done = rs.getString("objectives_done");
                    if (done != null && !done.isBlank()) {
                        for (String objective : done.split(",")) {
                            if (!objective.isBlank()) {
                                progress.markObjective(objective.trim());
                            }
                        }
                    }
                    decodeCounters(rs.getString("counters"), progress.getCounters());
                    progress.setRewardClaimed(rs.getInt("reward_claimed") == 1);
                    quests.put(code, progress);
                }
            }
        }
        return quests;
    }

    /** Documented format: key=value;key=value (codes are pre-validated). */
    static String encodeCounters(Map<String, Integer> counters) {
        if (counters == null || counters.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    static void decodeCounters(String raw, Map<String, Integer> target) {
        target.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int value = Integer.parseInt(pair.substring(eq + 1).trim());
                target.put(pair.substring(0, eq).trim(), Math.max(0, value));
            } catch (NumberFormatException ignored) {
                // corrupted pair is skipped rather than aborting the whole load
            }
        }
    }

    public List<String> integrityErrors(Connection c, int saveId) throws SQLException {
        // lightweight referential check: child rows without a parent slot
        List<String> errors = new ArrayList<>();
        for (String table : new String[]{"inventory_entries", "equipped_items", "story_flags",
                "quest_progress", "acquired_memories", "defeated_bosses",
                "discovered_locations", "unlocked_paths", "player_state"}) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE save_id = ? AND save_id NOT IN (SELECT save_id FROM save_slots)")) {
                ps.setInt(1, saveId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        errors.add(table + " has orphan rows");
                    }
                }
            }
        }
        return errors;
    }
}
