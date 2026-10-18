package com.ashenwarden.persistence;

import com.ashenwarden.common.PersistenceException;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.player.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** SQL for the player_state table (one row per save slot). */
public final class PlayerRepository {

    public void upsert(Connection c, int saveId, GameState state) throws SQLException {
        Player p = state.getPlayer();
        String sql = "INSERT INTO player_state (save_id, level, xp, hp, max_hp, stamina, max_stamina,"
                + " ash_energy, max_ash_energy, base_attack, base_defense, fragments, lost_fragments,"
                + " lost_fragment_location, rng_seed)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), hp = VALUES(hp),"
                + " max_hp = VALUES(max_hp), stamina = VALUES(stamina), max_stamina = VALUES(max_stamina),"
                + " ash_energy = VALUES(ash_energy), max_ash_energy = VALUES(max_ash_energy),"
                + " base_attack = VALUES(base_attack), base_defense = VALUES(base_defense),"
                + " fragments = VALUES(fragments), lost_fragments = VALUES(lost_fragments),"
                + " lost_fragment_location = VALUES(lost_fragment_location), rng_seed = VALUES(rng_seed)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, saveId);
            ps.setInt(i++, p.getLevel());
            ps.setInt(i++, p.getXp());
            ps.setInt(i++, p.getHp());
            ps.setInt(i++, p.getMaxHp());
            ps.setInt(i++, p.getStamina());
            ps.setInt(i++, p.getMaxStamina());
            ps.setInt(i++, p.getAsh());
            ps.setInt(i++, p.getMaxAsh());
            ps.setInt(i++, p.getBaseAttack());
            ps.setInt(i++, p.getBaseDefense());
            ps.setInt(i++, p.getFragments());
            ps.setInt(i++, p.getLostFragments());
            ps.setString(i++, p.getLostFragmentLocation());
            ps.setLong(i, state.getRngSeed());
            ps.executeUpdate();
        }
    }

    /** Reads the stored RNG seed first so GameState can be rebuilt with it. */
    public long loadSeed(Connection c, int saveId) throws SQLException {
        String sql = "SELECT rng_seed FROM player_state WHERE save_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new PersistenceException("Save slot " + saveId + " has no player_state row.");
                }
                return rs.getLong(1);
            }
        }
    }

    public void load(Connection c, int saveId, GameState state) throws SQLException {
        String sql = "SELECT level, xp, hp, max_hp, stamina, max_stamina, ash_energy, max_ash_energy,"
                + " base_attack, base_defense, fragments, lost_fragments, lost_fragment_location"
                + " FROM player_state WHERE save_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new PersistenceException("Save slot " + saveId + " has no player_state row.");
                }
                Player p = state.getPlayer();
                p.setLevel(rs.getInt("level"));
                p.setXp(rs.getInt("xp"));
                p.setMaxHp(rs.getInt("max_hp"));
                p.setMaxStamina(rs.getInt("max_stamina"));
                p.setMaxAsh(rs.getInt("max_ash_energy"));
                p.setBaseAttack(rs.getInt("base_attack"));
                p.setBaseDefense(rs.getInt("base_defense"));
                p.setHp(rs.getInt("hp"));
                p.setStamina(rs.getInt("stamina"));
                p.setAsh(rs.getInt("ash_energy"));
                p.setFragments(rs.getInt("fragments"));
                p.setLostFragments(rs.getInt("lost_fragments"), rs.getString("lost_fragment_location"));
            }
        }
    }
}
