package com.ashenwarden.enemy;

import java.util.Random;

/**
 * Deterministic, rule-based enemy decision maker. Implementations read a small
 * priority table; the seeded Random may only choose between already-valid
 * actions. No machine learning, no LLM, no network calls — ever.
 */
public interface EnemyBehavior {

    /**
     * @param enemy the acting enemy instance (blackboard carries cross-round state)
     * @param ctx   round number, player state, seeded randomness
     * @return the action the enemy performs this round (becomes the telegraph)
     */
    EnemyAction choose(Enemy enemy, Context ctx);

    /** Read-only combat context handed to behaviors. */
    record Context(
            int round,
            boolean playerDefending,
            int playerHpPercent,
            Random rng
    ) {
        /** Seeded choice among valid options, 0..n-1. */
        public int pick(int options) {
            if (options <= 1) {
                return 0;
            }
            return rng.nextInt(options);
        }
    }
}
