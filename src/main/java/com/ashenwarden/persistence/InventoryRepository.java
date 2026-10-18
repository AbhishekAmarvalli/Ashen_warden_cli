package com.ashenwarden.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL for inventory_entries. Saves use delete-then-batch-insert inside the
 * caller's transaction, so the snapshot is always coherent.
 */
public final class InventoryRepository {

    public void replaceAll(Connection c, int saveId, Map<String, Integer> items) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM inventory_entries WHERE save_id = ?")) {
            delete.setInt(1, saveId);
            delete.executeUpdate();
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO inventory_entries (save_id, item_code, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                ps.setInt(1, saveId);
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Map<String, Integer> load(Connection c, int saveId) throws SQLException {
        String sql = "SELECT item_code, quantity FROM inventory_entries WHERE save_id = ? ORDER BY entry_id";
        Map<String, Integer> items = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_code"), rs.getInt("quantity"));
                }
            }
        }
        return items;
    }
}
