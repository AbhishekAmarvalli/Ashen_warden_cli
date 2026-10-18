package com.ashenwarden.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** SQL for equipped_items (slot -> item_code, one row per slot). */
public final class EquipmentRepository {

    public void replaceAll(Connection c, int saveId, Map<String, String> equipped) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM equipped_items WHERE save_id = ?")) {
            delete.setInt(1, saveId);
            delete.executeUpdate();
        }
        if (equipped == null || equipped.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO equipped_items (save_id, slot_name, item_code) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : equipped.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                ps.setInt(1, saveId);
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Map<String, String> load(Connection c, int saveId) throws SQLException {
        String sql = "SELECT slot_name, item_code FROM equipped_items WHERE save_id = ?";
        Map<String, String> equipped = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    equipped.put(rs.getString("slot_name"), rs.getString("item_code"));
                }
            }
        }
        return equipped;
    }
}
