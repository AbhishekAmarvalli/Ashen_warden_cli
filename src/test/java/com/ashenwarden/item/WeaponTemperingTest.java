package com.ashenwarden.item;

import com.ashenwarden.combat.CombatEngine;
import com.ashenwarden.combat.SkillCatalog;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.enemy.Enemy;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 — weapon tempering and accessories:
 * <ul>
 *   <li>the cost curve is fixed, monotonic and ends at MAX_TIER (no infinite grind),</li>
 *   <li>a tempered weapon really hits harder in the combat engine (same seed,
 *       so the only variable is the temper bonus),</li>
 *   <li>accessory bonuses flow through EffectiveStats like every other bonus.</li>
 * </ul>
 */
class WeaponTemperingTest {
    private static ContentRepository content;
    private static CombatEngine engine;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        engine = new CombatEngine(content, new BehaviorRegistry(), new SkillCatalog());
    }

    // --- pure rules ---------------------------------------------------------

    @Test
    void costCurveRisesAndEndsAtMaxTier() {
        WeaponUpgrader.Cost first = WeaponUpgrader.costForNextTier(0);
        WeaponUpgrader.Cost second = WeaponUpgrader.costForNextTier(1);
        WeaponUpgrader.Cost third = WeaponUpgrader.costForNextTier(2);
        assertEquals(20, first.fragments());
        assertEquals(1, first.steel());
        assertTrue(second.fragments() > first.fragments(), "each tier costs more than the last");
        assertTrue(third.fragments() > second.fragments());
        assertTrue(WeaponUpgrader.isMaxed(WeaponUpgrader.MAX_TIER));
        assertNull(WeaponUpgrader.costForNextTier(WeaponUpgrader.MAX_TIER),
                "a maxed weapon can never be charged again");
        assertNull(WeaponUpgrader.costForNextTier(-1));
    }

    @Test
    void bonusDamageIsFlatPerTierAndNeverNegative() {
        assertEquals(0, WeaponUpgrader.bonusDamage(0));
        assertEquals(WeaponUpgrader.DAMAGE_PER_TIER, WeaponUpgrader.bonusDamage(1));
        assertEquals(WeaponUpgrader.DAMAGE_PER_TIER * WeaponUpgrader.MAX_TIER,
                WeaponUpgrader.bonusDamage(WeaponUpgrader.MAX_TIER));
        assertEquals(0, WeaponUpgrader.bonusDamage(-3), "a bad value can never grant damage");
    }

    @Test
    void gameStateTierSemantics() {
        GameState state = new GameState("Tester", 1L);
        assertEquals(0, state.upgradeLevel("warden_silverblade"));
        state.setUpgradeLevel("warden_silverblade", 2);
        assertEquals(2, state.upgradeLevel("warden_silverblade"));
        state.setUpgradeLevel("warden_silverblade", 0);
        assertEquals(0, state.upgradeLevel("warden_silverblade"), "tier 0 removes the entry");
        assertFalse(state.getUpgradeLevels().containsKey("warden_silverblade"));
        state.setUpgradeLevel(null, 3);
        state.setUpgradeLevel("  ", 3);
        assertEquals(0, state.getUpgradeLevels().size(), "blank codes are ignored");
    }

    // --- tempered weapons in the real combat engine -------------------------

    @Test
    void temperedWeaponHitsHarderWithTheSameSeed() {
        int base = totalAttackDamage(0);
        int tempered = totalAttackDamage(WeaponUpgrader.MAX_TIER);
        assertTrue(base > 0, "the baseline fight must land hits (got " + base + ")");
        assertTrue(tempered > base,
                "a +" + WeaponUpgrader.MAX_TIER + " tempered weapon must out-damage the plain one ("
                        + tempered + " vs " + base + ")");
    }

    /**
     * Five attacks with an identical seeded state, summing raw damage. The enemy
     * is topped back up after every hit so the total is never capped by its HP —
     * the only difference between the two runs is the temper bonus.
     */
    private static int totalAttackDamage(int tier) {
        GameState state = new GameState("Tester", 4242L);
        state.setCurrentLocationId("tomb_chamber");
        state.discover("tomb_chamber");
        state.getInventory().add("warden_silverblade", 1);
        state.getEquipment().equip(EquipmentSlot.WEAPON, "warden_silverblade",
                state.getInventory(), content.items(), 1);
        if (tier > 0) {
            state.setUpgradeLevel("warden_silverblade", tier);
        }

        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Enemy enemy = state.getCombat().firstEnemy();
        int total = 0;
        for (int i = 0; i < 5; i++) {
            int before = enemy.getHp();
            CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.ATTACK, null);
            if (!turn.turnConsumed()) {
                break;
            }
            total += before - enemy.getHp();
            enemy.heal(enemy.getMaxHp()); // keep the target alive so damage stays comparable
        }
        return total;
    }

    // --- accessories --------------------------------------------------------

    @Test
    void newAccessoriesExistAndAreEquippable() {
        for (String code : List.of("oathband", "grave_bead")) {
            ItemDef def = content.items().find(code);
            assertTrue(def != null, code + " must exist in the shipped catalogue");
            assertEquals(ItemType.ACCESSORY, def.type(), code + " must be an accessory");
            assertTrue(def.isEquipable());
        }
    }

    @Test
    void accessoryBonusesFlowIntoEffectiveStats() {
        GameState state = new GameState("Tester", 1L);

        state.getInventory().add("oathband", 1);
        String reason = state.getEquipment().equip(EquipmentSlot.ACCESSORY_1, "oathband",
                state.getInventory(), content.items(), 1);
        assertNull(reason, "the oathband must be wearable");

        EffectiveStats worn = EffectiveStats.compute(state.getPlayer(), state.getEquipment(), content.items());
        assertEquals(Player.BASE_DEFENSE + 2, worn.defense(), "oathband grants +2 defense");
        assertEquals(Player.BASE_MAX_HP + 10, worn.maxHp(), "oathband grants +10 max HP");
        assertEquals(Player.BASE_ATTACK, worn.attack(), "the oathband is defensive only");

        // swapping to the offensive bead changes the profile, never the base stats
        state.getEquipment().unequip(EquipmentSlot.ACCESSORY_1, state.getInventory());
        state.getInventory().add("grave_bead", 1);
        assertNull(state.getEquipment().equip(EquipmentSlot.ACCESSORY_1, "grave_bead",
                state.getInventory(), content.items(), 1));

        EffectiveStats bead = EffectiveStats.compute(state.getPlayer(), state.getEquipment(), content.items());
        assertEquals(Player.BASE_ATTACK + 3, bead.attack());
        assertEquals(Player.BASE_MAX_ASH + 4, bead.maxAsh());
        assertEquals(Player.BASE_DEFENSE, bead.defense());
        assertEquals(Player.BASE_ATTACK, state.getPlayer().getBaseAttack(),
                "equipping must never mutate base stats — only derived ones");
    }

    @Test
    void newItemsAreReachableFromPlacedLoot() {
        // both accessories and the tempering material must be findable in the world
        var locations = content.world().all();
        boolean foundOathband = false;
        boolean foundBead = false;
        boolean foundSteel = false;
        for (var loc : locations) {
            if (loc.interactables() == null) {
                continue;
            }
            for (var target : loc.interactables()) {
                if (target.loot() == null) {
                    continue;
                }
                for (var drop : target.loot()) {
                    foundOathband |= "oathband".equals(drop.itemCode());
                    foundBead |= "grave_bead".equals(drop.itemCode());
                    foundSteel |= "forged_steel".equals(drop.itemCode());
                }
            }
        }
        assertTrue(foundOathband, "Warden's Oathband must be placed somewhere reachable");
        assertTrue(foundBead, "Cantor's Grave Bead must be placed somewhere reachable");
        assertTrue(foundSteel, "Forged Steel must be obtainable for tempering");
    }
}
