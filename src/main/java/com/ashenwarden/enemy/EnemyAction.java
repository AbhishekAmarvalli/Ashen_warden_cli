package com.ashenwarden.enemy;

import java.util.List;

/**
 * One telegraphed enemy action. The intent shown at the start of the round is
 * exactly the action the enemy will perform (never a deliberately false telegraph).
 *
 * Tags:
 *  - PARRYABLE  : parry negates the hit, counters and staggers the enemy.
 *  - SPELL      : parry fails (arcane), defend still works.
 *  - UNBLOCKABLE: defend does NOT reduce it and parry fails (grab/crush/flame).
 */
public record EnemyAction(
        String name,
        String intent,
        String kind,          // LIGHT | HEAVY | SPELL | DEFEND | GATHER | BUFF
        double multiplier,    // damage multiplier applied to enemy attack
        List<String> tags,
        String status,        // status code applied on hit, or null
        int statusChance,     // 0..100
        int statusPower,      // damage per tick / magnitude
        int statusTurns,
        int healAmount,       // self-heal (support actions)
        String effectText     // flavour/mechanic line for logs
) {
    public static final String TAG_PARRYABLE = "PARRYABLE";
    public static final String TAG_SPELL = "SPELL";
    public static final String TAG_UNBLOCKABLE = "UNBLOCKABLE";

    public boolean is(String tag) {
        return tags != null && tags.contains(tag);
    }

    public boolean isDefend() {
        return "DEFEND".equals(kind) || "GATHER".equals(kind);
    }

    public boolean isDamaging() {
        return multiplier > 0 && !"DEFEND".equals(kind) && !"GATHER".equals(kind) && !"BUFF".equals(kind);
    }
}
