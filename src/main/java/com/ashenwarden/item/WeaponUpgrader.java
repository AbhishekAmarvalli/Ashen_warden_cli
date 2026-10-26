package com.ashenwarden.item;

/**
 * Weapon tempering rules — the math behind the shrine-side upgrade service.
 * Pure and side-effect free: no inventory, no currency, no SQL, no UI.
 *
 * <p>A weapon gains {@link #DAMAGE_PER_TIER} weapon damage per tier, up to
 * {@link #MAX_TIER}. Each tier is paid once, per item code, in Ash Fragments
 * plus forged steel; the cost curve steepens so tempering stays a choice
 * rather than an automatic grind.</p>
 */
public final class WeaponUpgrader {

    public static final int MAX_TIER = 3;
    public static final int DAMAGE_PER_TIER = 2;

    private static final int[] FRAGMENT_COST = {20, 40, 80}; // to reach tier 1, 2, 3
    private static final int[] STEEL_COST = {1, 1, 2};

    /** What the next tier asks for. */
    public record Cost(int fragments, int steel) {
    }

    private WeaponUpgrader() {
    }

    /** Flat weapon damage a tempering tier adds. */
    public static int bonusDamage(int tier) {
        return Math.max(0, tier) * DAMAGE_PER_TIER;
    }

    public static boolean isMaxed(int tier) {
        return tier >= MAX_TIER;
    }

    /** The price of the next tier, or null when the weapon is fully tempered. */
    public static Cost costForNextTier(int tier) {
        if (tier < 0 || tier >= MAX_TIER) {
            return null;
        }
        return new Cost(FRAGMENT_COST[tier], STEEL_COST[tier]);
    }
}
