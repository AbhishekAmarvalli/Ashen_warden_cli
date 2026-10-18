package com.ashenwarden.combat;

/**
 * A usable combat skill: weapon specials and ability-granted techniques.
 * Costs, cooldown and effect are always shown before use (inspection-first rule).
 */
public record SkillDef(
        String code,
        String name,
        String description,
        int staminaCost,
        int ashCost,
        int cooldown,
        double multiplier,
        String effect
) {
}
