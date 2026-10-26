package com.ashenwarden.combat;

import com.ashenwarden.enemy.Enemy;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.Player;

import java.util.Random;

/**
 * The one place combat math happens (never scattered across classes).
 *
 * raw      = attackStat + weaponPower + skillBonus
 * mitigated= max(1, raw - floor(defense * 0.5))
 * damage   = round(mitigated * multiplier * (1 - resistance) * weakness * status)
 * Damage is never negative; a successful non-immune hit deals at least 1.
 * Crits, loot, dodge and flee decisions use the injected seeded Random only.
 *
 * Dodge (probabilistic evasion, {@link #dodgeChance}) lives here too: a dodge
 * attempt evades an incoming normal attack with chance
 * {@code DODGE_BASE_CHANCE + defense * DODGE_DEFENSE_FACTOR}, capped at
 * {@link #DODGE_MAX_CHANCE}. Attacks tagged UNBLOCKABLE cannot be evaded.
 */
public final class DamageCalculator {
    public static final double CRIT_CHANCE = 0.05;
    public static final double CRIT_MULTIPLIER = 1.5;
    public static final double WEAKNESS_MULTIPLIER = 1.25;
    public static final double RESISTED_MULTIPLIER = 0.75; // "resistant but not immune"
    public static final double DEFEND_MULTIPLIER = 0.5;
    public static final double WEAKNESS_STATUS_MULTIPLIER = 0.75;

    /** Dodge: a stance taken instead of attacking, rolled per incoming hit. */
    public static final double DODGE_BASE_CHANCE = 0.45;
    /** Each point of effective defense adds this much evasion (defensive training). */
    public static final double DODGE_DEFENSE_FACTOR = 0.015;
    /** No build dodges everything — the ceiling keeps offense king. */
    public static final double DODGE_MAX_CHANCE = 0.80;

    /** Outcome of one damage application, ready for the combat log. */
    public record Hit(int raw, int mitigated, int finalDamage, boolean critical) {
    }

    private final Random rng;

    public DamageCalculator(Random rng) {
        this.rng = rng;
    }

    private static int mitigate(int raw, int defense) {
        return Math.max(1, raw - (int) Math.floor(Math.max(0, defense) * 0.5));
    }

    private static int clampNonNegative(int value) {
        return Math.max(0, value);
    }

    /** Warden -> enemy, including element resistance, weakness, guard break, crit. */
    public Hit playerToEnemy(Player player, EffectiveStats stats, int weaponPower,
                             double multiplier, String element, Enemy enemy) {
        int skillBonus = 0;
        int raw = stats.attack() + weaponPower + skillBonus;
        int enemyDefense = enemy.def().defense();
        if (enemy.hasStatus(StatusType.GUARD_BREAK)) {
            enemyDefense = enemyDefense / 2;
        }
        int mitigated = mitigate(raw, enemyDefense);

        double scale = multiplier;
        double resistance = enemy.def().resistanceOf(element);
        if (resistance >= 1.0) {
            return new Hit(raw, mitigated, 0, false);
        }
        if (resistance > 0) {
            scale *= (1.0 - resistance);
        }
        if (enemy.def().weakTo(element)) {
            scale *= WEAKNESS_MULTIPLIER;
        }
        if (player.hasStatus(StatusType.WEAKNESS)) {
            scale *= WEAKNESS_STATUS_MULTIPLIER;
        }

        boolean crit = rng.nextDouble() < CRIT_CHANCE;
        if (crit) {
            scale *= CRIT_MULTIPLIER;
        }
        // defend-by-enemy: their Battered Guard action halves incoming damage this round
        if (enemy.getCurrentIntent() != null && enemy.getCurrentIntent().isDefend()) {
            scale *= DEFEND_MULTIPLIER;
        }

        // immunity was already handled above: a successful non-immune hit deals at least 1
        int damage = Math.max(1, clampNonNegative((int) Math.round(mitigated * scale)));
        return new Hit(raw, mitigated, damage, crit);
    }

    /** Enemy -> Warden, including defend eligibility and unblockable attacks. */
    public Hit enemyToPlayer(Enemy enemy, double multiplier, boolean playerDefending,
                             boolean unblockable, EffectiveStats stats) {
        int raw = (int) Math.round(enemy.def().attack() * multiplier);
        int mitigated = mitigate(raw, stats.defense());

        double scale = 1.0;
        boolean reduced = playerDefending && !unblockable;
        if (reduced) {
            scale *= DEFEND_MULTIPLIER;
        }
        if (enemy.hasStatus(StatusType.WEAKNESS)) {
            scale *= WEAKNESS_STATUS_MULTIPLIER;
        }
        boolean crit = rng.nextDouble() < CRIT_CHANCE;
        if (crit) {
            scale *= CRIT_MULTIPLIER;
        }

        int damage = clampNonNegative((int) Math.round(mitigated * scale));
        return new Hit(raw, mitigated, damage, crit);
    }

    /** Direct status/DoT tick damage: flat power, never reduced by defense. */
    public int dotTick(int power) {
        return Math.max(1, power);
    }

    /**
     * Probability that a dodge attempt evades the next normal attack.
     * Documented formula: base 45% + 1.5% per effective defense point,
     * capped at 80% (unblockable attacks are never evaded — that is decided
     * by the caller, not by this number).
     */
    public static double dodgeChance(EffectiveStats stats) {
        double chance = DODGE_BASE_CHANCE + Math.max(0, stats.defense()) * DODGE_DEFENSE_FACTOR;
        return Math.min(DODGE_MAX_CHANCE, chance);
    }

    /** One seeded roll for the current dodge attempt. */
    public boolean rollsDodge(EffectiveStats stats) {
        return rng.nextDouble() < dodgeChance(stats);
    }
}
