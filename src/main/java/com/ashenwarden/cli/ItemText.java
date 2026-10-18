package com.ashenwarden.cli;

import com.ashenwarden.item.ItemCatalog;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.item.WeaponSpec;
import com.ashenwarden.player.EquipmentManager;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the inspection text required BEFORE every choice (loot, shop, equip,
 * upgrade, combat item). Unknown fields print as "Not applicable" or "Unknown";
 * undiscovered effects print as "???" and are never invented.
 */
public final class ItemText {
    private ItemText() {
    }

    /** Full detail screen for one item, including a delta vs the equipped item. */
    public static List<String> describe(ItemDef def, Inventory inventory, EquipmentManager equipment,
                                        ItemCatalog catalog) {
        List<String> out = new ArrayList<>();
        out.add(def.name().toUpperCase());
        out.add("Category: " + category(def) + " | Rarity: " + def.rarity().label());
        out.add("Description: " + text(def.description()));
        out.add("Lore: '" + text(def.lore()) + "'");

        List<String> stats = new ArrayList<>();
        if (def.attackBonus() != 0) {
            stats.add("Attack (equipment bonus): " + signed(def.attackBonus()));
        }
        if (def.defenseBonus() != 0) {
            stats.add("Defense (equipment bonus): " + signed(def.defenseBonus()));
        }
        if (def.maxHpBonus() != 0) {
            stats.add("Max HP (equipment bonus): " + signed(def.maxHpBonus()));
        }
        if (def.maxStaminaBonus() != 0) {
            stats.add("Max Stamina (equipment bonus): " + signed(def.maxStaminaBonus()));
        }
        if (def.maxAshBonus() != 0) {
            stats.add("Max Ash (equipment bonus): " + signed(def.maxAshBonus()));
        }
        if (def.weapon() != null) {
            WeaponSpec w = def.weapon();
            stats.add("Weapon damage: " + w.damage() + " (" + text(w.element()) + ")");
            stats.add("Stamina modifier: " + signed(w.staminaMod()));
            if (w.specialName() != null && !w.specialName().isBlank()) {
                stats.add("Special: " + w.specialName()
                        + " — " + text(w.specialDescription())
                        + " (Cost: " + w.specialStaminaCost() + " stamina, " + w.specialAshCost()
                        + " ash | Cooldown: " + w.specialCooldown() + " turns"
                        + (w.specialEffect() == null || w.specialEffect().isBlank() ? "" : " | " + w.specialEffect()) + ")");
            }
        }
        if (def.consumable() != null && def.consumable().hasAny()) {
            var c = def.consumable();
            if (c.healHp() != 0) {
                stats.add("Effect: restores " + c.healHp() + " HP (capped at max HP)");
            }
            if (c.restoreAsh() != 0) {
                stats.add("Effect: restores " + c.restoreAsh() + " Ash energy");
            }
            if (c.restoreStamina() != 0) {
                stats.add("Effect: restores " + c.restoreStamina() + " stamina");
            }
            if (c.cureStatus() != null) {
                stats.add("Effect: cures " + c.cureStatus());
            }
            if (c.buffDefense() != 0) {
                stats.add("Effect: +" + c.buffDefense() + " defense for " + c.buffTurns() + " combat rounds");
            }
        }
        if (stats.isEmpty()) {
            out.add("Stats: Not applicable");
        } else {
            out.addAll(stats);
        }
        if (def.effectText() != null && !def.effectText().isBlank()) {
            out.add("Special effect: " + def.effectText());
        }
        out.add("Drawback: " + (def.drawback() == null || def.drawback().isBlank()
                ? "Not applicable" : def.drawback()));
        out.add("Requirements: " + (def.requirements() == null || def.requirements().isBlank()
                ? "Not applicable" : def.requirements()));
        out.add("Type: " + (def.stackable() ? "Stackable" : "Unique")
                + " | Consumable: " + (def.isConsumable() ? "Yes" : "No")
                + " | Tradeable: " + (def.tradeable() ? "Yes" : "No (quest item)"));

        int owned = inventory == null ? 0 : inventory.quantityOf(def.code());
        boolean equipped = equipment != null && isEquippedSomewhere(equipment, def.code());
        out.add("Owned: " + owned + " | Sell value: "
                + (def.value() > 0 ? def.value() + " Ash Fragments" : "Not sold")
                + (equipped ? " | EQUIPPED" : ""));

        if (def.isEquipable() && equipment != null) {
            EquipmentSlot slot = EquipmentManager.slotFor(def.type());
            String equippedCode = firstEquippedInType(equipment, def, slot);
            if (equippedCode != null && !equippedCode.equals(def.code())) {
                out.addAll(compare(def, catalog.require(equippedCode)));
            } else if (equippedCode == null) {
                out.add("Current comparison: no item in that slot — nothing to compare.");
            } else {
                out.add("Current comparison: this is the equipped item.");
            }
        }
        return out;
    }

