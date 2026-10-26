package com.ashenwarden.enemy;

import com.ashenwarden.content.ContentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The new Tier-1 rule tables behave exactly as their documented priority
 * lists say: telegraph before payoff, punishment for turtling, a heal that
 * can never become a loop, and recklessness only when the enemy is hurt.
 */
class TierOneBehaviorTest {
    private static ContentRepository content;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
    }

    private static EnemyBehavior.Context ctx(int round, boolean defending, int hpPercent) {
        return new EnemyBehavior.Context(round, defending, hpPercent, new Random(7));
    }

    // --- Ashen Wanderer -----------------------------------------------------

    @Test
    void wandererTelegraphsThenPaysOffTheNextRound() {
        Enemy wanderer = new Enemy(content.enemy("ashen_wanderer"));
        AshenWandererBehavior behavior = new AshenWandererBehavior();

        assertEquals("Gather Ash", behavior.choose(wanderer, ctx(3, false, 100)).name());
        assertEquals("Censer Sweep", behavior.choose(wanderer, ctx(4, false, 100)).name(),
                "the gather must be paid off by the sweep it promised");
        assertEquals("Lurching Strike", behavior.choose(wanderer, ctx(5, false, 100)).name());
    }

    @Test
    void wandererPunishesTurtlingWithASpell() {
        Enemy wanderer = new Enemy(content.enemy("ashen_wanderer"));
        AshenWandererBehavior behavior = new AshenWandererBehavior();
        assertEquals("Cinder Veil", behavior.choose(wanderer, ctx(2, true, 100)).name());
    }

    @Test
    void wandererGrowsRecklessBelowHalfHealth() {
        Enemy wanderer = new Enemy(content.enemy("ashen_wanderer"));
        AshenWandererBehavior behavior = new AshenWandererBehavior();
        wanderer.takeDamage(wanderer.getMaxHp() / 2 + 1); // below half health
        // hurt and not on a gather round: only the reckless options are legal
        for (int round : new int[]{1, 2, 4, 5}) {
            String move = behavior.choose(wanderer, ctx(round, false, 40)).name();
            assertTrue(move.equals("Censer Sweep") || move.equals("Cinder Veil"),
                    "round " + round + " should be reckless, was: " + move);
        }
    }

    // --- Rotting Pilgrim ----------------------------------------------------

    @Test
    void pilgrimPraysWhenHurtAndNeverTwiceInARow() {
        Enemy pilgrim = new Enemy(content.enemy("rotting_pilgrim"));
        RottingPilgrimBehavior behavior = new RottingPilgrimBehavior();
        pilgrim.takeDamage(pilgrim.getMaxHp() / 2 + 1); // below half health

        assertEquals("Rot-Mending Prayer", behavior.choose(pilgrim, ctx(5, false, 40)).name(),
                "a wounded pilgrim kneels to mend");
        assertNotEquals("Rot-Mending Prayer", behavior.choose(pilgrim, ctx(6, false, 40)).name(),
                "the heal must be punishable, never a loop");
        assertNotEquals("Rot-Mending Prayer", behavior.choose(pilgrim, ctx(7, false, 40)).name());
        assertEquals("Rot-Mending Prayer", behavior.choose(pilgrim, ctx(8, false, 40)).name(),
                "after a two-round gap it may pray again");
    }

    @Test
    void healthyPilgrimNeverPraysAndPunishesTurtling() {
        Enemy pilgrim = new Enemy(content.enemy("rotting_pilgrim"));
        RottingPilgrimBehavior behavior = new RottingPilgrimBehavior();

        assertNotEquals("Rot-Mending Prayer", behavior.choose(pilgrim, ctx(1, false, 100)).name());
        assertEquals("Reliquary Breath", behavior.choose(pilgrim, ctx(2, true, 100)).name());
        // every 4th round pressure comes even without a turtle
        assertEquals("Reliquary Breath", behavior.choose(pilgrim, ctx(4, false, 100)).name());
        String plain = behavior.choose(pilgrim, ctx(5, false, 100)).name();
        assertTrue(plain.equals("Rot-Touched Grasp") || plain.equals("Pilgrim's Staff"),
                "otherwise it attacks, was: " + plain);
    }

    // --- registration -------------------------------------------------------

    @Test
    void bothBehaviorsAreRegisteredForContentValidation() {
        BehaviorRegistry registry = new BehaviorRegistry();
        assertTrue(registry.contains("ashen_wanderer"));
        assertTrue(registry.contains("rotting_pilgrim"));
        assertEquals(4, content.enemy("rotting_pilgrim").moves().size());
    }
}
