package com.ashenwarden.enemy;

/**
 * Rotting Pilgrim rule table (self-mending support fighter):
 *  1. below half health, and if it has not prayed within the last 2 rounds,
 *     it kneels to mend its rot (visible, punishable — never a heal loop)
 *  2. else it answers a turtling Warden with the reliquary breath (spell)
 *  3. else every 4th round it breathes the reliquary anyway (pressure)
 *  4. else seeded choice: the poisonous rot-touched grasp, or a plain staff strike
 */
public final class RottingPilgrimBehavior implements EnemyBehavior {
    private static final String LAST_PRAYER = "last_prayer_round";
    /** After kneeling, it must survive this many rounds before it may pray again. */
    private static final int PRAYER_COOLDOWN_ROUNDS = 2;

    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        EnemyDef def = enemy.def();

        if (enemy.hpFraction() <= 0.5) {
            String last = enemy.getState(LAST_PRAYER);
            boolean onCooldown = last != null
                    && ctx.round() - Integer.parseInt(last) <= PRAYER_COOLDOWN_ROUNDS;
            if (!onCooldown) {
                enemy.putState(LAST_PRAYER, Integer.toString(ctx.round()));
                return Behaviors.move(def, "Rot-Mending Prayer");
            }
        }
        if (ctx.playerDefending()) {
            return Behaviors.move(def, "Reliquary Breath");
        }
        if (ctx.round() % 4 == 0) {
            return Behaviors.move(def, "Reliquary Breath");
        }
        return ctx.pick(3) == 0
                ? Behaviors.move(def, "Pilgrim's Staff")
                : Behaviors.move(def, "Rot-Touched Grasp");
    }
}
