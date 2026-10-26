package com.ashenwarden.enemy;

import com.ashenwarden.combat.CombatEngine;
import com.ashenwarden.combat.DamageCalculator;
import com.ashenwarden.combat.SkillCatalog;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 elites fight in patterns, not in random order: a telegraphed combo
 * always pays off, a phase shift happens exactly once at its threshold, and
 * defensive cycles are on a fixed rhythm. Also covers the engine-level rule
 * that a self-healing action actually heals (regression for the heal branch).
 */
class TierTwoEliteBehaviorTest {
    private static ContentRepository content;
    private static CombatEngine engine;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        engine = new CombatEngine(content, new BehaviorRegistry(), new SkillCatalog());
    }

    private static EnemyBehavior.Context ctx(int round, boolean defending) {
        return new EnemyBehavior.Context(round, defending, 100, new Random(7));
    }

    // --- Cursed Executioner -------------------------------------------------

    @Test
    void executionerMeasureAlwaysPaysOffWithTheGuillotine() {
        Enemy enemy = new Enemy(content.enemy("cursed_executioner"));
        CursedExecutionerBehavior behavior = new CursedExecutionerBehavior();

        assertEquals("Measured Steps", behavior.choose(enemy, ctx(3, false)).name(),
                "every third round it measures");
        assertEquals("Guillotine", behavior.choose(enemy, ctx(4, false)).name(),
                "the announced execution must land next round — the telegraph is truthful");
        assertNotEquals("Guillotine", behavior.choose(enemy, ctx(5, false)).name(),
                "and never twice without a new measure");
    }

    @Test
    void executionerShiftsPhaseOnceAtHalfHealthAndStopsMeasuring() {
        Enemy enemy = new Enemy(content.enemy("cursed_executioner"));
        CursedExecutionerBehavior behavior = new CursedExecutionerBehavior();
        enemy.takeDamage(enemy.getMaxHp() / 2 + 1);

        assertEquals("The Red Opens", behavior.choose(enemy, ctx(1, false)).name());
        assertEquals(2, enemy.getPhase());
        String second = behavior.choose(enemy, ctx(2, false)).name();
        assertTrue(second.equals("Iron Backhand") || second.equals("Flensing Cleave"),
                "phase 2 mixes fast cuts and bleeds, was: " + second);
        assertNotEquals("The Red Opens", second, "the phase shift announces exactly once");
    }

    @Test
    void executionerPunishesTurtlingWithTheUnparryableBackhand() {
        Enemy enemy = new Enemy(content.enemy("cursed_executioner"));
        CursedExecutionerBehavior behavior = new CursedExecutionerBehavior();
        assertEquals("Iron Backhand", behavior.choose(enemy, ctx(2, true)).name());
    }

    // --- Wraith Priest ------------------------------------------------------

    @Test
    void priestLitanyAlwaysPaysOffWithTheSoulRake() {
        Enemy enemy = new Enemy(content.enemy("wraith_priest"));
        WraithPriestBehavior behavior = new WraithPriestBehavior();
        enemy.putState("litany_intoned", "true");

        assertEquals("Soul Rake", behavior.choose(enemy, ctx(5, false)).name());
        assertNotEquals("Soul Rake", behavior.choose(enemy, ctx(6, false)).name(),
                "the payoff happens once per litany");
    }

    @Test
    void priestOpensTheRiteOnceAtFortyPercentThenWardsOnRhythm() {
        Enemy enemy = new Enemy(content.enemy("wraith_priest"));
        WraithPriestBehavior behavior = new WraithPriestBehavior();
        enemy.takeDamage(enemy.getMaxHp() - enemy.getMaxHp() * 4 / 10 + 1); // at 39%

        assertEquals("Ashen Rite", behavior.choose(enemy, ctx(1, false)).name());
        assertEquals(2, enemy.getPhase());
        String afterRite = behavior.choose(enemy, ctx(2, false)).name();
        assertTrue(afterRite.equals("Soul Rake") || afterRite.equals("Grave Chant"),
                "phase 2 mixes its spells, was: " + afterRite);
        assertEquals("Veil Ward", behavior.choose(enemy, ctx(3, false)).name(),
                "every third round in phase 2 it raises the ward");
    }

    @Test
    void spectralWeaponsCannotTouchThePriest() {
        Enemy priest = new Enemy(content.enemy("wraith_priest"));
        Player player = new Player("Tester");
        EffectiveStats stats = new EffectiveStats(100, 50, 30, 20, 5);
        DamageCalculator calc = new DamageCalculator(new Random(3));

        DamageCalculator.Hit spectral = calc.playerToEnemy(player, stats, 10, 1.0, "spectral", priest);
        DamageCalculator.Hit physical = calc.playerToEnemy(player, stats, 10, 1.0, "physical", priest);
        assertEquals(0, spectral.finalDamage(), "a fully resisted element deals nothing");
        assertTrue(physical.finalDamage() > 0, "plain steel still bites");
    }

    // --- engine regression: self-heals must actually heal --------------------

    @Test
    void aGatherHealActionHealsThroughTheEngine() {
        GameState state = new GameState("Tester", 21L);
        state.setCurrentLocationId("tomb_chamber");
        state.discover("tomb_chamber");
        engine.start(state, "enc_test", List.of("rotting_pilgrim"), true);
        var pilgrim = state.getCombat().firstEnemy();
        pilgrim.setCurrentIntent(pilgrim.def().move("Rot-Mending Prayer"));
        pilgrim.takeDamage(20);
        int hpBefore = pilgrim.getHp();

        // a dodge costs no damage to the enemy: any HP gain is the prayer's
        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.DODGE, null);

        assertTrue(pilgrim.getHp() > hpBefore, "the prayer must mend the pilgrim");
        assertTrue(turn.lines().stream().anyMatch(l -> l.contains("recovers")),
                "the heal must be reported to the player");
    }
}
