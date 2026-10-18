package com.ashenwarden.enemy;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback behavior: a seeded choice among the enemy's damaging moves.
 * Still strictly rule-based — it never invents an action outside the definition.
 */
public final class BasicMeleeBehavior implements EnemyBehavior {
    @Override
    public EnemyAction choose(Enemy enemy, Context ctx) {
        List<EnemyAction> damaging = new ArrayList<>();
        if (enemy.def().moves() != null) {
            for (EnemyAction m : enemy.def().moves()) {
                if (m.isDamaging()) {
                    damaging.add(m);
                }
            }
        }
        if (damaging.isEmpty()) {
            return Behaviors.move(enemy.def(), "__none__");
        }
        return damaging.get(ctx.pick(damaging.size()));
    }
}
