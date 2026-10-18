package com.ashenwarden.player;

import com.ashenwarden.item.ItemCatalog;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.item.ItemType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Holds equipped item codes per slot. Equipped gear is moved out of the inventory
 * into a slot (and back on unequip), so an item can never be equipped twice and
 * passive bonuses can never stack from duplicate copies. Derived stats are
 * computed on demand from base stats + equipped codes; base stats are never
 * mutated by equipping.
 */
public final class EquipmentManager {
    private final Map<EquipmentSlot, String> equipped = new EnumMap<>(EquipmentSlot.class);
    private boolean accessory2Unlocked;

    public EquipmentManager() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipped.put(slot, null);
        }
    }

    public String equippedCode(EquipmentSlot slot) {
        return equipped.get(slot);
    }

    public boolean isAccessory2Unlocked() {
        return accessory2Unlocked;
    }

    public void unlockAccessory2() {
        accessory2Unlocked = true;
    }

    public static EquipmentSlot slotFor(ItemType type) {
        if (type == ItemType.WEAPON) {
            return EquipmentSlot.WEAPON;
        }
        if (type == ItemType.ARMOR) {
            return EquipmentSlot.ARMOR;
        }
        if (type == ItemType.ACCESSORY) {
            return EquipmentSlot.ACCESSORY_1;
        }
        return null;
    }

    /**
     * Equips one copy of itemCode from the inventory into slot.
     * Any item already in that slot returns to the inventory first (swap semantics).
     *
     * @return null on success, otherwise a player-facing rejection reason
     */
    public String equip(EquipmentSlot slot, String itemCode, Inventory inventory,
                        ItemCatalog catalog, int playerLevel) {
        if (slot == null) {
            return "No equipment slot was given.";
        }
        if (slot == EquipmentSlot.ACCESSORY_2 && !accessory2Unlocked) {
            return "The second accessory slot has not been unlocked yet.";
        }
        ItemDef def = catalog.require(itemCode);
        if (inventory.quantityOf(itemCode) < 1) {
            return "You do not carry a " + def.name() + ".";
        }
        if (!matchesSlot(slot, def)) {
            return def.name() + " cannot go into the " + slot.name().toLowerCase() + " slot.";
        }
        if (def.requirements() != null && !def.requirements().isBlank()
                && !requirementsMet(def, playerLevel)) {
            return "Requirements not met: " + def.requirements();
        }
        String current = equipped.get(slot);
        if (itemCode.equals(current)) {
            return def.name() + " is already equipped in that slot.";
        }
        if (!inventory.remove(itemCode, 1)) {
            return "You do not carry a " + def.name() + ".";
        }
        if (current != null) {
            inventory.add(current, 1); // old item returns to the bag
        }
        equipped.put(slot, itemCode);
        return null;
    }

    /** Unequips the slot back into the inventory. Returns null on success or a reason. */
    public String unequip(EquipmentSlot slot, Inventory inventory) {
        if (slot == null) {
            return "No equipment slot was given.";
        }
        String current = equipped.get(slot);
        if (current == null) {
            return "That slot is already empty.";
        }
        if (!inventory.add(current, 1)) {
            return "Your inventory cannot hold another stack.";
        }
        equipped.put(slot, null);
        return null;
    }

    public boolean matchesSlot(EquipmentSlot slot, ItemDef def) {
        ItemType type = def.type();
        return (slot == EquipmentSlot.WEAPON && type == ItemType.WEAPON)
                || (slot == EquipmentSlot.ARMOR && type == ItemType.ARMOR)
                || ((slot == EquipmentSlot.ACCESSORY_1 || slot == EquipmentSlot.ACCESSORY_2)
                    && type == ItemType.ACCESSORY);
    }

    private boolean requirementsMet(ItemDef def, int playerLevel) {
        // Requirements are written like "Level 3"; unknown formats are treated as unmet
        // only when they mention a level, otherwise they are informational.
        String req = def.requirements().toLowerCase();
        int idx = req.indexOf("level ");
        if (idx >= 0) {
            String rest = req.substring(idx + 6).trim();
            StringBuilder num = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (Character.isDigit(c)) {
                    num.append(c);
                } else {
                    break;
                }
            }
            if (num.length() > 0) {
                return playerLevel >= Integer.parseInt(num.toString());
            }
        }
        return true;
    }

    /** Aggregated flat bonuses from all equipped codes. */
    public BonusSet bonuses(ItemCatalog catalog) {
        int atk = 0;
        int def = 0;
        int hp = 0;
        int stamina = 0;
        int ash = 0;
        for (String code : equipped.values()) {
            if (code == null) {
                continue;
            }
            ItemDef d = catalog.find(code);
            if (d == null) {
                continue;
            }
            atk += d.attackBonus();
            def += d.defenseBonus();
            hp += d.maxHpBonus();
            stamina += d.maxStaminaBonus();
            ash += d.maxAshBonus();
        }
        return new BonusSet(atk, def, hp, stamina, ash);
    }

    public Map<EquipmentSlot, String> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(equipped));
    }

    public void loadSnapshot(Map<EquipmentSlot, String> data) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipped.put(slot, data == null ? null : data.get(slot));
        }
    }

    /** Flat stat bonuses contributed by equipment only. */
    public record BonusSet(int attack, int defense, int maxHp, int maxStamina, int maxAsh) {
    }
}
