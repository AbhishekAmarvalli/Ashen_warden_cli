package com.ashenwarden.player;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.item.ItemCatalog;
import com.ashenwarden.item.ItemDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAndEquipmentTest {
    private static ItemCatalog items;
    private static ContentRepository content;

    @BeforeAll
    static void loadContent() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        items = content.items();
    }

    @Test
    void inventoryNeverGoesNegativeOrCorrupt() {
        Inventory inv = new Inventory();
        assertTrue(inv.add("crimson_vial", 2));
        assertFalse(inv.add("crimson_vial", 0));
        assertFalse(inv.add("crimson_vial", -3));
        assertFalse(inv.add(null, 1));
        assertEquals(2, inv.quantityOf("crimson_vial"));
        assertFalse(inv.remove("crimson_vial", 5), "removing more than owned must fail");
        assertEquals(2, inv.quantityOf("crimson_vial"), "failed removal must not mutate");
        assertTrue(inv.remove("crimson_vial", 2));
        assertEquals(0, inv.quantityOf("crimson_vial"));
        assertTrue(inv.isEmpty());
    }

    @Test
    void equipMovesItemOutAndBackWithoutDuplication() {
        Player player = new Player("T");
        Inventory inv = new Inventory();
        EquipmentManager eq = new EquipmentManager();
        inv.add("warden_silverblade", 1);
        inv.add("warden_armor", 1);

        assertNull(eq.equip(EquipmentSlot.WEAPON, "warden_silverblade", inv, items, 1));
        assertEquals(0, inv.quantityOf("warden_silverblade"), "equipped gear leaves the bag");
        assertEquals("warden_silverblade", eq.equippedCode(EquipmentSlot.WEAPON));

        int defenseWithArmor = EffectiveStats.compute(player, eq, items).defense();
        assertNull(eq.equip(EquipmentSlot.ARMOR, "warden_armor", inv, items, 1));
        assertEquals(defenseWithArmor + 3,
                EffectiveStats.compute(player, eq, items).defense(), "Warden Armor gives +3 defense");

        // equip the same armor again repeatedly: defense must never duplicate
        assertNull(eq.unequip(EquipmentSlot.ARMOR, inv));
        assertEquals(defenseWithArmor, EffectiveStats.compute(player, eq, items).defense());
        assertNull(eq.equip(EquipmentSlot.ARMOR, "warden_armor", inv, items, 1));
        assertNull(eq.unequip(EquipmentSlot.ARMOR, inv));
        assertNull(eq.equip(EquipmentSlot.ARMOR, "warden_armor", inv, items, 1));
        assertEquals(defenseWithArmor + 3, EffectiveStats.compute(player, eq, items).defense(),
                "repeated equip/unequip must not stack bonuses");
    }

    @Test
    void swappingWeaponsReturnsOldOneToInventory() {
        Inventory inv = new Inventory();
        EquipmentManager eq = new EquipmentManager();
        inv.add("warden_silverblade", 1);
        inv.add("wraith_dagger", 1);
        assertNull(eq.equip(EquipmentSlot.WEAPON, "warden_silverblade", inv, items, 1));
        assertNull(eq.equip(EquipmentSlot.WEAPON, "wraith_dagger", inv, items, 1));
        assertEquals("wraith_dagger", eq.equippedCode(EquipmentSlot.WEAPON));
        assertEquals(1, inv.quantityOf("warden_silverblade"), "swapped-out weapon returns to the bag");
        assertEquals(0, inv.quantityOf("wraith_dagger"));
    }

    @Test
    void wrongSlotAndLockedSlotAreRejected() {
        Inventory inv = new Inventory();
        EquipmentManager eq = new EquipmentManager();
        inv.add("warden_silverblade", 1);
        inv.add("crimson_vial", 1);
        assertNotNull(eq.equip(EquipmentSlot.ARMOR, "warden_silverblade", inv, items, 1),
                "a weapon cannot be equipped as armor");
        assertNotNull(eq.equip(EquipmentSlot.ARMOR, "crimson_vial", inv, items, 1),
                "a potion cannot be equipped as armor");
        inv.add("ancient_coin", 1);
        assertNotNull(eq.equip(EquipmentSlot.ACCESSORY_2, "ancient_coin", inv, items, 1),
                "second accessory slot starts locked");
    }

    @Test
    void equipRequiresOwnedItem() {
        Inventory inv = new Inventory();
        EquipmentManager eq = new EquipmentManager();
        assertNotNull(eq.equip(EquipmentSlot.WEAPON, "warden_silverblade", inv, items, 1));
    }

    @Test
    void allShippedItemsHaveHonestInspectionFields() {
        assertFalse(items.all().isEmpty(), "the shipped catalogue must not be empty");
        for (ItemDef def : items.all()) {
            assertNotNull(def.name());
            assertNotNull(def.description(), def.code() + " must describe itself before any choice");
            assertNotNull(def.lore());
            if (def.isWeapon()) {
                assertTrue(def.weapon().damage() > 0, def.code() + " needs real weapon damage");
                assertNotNull(def.weapon().specialName(), def.code() + " must disclose its special");
            }
            if (def.type() == com.ashenwarden.item.ItemType.CONSUMABLE) {
                assertTrue(def.consumable().hasAny(), def.code() + " must do something when used");
            }
            assertTrue(def.value() >= 0);
        }
    }
}
