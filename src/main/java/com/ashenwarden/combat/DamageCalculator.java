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
 * Crits, loot and flee decisions use the injected seeded Random only.
 */
public final class DamageCalculator {
    public static final double CRIT_CHANCE = 0.05;
    public static final double CRIT_MULTIPLIER = 1.5;
    public static final double WEAKNESS_MULTIPLIER = 1.25;
    public static final double RESISTED_MULTIPLIER = 0.75; // "resistant but not immune"
    public static final double DEFEND_MULTIPLIER = 0.5;
    public static final double WEAKNESS_STATUS_MULTIPLIER = 0.75;

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
}
