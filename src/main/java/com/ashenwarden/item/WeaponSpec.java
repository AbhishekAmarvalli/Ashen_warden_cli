package com.ashenwarden.item;

/**
 * Combat profile of a weapon. All numbers are tuning suggestions loaded from
 * content/items.json and are not canon.
 *
 * @param damage           base weapon power added to raw damage
 * @param staminaMod       flat stamina cost modifier applied to weapon actions
 * @param element          damage element: physical | flame | spectral | blood
 * @param specialName      skill menu name of the weapon special
 * @param specialDescription inspection text for the special
 * @param specialMultiplier damage multiplier of the special
 * @param specialStaminaCost stamina cost of the special
 * @param specialAshCost    Ash energy cost of the special
 * @param specialCooldown   rounds before the special can be used again
 * @param specialEffect     extra effect text (status application, stagger, ...)
 */
public record WeaponSpec(
        int damage,
        int staminaMod,
        String element,
        String specialName,
        String specialDescription,
        double specialMultiplier,
        int specialStaminaCost,
        int specialAshCost,
        int specialCooldown,
        String specialEffect
) {
    public static final String ELEMENT_PHYSICAL = "physical";
}
