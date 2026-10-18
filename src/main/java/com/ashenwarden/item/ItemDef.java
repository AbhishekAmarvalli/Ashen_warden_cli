package com.ashenwarden.item;

/**
 * Immutable item definition (the catalogue side). Mutable ownership lives in
 * Inventory. Loaded from src/main/resources/content/items.json.
 *
 * Inspection-first rule: every field a player may need before committing to a
 * choice lives here; the renderer prints "Not applicable" or "Unknown" for
 * absent fields instead of inventing values.
 */
public record ItemDef(
        String code,
        String name,
        ItemType type,
        Rarity rarity,
        String description,
        String lore,
        int value,             // shop/sell value in Ash Fragments; 0 = not sold
        boolean stackable,
        boolean tradeable,     // quest items are never tradeable
        String requirements,   // e.g. "Level 3" or null when none
        String drawback,       // explicit downside, e.g. Blood Pact self-damage
        String effectText,     // plain-language summary of the special effect
        int attackBonus,       // flat attack while equipped
        int defenseBonus,      // flat defense while equipped
        int maxHpBonus,        // max HP bonus while equipped
        int maxStaminaBonus,   // max stamina bonus while equipped
        int maxAshBonus,       // max Ash energy bonus while equipped
        WeaponSpec weapon,     // non-null for weapons
        ConsumableEffect consumable // non-null for consumables
) {
    public boolean isEquipable() {
        return type == ItemType.WEAPON || type == ItemType.ARMOR || type == ItemType.ACCESSORY;
    }

    public boolean isWeapon() {
        return type == ItemType.WEAPON && weapon != null;
    }

    public boolean isConsumable() {
        return type == ItemType.CONSUMABLE && consumable != null;
    }
}
