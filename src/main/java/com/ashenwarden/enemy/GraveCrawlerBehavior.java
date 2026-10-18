package com.ashenwarden.enemy;

/**
 * Grave Crawler rule table (ambush predator):
 *  1. if it already buried last round, erupts with an unblockable Ambush Bite
 *  2. else if the Warden is wounded (<=30% HP), it lunges (parryable, bleed)
 *  3. else every 3rd round it burrows to set up the ambush (visible telegraph)
 *  4. else a plain bite with a bleed chance
 */
public final class GraveCrawlerBehavior implements EnemyBehavior {
    private static final String BURIED = "buried";

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        if (enemy.flag(BURIED)) {
            enemy.setFlag(BURIED, false);
            return Behaviors.move(def, "Ambush Bite");
        }
        if (ctx.playerHpPercent() <= 30) {
            return Behaviors.move(def, "Lunge");
        }
        if (ctx.round() % 3 == 0) {
            enemy.setFlag(BURIED, true);
            return Behaviors.move(def, "Burrow");
        }
        return Behaviors.move(def, "Bite");
    }
}
