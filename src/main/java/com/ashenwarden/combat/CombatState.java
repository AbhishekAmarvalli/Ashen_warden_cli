package com.ashenwarden.combat;

import com.ashenwarden.enemy.Enemy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime state of a single encounter. The combat log is an ordered list kept
 * for the 'log' command (bounded so it cannot grow without control).
 */
public final class CombatState {
    public enum Outcome { ONGOING, VICTORY, DEFEAT, ESCAPED }

    public static final int LOG_LIMIT = 60;

    private final String encounterCode;
    private final List<Enemy> enemies = new ArrayList<>();
    private final boolean escapeAllowed;
    private final List<String> log = new ArrayList<>();
    private final Map<String, Integer> cooldowns = new LinkedHashMap<>();
    private final List<PendingLoot> pendingLoot = new ArrayList<>();

    private Outcome outcome = Outcome.ONGOING;
    private int round = 1;
    private boolean playerDefendedThisRound;
    private boolean playerDefendedLastRound;
    private boolean playerDodgedThisRound;
    private int tempDefenseBuff;
    private int tempDefenseTurns;
    private int xpGained;
    private int fragmentsGained;

    public CombatState(String encounterCode, boolean escapeAllowed) {
        this.encounterCode = encounterCode;
        this.escapeAllowed = escapeAllowed;
    }

    public void addEnemy(Enemy enemy) {
        enemies.add(enemy);
    }

    public String encounterCode() { return encounterCode; }
    public List<Enemy> enemies() { return enemies; }
    public Enemy firstEnemy() { return enemies.isEmpty() ? null : enemies.get(0); }
    public boolean escapeAllowed() { return escapeAllowed; }
    public Outcome outcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public boolean isOngoing() { return outcome == Outcome.ONGOING; }

    public int round() { return round; }
    public void advanceRound() { round++; }

    public boolean playerDefendedThisRound() { return playerDefendedThisRound; }
    public void setPlayerDefendedThisRound(boolean v) { this.playerDefendedThisRound = v; }

    public boolean playerDefendedLastRound() { return playerDefendedLastRound; }
    public void commitDefendReaction() {
        playerDefendedLastRound = playerDefendedThisRound;
        playerDefendedThisRound = false;
    }

    /** True when the player chose to dodge this round (checked per incoming hit). */
    public boolean playerDodgedThisRound() { return playerDodgedThisRound; }
    public void setPlayerDodgedThisRound(boolean v) { this.playerDodgedThisRound = v; }
    /** Cleared at end-of-round so a dodge never lingers into the next round. */
    public void clearDodgeReaction() { this.playerDodgedThisRound = false; }

    public int tempDefenseBuff() { return tempDefenseBuff; }
    public int tempDefenseTurns() { return tempDefenseTurns; }
    /** One active tonic effect at a time: a new tonic replaces the current one. */
    public void applyTempDefense(int bonus, int turns) {
        tempDefenseBuff = bonus;
        tempDefenseTurns = Math.max(1, turns);
    }
    public void tickTempDefense() {
        if (tempDefenseTurns > 0) {
            tempDefenseTurns--;
            if (tempDefenseTurns == 0) {
                tempDefenseBuff = 0;
            }
        }
    }

    // --- cooldowns ---------------------------------------------------------
    public int cooldownOf(String skillCode) {
        return cooldowns.getOrDefault(skillCode, 0);
    }
    public void setCooldown(String skillCode, int turns) {
        if (turns <= 0) {
            cooldowns.remove(skillCode);
        } else {
            cooldowns.put(skillCode, turns);
        }
    }
    public Map<String, Integer> cooldowns() { return cooldowns; }
    public void tickCooldowns() {
        List<String> done = new ArrayList<>();
        cooldowns.replaceAll((k, v) -> v - 1);
        cooldowns.values().removeIf(v -> v <= 0);
    }

    // --- log ---------------------------------------------------------------
    public void log(String line) {
        log.add(line);
        if (log.size() > LOG_LIMIT) {
            log.remove(0);
        }
    }
    public List<String> logLines() { return List.copyOf(log); }

    // --- rewards -----------------------------------------------------------
    public void addPendingLoot(String itemCode, int qty) {
        pendingLoot.add(new PendingLoot(itemCode, qty));
    }
    public List<PendingLoot> pendingLoot() { return pendingLoot; }
    public void clearLoot() { pendingLoot.clear(); }

    public int xpGained() { return xpGained; }
    public void addXp(int xp) { xpGained += xp; }
    public int fragmentsGained() { return fragmentsGained; }
    public void addFragments(int n) { fragmentsGained += n; }

    /** Reward rows handed to the LOOT phase; inspectable before taking. */
    public record PendingLoot(String itemCode, int quantity) {
    }
}
