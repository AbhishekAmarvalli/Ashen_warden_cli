package com.ashenwarden.enemy;

/**
 * Helpers shared by rule tables. move() falls back to the first damaging move so
 * a content typo can never throw mid-combat (content validation reports it first).
 */
public final class Behaviors {
    private Behaviors() {
    }

    public static EnemyAction move(EnemyDef def, String name) {
        EnemyAction action = def.move(name);
        if (action != null) {
            return action;
        }
        if (def.moves() != null) {
            for (EnemyAction candidate : def.moves()) {
                if (candidate.isDamaging()) {
                    return candidate;
                }
            }
            if (!def.moves().isEmpty()) {
                return def.moves().get(0);
            }
        }
        // Should be unreachable after startup validation; a safe neutral action:
        return new EnemyAction("Hesitates", "Steadying itself", "GATHER", 0,
                java.util.List.of(), null, 0, 0, 0, 0, "The enemy hesitates.");
    }
}
