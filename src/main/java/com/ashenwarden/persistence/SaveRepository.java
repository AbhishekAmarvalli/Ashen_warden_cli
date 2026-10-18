package com.ashenwarden.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the save_slots table. All statements are parameterized. */
public final class SaveRepository {

    public int insertSlot(Connection c, String slotName, String playerName, String chapter,
                          String locationCode, String sanctuaryCode, String gameVersion) throws SQLException {
        String sql = "INSERT INTO save_slots (slot_name, player_name, chapter, location_code, sanctuary_code, game_version)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, slotName);
            ps.setString(2, playerName);
            ps.setString(3, chapter);
            ps.setString(4, locationCode);
            ps.setString(5, sanctuaryCode);
            ps.setString(6, gameVersion);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Save slot insert produced no id");
                }
                return keys.getInt(1);
            }
        }
    }

    public void updateSlot(Connection c, int saveId, String playerName, String chapter,
                           String locationCode, String sanctuaryCode, String gameVersion) throws SQLException {
        String sql = "UPDATE save_slots SET player_name = ?, chapter = ?, location_code = ?,"
                + " sanctuary_code = ?, game_version = ? WHERE save_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, chapter);
            ps.setString(3, locationCode);
            ps.setString(4, sanctuaryCode);
            ps.setString(5, gameVersion);
            ps.setInt(6, saveId);
            ps.executeUpdate();
        }
    }

    public boolean slotNameTaken(Connection c, int ownSaveId, String slotName) throws SQLException {
        String sql = "SELECT save_id FROM save_slots WHERE slot_name = ? AND save_id <> ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slotName);
            ps.setInt(2, ownSaveId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<SaveSlotInfo> listSlots(Connection c) throws SQLException {
        String sql = "SELECT save_id, slot_name, player_name, chapter, location_code, game_version,"
                + " updated_at FROM save_slots ORDER BY updated_at DESC";
        List<SaveSlotInfo> slots = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                slots.add(new SaveSlotInfo(
                        rs.getInt("save_id"),
                        rs.getString("slot_name"),
                        rs.getString("player_name"),
                        rs.getString("chapter"),
                        rs.getString("location_code"),
                        rs.getString("game_version"),
                        String.valueOf(rs.getTimestamp("updated_at"))
                ));
            }
        }
        return slots;
    }

    public SaveSlotInfo findSlot(Connection c, int saveId) throws SQLException {
        String sql = "SELECT save_id, slot_name, player_name, chapter, location_code, game_version,"
                + " updated_at FROM save_slots WHERE save_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SaveSlotInfo(
                        rs.getInt("save_id"),
                        rs.getString("slot_name"),
                        rs.getString("player_name"),
                        rs.getString("chapter"),
                        rs.getString("location_code"),
                        rs.getString("game_version"),
                        String.valueOf(rs.getTimestamp("updated_at"))
                );
            }
        }
    }

    /** Deletes one slot; child rows cascade via foreign keys. */
    public void deleteSlot(Connection c, int saveId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM save_slots WHERE save_id = ?")) {
            ps.setInt(1, saveId);
            ps.executeUpdate();
        }
    }
}
