package com.ashenwarden.enemy;

/**
 * Cursed Executioner rule table (Tier-2 elite — slow, telegraphed executioner):
 *  1. if it announced the execution last round, the Guillotine lands now
 *     (huge, parryable — the parry payoff of the pattern)
 *  2. else below half health it shifts to its second phase once, announcing it
 *  3. phase 2: every 3rd round a bleeding Flensing Cleave, otherwise a seeded
 *     mix of the cleave and the fast Iron Backhand
 *  4. phase 1: it punishes a turtling Warden with the Iron Backhand, every
 *     3rd round it measures (telegraph) before the guillotine, otherwise a
 *     seeded mix of the rusted cleaver and the backhand
 */
public final class CursedExecutionerBehavior implements EnemyBehavior {
    private static final String WOUNDING = "execution_announced";

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        if (enemy.flag(WOUNDING)) {
            enemy.setFlag(WOUNDING, false);
            return Behaviors.move(def, "Guillotine");
        }
        if (enemy.getPhase() == 1 && enemy.hpFraction() <= 0.5) {
            enemy.setPhase(2);
            return Behaviors.move(def, "The Red Opens");
        }
        if (enemy.getPhase() == 2) {
            if (ctx.round() % 3 == 0) {
                return Behaviors.move(def, "Flensing Cleave");
            }
            return ctx.pick(2) == 0
                    ? Behaviors.move(def, "Iron Backhand")
                    : Behaviors.move(def, "Flensing Cleave");
        }
        if (ctx.playerDefending()) {
            return Behaviors.move(def, "Iron Backhand");
        }
        if (ctx.round() % 3 == 0) {
            enemy.setFlag(WOUNDING, true);
            return Behaviors.move(def, "Measured Steps");
        }
        return ctx.pick(2) == 0
                ? Behaviors.move(def, "Rusted Cleaver")
                : Behaviors.move(def, "Iron Backhand");
    }
}
