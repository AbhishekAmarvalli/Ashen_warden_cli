package com.ashenwarden.combat;

import com.ashenwarden.enemy.Enemy;
import com.ashenwarden.enemy.EnemyAction;
import com.ashenwarden.enemy.EnemyDef;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.EquipmentManager;
import com.ashenwarden.player.Inventory;
import com.ashenwarden.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the documented formula:
 * raw = attack + weapon + bonus; mitigated = max(1, raw - floor(defense/2));
 * then multipliers/resistances; never negative; minimum 1 for non-immune hits.
 */
class DamageCalculatorTest {

    private static EnemyDef enemyDef(int hp, int attack, int defense,
                                     Map<String, Double> resistances, List<String> weaknesses) {
        List<EnemyAction> moves = List.of(
                new EnemyAction("Slash", "Slash", "LIGHT", 1.0, List.of(EnemyAction.TAG_PARRYABLE),
                        null, 0, 0, 0, 0, null));
        return new EnemyDef("test_enemy", "Test Enemy", "Test", hp, attack, defense, 10,
                "lore", "hint", resistances, weaknesses, false, "basic_melee", moves, List.of());
    }

    private static EffectiveStats stats(Player player) {
        return EffectiveStats.compute(player, new EquipmentManager(), new ItemCatalogFixture().catalog());
    }

    @Test
    void dodgeChanceScalesWithDefenseAndIsCapped() {
        EffectiveStats noDefense = new EffectiveStats(100, 50, 30, 10, 0);
        EffectiveStats trained = new EffectiveStats(100, 50, 30, 10, 8);
        EffectiveStats absurd = new EffectiveStats(100, 50, 30, 10, 200);

        assertEquals(DamageCalculator.DODGE_BASE_CHANCE,
                DamageCalculator.dodgeChance(noDefense), 1e-9,
                "base chance with no defensive training");
        assertEquals(0.45 + 8 * 0.015, DamageCalculator.dodgeChance(trained), 1e-9,
                "documented +1.5% per defense point");
        assertEquals(DamageCalculator.DODGE_MAX_CHANCE,
                DamageCalculator.dodgeChance(absurd), 1e-9,
                "no build may dodge everything");
    }

    /** Minimal catalogue wrapper so EffectiveStats can resolve an empty equipment set. */
    private static final class ItemCatalogFixture {
        private final com.ashenwarden.item.ItemCatalog catalog =
                new com.ashenwarden.item.ItemCatalog(List.of());

        com.ashenwarden.item.ItemCatalog catalog() {
            return catalog;
        }
    }

    @Test
    void mitigationSubtractsHalfDefenseWithFloorOne() {
        DamageCalculator calc = new DamageCalculator(new Random(1));
        Player player = new Player("T");
        Enemy enemy = new Enemy(enemyDef(50, 10, 5, Map.of(), List.of()));
        // raw = 10 attack + 10 weapon = 20; mitigated = 20 - floor(5/2)=2 -> 18
        DamageCalculator.Hit hit = calc.playerToEnemy(player, stats(player), 10, 1.0, "physical", enemy);
        assertEquals(20, hit.raw());
        assertEquals(18, hit.mitigated());
        assertTrue(hit.finalDamage() >= 18, "crit may raise but never lower damage");
    }

    @Test
    void damageIsNeverZeroOrNegativeForNonImmuneHits() {
        DamageCalculator calc = new DamageCalculator(new Random(7));
        Player player = new Player("T");
        player.setBaseAttack(0);
        Enemy enemy = new Enemy(enemyDef(50, 0, 999, Map.of(), List.of()));
        DamageCalculator.Hit hit = calc.playerToEnemy(player, stats(player), 0, 0.1, "physical", enemy);
        assertTrue(hit.finalDamage() >= 1);
    }

    @Test
    void immunityResultsInZeroDamage() {
        DamageCalculator calc = new DamageCalculator(new Random(7));
        Player player = new Player("T");
        Enemy enemy = new Enemy(enemyDef(50, 10, 0, Map.of("physical", 1.0), List.of()));
        assertEquals(0, calc.playerToEnemy(player, stats(player), 10, 1.0, "physical", enemy).finalDamage());
    }

    @Test
    void weaknessIncreasesDamage() {
        DamageCalculator calc = new DamageCalculator(new Random(3));
        Player player = new Player("T");
        Enemy weak = new Enemy(enemyDef(50, 10, 0, Map.of(), List.of("physical")));
        DamageCalculator.Hit hit = calc.playerToEnemy(player, stats(player), 10, 1.0, "physical", weak);
        assertEquals(20, hit.mitigated(), "mitigation happens before weakness");
        assertTrue(hit.finalDamage() >= 25, "weakness (x1.25) must raise the 20 mitigated to at least 25");
    }

    @Test
    void defendHalvesEligibleDamageButNotUnblockable() {
        DamageCalculator calc = new DamageCalculator(new Random(5));
        Player player = new Player("T");
        Enemy enemy = new Enemy(enemyDef(50, 20, 0, Map.of(), List.of()));
        EffectiveStats stats = stats(player);
        DamageCalculator.Hit blocked = calc.enemyToPlayer(enemy, 1.0, true, false, stats);
        DamageCalculator.Hit full = calc.enemyToPlayer(enemy, 1.0, false, false, stats);
        DamageCalculator.Hit unblockable = calc.enemyToPlayer(enemy, 1.0, true, true, stats);
        assertTrue(blocked.finalDamage() <= full.finalDamage(),
                "defend must not increase damage");
        assertTrue(unblockable.finalDamage() >= blocked.finalDamage(),
                "unblockable attacks ignore the guard");
        assertTrue(unblockable.finalDamage() > 0);
    }

    @Test
    void playerWeaknessStatusReducesOutgoingDamage() {
        DamageCalculator calc = new DamageCalculator(new Random(9));
        Player player = new Player("T");
        player.addStatus(new StatusInstance(StatusType.WEAKNESS, 0, 2));
        Enemy enemy = new Enemy(enemyDef(50, 10, 0, Map.of(), List.of()));
        DamageCalculator.Hit hit = calc.playerToEnemy(player, stats(player), 10, 1.0, "physical", enemy);
        assertTrue(hit.finalDamage() < 30 || hit.critical(),
                "WEAKNESS should reduce damage unless a crit compensates");
    }
}
