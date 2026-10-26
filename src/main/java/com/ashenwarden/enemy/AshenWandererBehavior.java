package com.ashenwarden.enemy;

/**
 * Ashen Wanderer rule table (slow, telegraph-heavy ash creature):
 *  1. if it gathered ash last round, the censer sweep lands now (payoff)
 *  2. else it answers a turtling Warden with a spell (cinder veil, no parry)
 *  3. else every 3rd round it stoops to gather ash (visible telegraph)
 *  4. else below half health it grows reckless: sweep or veil with no tell
 *  5. else a plain lurching strike
 */
public final class AshenWandererBehavior implements EnemyBehavior {
    private static final String GATHERED = "gathered";

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        if (enemy.flag(GATHERED)) {
            enemy.setFlag(GATHERED, false);
            return Behaviors.move(def, "Censer Sweep");
        }
        if (ctx.playerDefending()) {
            return Behaviors.move(def, "Cinder Veil");
        }
        if (ctx.round() % 3 == 0) {
            enemy.setFlag(GATHERED, true);
            return Behaviors.move(def, "Gather Ash");
        }
        if (enemy.hpFraction() <= 0.5) {
            return ctx.pick(2) == 0
                    ? Behaviors.move(def, "Censer Sweep")
                    : Behaviors.move(def, "Cinder Veil");
        }
        return Behaviors.move(def, "Lurching Strike");
    }
}
