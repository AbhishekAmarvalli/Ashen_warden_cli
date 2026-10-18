package com.ashenwarden.player;

/**
 * Equipment slots. Accessory 2 stays locked until the game unlocks it, so
 * "slot unlock" is explicit state rather than an implicit rule.
 */
public enum EquipmentSlot {
    WEAPON("weapon"),
    ARMOR("armor"),
    ACCESSORY_1("accessory1"),
    ACCESSORY_2("accessory2");

    private final String dbKey;

    EquipmentSlot(String dbKey) {
        this.dbKey = dbKey;
    }

    public String dbKey() {
        return dbKey;
    }

    public static EquipmentSlot fromDbKey(String key) {
        for (EquipmentSlot slot : values()) {
            if (slot.dbKey.equals(key)) {
                return slot;
            }
        }
        return null;
    }
}
