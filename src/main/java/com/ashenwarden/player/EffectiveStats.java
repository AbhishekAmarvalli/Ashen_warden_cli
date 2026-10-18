package com.ashenwarden.player;

import com.ashenwarden.combat.StatusType;
import com.ashenwarden.item.ItemCatalog;

/**
 * Derived combat stats: base stats + active equipment + active statuses.
 * Computed on demand so equipping/unequipping can never duplicate bonuses.
 */
public record EffectiveStats(int maxHp, int maxStamina, int maxAsh, int attack, int defense) {

    public static EffectiveStats compute(Player player, EquipmentManager equipment, ItemCatalog catalog) {
        EquipmentManager.BonusSet bonus = equipment.bonuses(catalog);
        int defense = player.getBaseDefense() + bonus.defense();
        if (player.hasStatus(StatusType.GUARD_BREAK)) {
            defense = Math.max(0, defense / 2);
        }
        return new EffectiveStats(
                player.getMaxHp() + bonus.maxHp(),
                player.getMaxStamina() + bonus.maxStamina(),
                player.getMaxAsh() + bonus.maxAsh(),
                player.getBaseAttack() + bonus.attack(),
                defense
        );
    }
}
