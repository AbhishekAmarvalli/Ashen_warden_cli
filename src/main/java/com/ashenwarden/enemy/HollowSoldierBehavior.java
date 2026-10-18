package com.ashenwarden.enemy;

/**
 * Hollow Soldier rule table (spec example, strictly deterministic):
 *  1. at <=25% HP may raise a battered guard (not if it just defended)
 *  2. else punish a turtling player with an unblockable shield bash
 *  3. else every 3rd round winds up a heavy, parryable charge
 *  4. else seeded choice between light slash and rusted jab
 */
public final class HollowSoldierBehavior implements EnemyBehavior {
    private static final String LAST_DEFEND = "last_defend_round";

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        String lastDefend = enemy.getState(LAST_DEFEND);
        boolean justDefended = lastDefend != null && Integer.parseInt(lastDefend) == ctx.round() - 1;

        if (enemy.hpFraction() <= 0.25 && !justDefended) {
            enemy.putState(LAST_DEFEND, Integer.toString(ctx.round()));
            return Behaviors.move(def, "Battered Guard");
        }
        if (ctx.playerDefending()) {
            return Behaviors.move(def, "Shield Bash");
        }
        if (ctx.round() % 3 == 0) {
            return Behaviors.move(def, "Charged Heavy");
        }
        return ctx.pick(2) == 0
                ? Behaviors.move(def, "Light Slash")
                : Behaviors.move(def, "Rusted Jab");
    }
}