    private static boolean isEquippedSomewhere(EquipmentManager equipment, String code) {
        if (code == null) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (code.equals(equipment.equippedCode(slot))) {
                return true;
            }
        }
        return false;
    }

    private static String firstEquippedInType(EquipmentManager equipment, ItemDef def, EquipmentSlot slot) {
        if (def.type() == com.ashenwarden.item.ItemType.ACCESSORY) {
            String a1 = equipment.equippedCode(EquipmentSlot.ACCESSORY_1);
            if (a1 != null) {
                return a1;
            }
            return equipment.equippedCode(EquipmentSlot.ACCESSORY_2);
        }
        return equipment.equippedCode(slot);
    }

    /** Precise deltas of candidate vs equipped (candidate minus equipped). */
    public static List<String> compare(ItemDef candidate, ItemDef equipped) {
        List<String> out = new ArrayList<>();
        out.add("COMPARISON — " + candidate.name() + " vs " + equipped.name());
        out.add(delta("Attack", candidate.attackBonus() - equipped.attackBonus()));
        out.add(delta("Defense", candidate.defenseBonus() - equipped.defenseBonus()));
        out.add(delta("Max HP", candidate.maxHpBonus() - equipped.maxHpBonus()));
        out.add(delta("Max Stamina", candidate.maxStaminaBonus() - equipped.maxStaminaBonus()));
        out.add(delta("Max Ash", candidate.maxAshBonus() - equipped.maxAshBonus()));
        if (candidate.weapon() != null && equipped.weapon() != null) {
            out.add(delta("Weapon damage", candidate.weapon().damage() - equipped.weapon().damage()));
            out.add(delta("Stamina modifier", equipped.weapon().staminaMod() - candidate.weapon().staminaMod()));
        }
        if (candidate.drawback() != null && !candidate.drawback().isBlank()) {
            out.add("New drawback: " + candidate.drawback());
        }
        if (equipped.drawback() != null && !equipped.drawback().isBlank()
                && (candidate.drawback() == null || !candidate.drawback().equals(equipped.drawback()))) {
            out.add("Removed drawback: " + equipped.drawback());
        }
        return out;
    }

    private static String delta(String label, int value) {
        if (value > 0) {
            return label + ": +" + value;
        }
        if (value < 0) {
            return label + ": " + value;
        }
        return label + ": no change";
    }

    /** Short one-line row for menus (still shows the key tradeoff tag). */
    public static String row(ItemDef def, Inventory inventory) {
        StringBuilder sb = new StringBuilder(def.name());
        if (def.weapon() != null) {
            sb.append("   ATK ").append(def.weapon().damage());
        } else if (def.defenseBonus() != 0) {
            sb.append("   DEF ").append(signed(def.defenseBonus()));
        }
        sb.append("   ").append(def.rarity().label());
        if (def.drawback() != null && !def.drawback().isBlank()) {
            sb.append("   [!]");
        }
        int owned = inventory == null ? 0 : inventory.quantityOf(def.code());
        if (owned > 0 && !def.isEquipable()) {
            sb.append("   x").append(owned);
        }
        return sb.toString();
    }

    private static String category(ItemDef def) {
        return switch (def.type()) {
            case WEAPON -> "Weapon";
            case ARMOR -> "Armor";
            case ACCESSORY -> "Accessory";
            case CONSUMABLE -> "Consumable";
            case MATERIAL -> "Material";
            case QUEST -> "Quest item";
            case CURRENCY -> "Currency";
        };
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
