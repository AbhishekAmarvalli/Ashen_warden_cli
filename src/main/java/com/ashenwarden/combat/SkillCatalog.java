package com.ashenwarden.combat;

import com.ashenwarden.item.ItemCatalog;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.item.WeaponSpec;
import com.ashenwarden.player.EquipmentManager;
import com.ashenwarden.player.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves the skills currently available to the Warden: the equipped weapon's
 * special plus techniques granted by unlocked story abilities. Empty when the
 * Warden fights bare-handed.
 */
public final class SkillCatalog {
    public static final String ABILITY_ASHEN_FLAME = "ashen_flame";

    public List<SkillDef> available(EquipmentManager equipment, ItemCatalog items, Set<String> abilities) {
        List<SkillDef> skills = new ArrayList<>();

        String weaponCode = equipment.equippedCode(EquipmentSlot.WEAPON);
        if (weaponCode != null) {
            ItemDef weapon = items.find(weaponCode);
            WeaponSpec spec = weapon == null ? null : weapon.weapon();
            if (spec != null && spec.specialName() != null && !spec.specialName().isBlank()) {
                skills.add(new SkillDef(
                        "special:" + weaponCode,
                        spec.specialName(),
                        spec.specialDescription() == null ? weapon.name() + " special technique."
                                : spec.specialDescription(),
                        spec.specialStaminaCost(),
                        spec.specialAshCost(),
                        spec.specialCooldown(),
                        spec.specialMultiplier(),
                        spec.specialEffect()
                ));
            }
        }

        if (abilities != null && abilities.contains(ABILITY_ASHEN_FLAME)) {
            skills.add(new SkillDef(
                    "skill:ember_arc",
                    "Ember Arc",
                    "A sweeping arc of amber flame drawn from the ember in your chest.",
                    10, 8, 2, 1.7,
                    "Applies Burn for 2 turns when it connects."
            ));
        }
        return skills;
    }
}
