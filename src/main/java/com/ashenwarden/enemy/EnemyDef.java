package com.ashenwarden.enemy;

import java.util.List;
import java.util.Map;

/**
 * Static enemy definition (content/enemies.json). Behavior is a deterministic
 * rule table selected by behaviorCode — no AI/ML anywhere in this game.
 *
 * @param resistances element -> damage reduction 0.0..1.0 (1.0 = immune)
 * @param weaknesses  elements that take +25% damage
 * @param moves       every action this enemy may telegraph
 * @param behaviorCode key into BehaviorRegistry
 */
public record EnemyDef(
        String code,
        String name,
        String region,
        int hp,
        int attack,
        int defense,
        int xp,
        String lore,
        String counterplayHint,
        Map<String, Double> resistances,
        List<String> weaknesses,
        boolean boss,
        String behaviorCode,
        List<EnemyAction> moves,
        List<Drop> drops
) {
    public EnemyAction move(String name) {
        if (moves == null || name == null) {
            return null;
        }
        return moves.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public double resistanceOf(String element) {
        if (resistances == null || element == null) {
            return 0.0;
        }
        return resistances.getOrDefault(element, 0.0);
    }

    public boolean weakTo(String element) {
        return weaknesses != null && element != null && weaknesses.contains(element);
    }

    public record Drop(String itemCode, int chance, int quantity) {
    }
}
