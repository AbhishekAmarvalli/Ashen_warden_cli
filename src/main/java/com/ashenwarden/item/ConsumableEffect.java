package com.ashenwarden.item;

/**
 * Effect of a consumable item. Zero values mean "not applicable" in inspection
 * text; nothing is invented for the player.
 *
 * @param healHp        flat HP restored (capped at max HP)
 * @param restoreAsh    Ash energy restored
 * @param restoreStamina stamina restored
 * @param cureStatus    status code removed, e.g. BLEED, or null
 * @param buffDefense   temporary defense bonus
 * @param buffTurns     duration of the temporary buff in combat rounds
 */
public record ConsumableEffect(
        int healHp,
        int restoreAsh,
        int restoreStamina,
        String cureStatus,
        int buffDefense,
        int buffTurns
) {
    public boolean hasAny() {
        return healHp != 0 || restoreAsh != 0 || restoreStamina != 0
                || cureStatus != null || buffDefense != 0;
    }
}
