package com.ashenwarden.enemy;

/**
 * Wraith Priest rule table (Tier-2 elite — caster with a ward cycle):
 *  1. if it finished the Grave Litany last round, Soul Rake lands now
 *     (the litany's telegraphed payoff)
 *  2. else below 40% health it opens its Ashen Rite once, announcing the
 *     second phase and knitting some of its own wounds
 *  3. phase 2: every 3rd round it raises the Veil Ward (guards and mends a
 *     little), otherwise a seeded mix of Soul Rake and Grave Chant
 *  4. phase 1: it punishes a turtling Warden with Grave Chant, every 4th
 *     round it wards, otherwise a seeded mix of chant and the Grave Litany
 *     that sets up the Soul Rake
 */
public final class WraithPriestBehavior implements EnemyBehavior {
    private static final String LITANY = "litany_intoned";

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        if (enemy.flag(LITANY)) {
            enemy.setFlag(LITANY, false);
            return Behaviors.move(def, "Soul Rake");
        }
        if (enemy.getPhase() == 1 && enemy.hpFraction() <= 0.4) {
            enemy.setPhase(2);
            return Behaviors.move(def, "Ashen Rite");
        }
        if (enemy.getPhase() == 2) {
            if (ctx.round() % 3 == 0) {
                return Behaviors.move(def, "Veil Ward");
            }
            return ctx.pick(2) == 0
                    ? Behaviors.move(def, "Soul Rake")
                    : Behaviors.move(def, "Grave Chant");
        }
        if (ctx.playerDefending()) {
            return Behaviors.move(def, "Grave Chant");
        }
        if (ctx.round() % 4 == 0) {
            return Behaviors.move(def, "Veil Ward");
        }
        if (ctx.pick(3) == 0) {
            enemy.setFlag(LITANY, true);
            return Behaviors.move(def, "Grave Litany");
        }
        return Behaviors.move(def, "Grave Chant");
    }
}
