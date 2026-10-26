package com.ashenwarden.enemy;

import com.ashenwarden.common.ContentException;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps stable behaviorCodes from content/enemies.json to rule implementations.
 * Unknown codes fail at startup validation, never mid-combat.
 */
public final class BehaviorRegistry {
    private final Map<String, EnemyBehavior> behaviors = new HashMap<>();

    public BehaviorRegistry() {
        register("hollow_soldier", new HollowSoldierBehavior());
        register("grave_crawler", new GraveCrawlerBehavior());
        register("ashen_wanderer", new AshenWandererBehavior());
        register("rotting_pilgrim", new RottingPilgrimBehavior());
        register("cursed_executioner", new CursedExecutionerBehavior());
        register("wraith_priest", new WraithPriestBehavior());
        register("basic_melee", new BasicMeleeBehavior());
    }

    public void register(String code, EnemyBehavior behavior) {
        behaviors.put(code, behavior);
    }

    public EnemyBehavior require(String code) {
        EnemyBehavior b = behaviors.get(code);
        if (b == null) {
            throw new ContentException("Unknown enemy behavior code: " + code);
        }
        return b;
    }

    public boolean contains(String code) {
        return behaviors.containsKey(code);
    }
}
