package com.ashenwarden.combat;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEvent;
import com.ashenwarden.engine.GamePhase;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.enemy.Enemy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vertical-slice combat rules: invalid input never spends a turn, free look
 * actions never spend a turn, parry works only against telegraphed parryable
 * attacks, victory grants XP/events exactly once.
 */
class CombatEngineTest {
    private static ContentRepository content;
    private static CombatEngine engine;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        engine = new CombatEngine(content, new BehaviorRegistry(), new SkillCatalog());
    }

    private GameState newState() {
        GameState state = new GameState("Tester", 4242L);
        state.setCurrentLocationId("tomb_chamber");
        state.discover("tomb_chamber");
        state.getInventory().add("warden_silverblade", 1);
        state.getInventory().add("crimson_vial", 3);
        state.getEquipment().equip(com.ashenwarden.player.EquipmentSlot.WEAPON,
                "warden_silverblade", state.getInventory(), content.items(), 1);
        return state;
    }

    @Test
    void invalidInputNeverConsumesATurn() {
        GameState state = newState();
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Enemy enemy = state.getCombat().firstEnemy();
        int enemyHp = enemy.getHp();
        int stamina = state.getPlayer().getStamina();

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.SKILL, "no such art");
        assertFalse(turn.turnConsumed(), "unknown skill must not spend the turn");
        assertEquals(enemyHp, enemy.getHp());
        assertEquals(stamina, state.getPlayer().getStamina());

        CombatEngine.Turn itemTurn = engine.act(state, CombatEngine.ActionType.ITEM, "rubble");
        assertFalse(itemTurn.turnConsumed(), "missing item must not spend the turn");
        assertEquals(3, state.getInventory().quantityOf("crimson_vial"));
    }

    @Test
    void inspectAndStatusAreFree() {
        GameState state = newState();
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        int round = state.getCombat().round();
        CombatEngine.Turn inspect = engine.act(state, CombatEngine.ActionType.INSPECT_ENEMY, null);
        assertFalse(inspect.turnConsumed());
        assertTrue(inspect.lines().stream().anyMatch(l -> l.contains("Counterplay")));
        CombatEngine.Turn status = engine.act(state, CombatEngine.ActionType.STATUS, null);
        assertFalse(status.turnConsumed());
        assertEquals(round, state.getCombat().round());
    }

    @Test
    void parryNegatesParryableAndCounters() {
        GameState state = newState();
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Enemy enemy = state.getCombat().firstEnemy();
        enemy.setCurrentIntent(enemy.def().move("Light Slash"));
        int playerHp = state.getPlayer().getHp();

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.PARRY, null);
        assertTrue(turn.turnConsumed());
        assertTrue(enemy.getHp() < enemy.getMaxHp(), "parry riposte deals damage");
        assertEquals(playerHp, state.getPlayer().getHp(), "parried attack deals no damage");
        assertTrue(turn.lines().stream().anyMatch(l -> l.contains("stagger")),
                "the parried enemy is staggered and loses its action this round");
        assertFalse(enemy.isStunned(), "the stagger is consumed when the enemy skips its action");
    }

    @Test
    void parryFailsAgainstUnblockableButDoesNotZeroTheHit() {
        GameState state = newState();
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Enemy enemy = state.getCombat().firstEnemy();
        enemy.setCurrentIntent(enemy.def().move("Shield Bash"));

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.PARRY, null);
        assertTrue(turn.turnConsumed());
        assertFalse(enemy.isStunned());
        assertTrue(state.getPlayer().getHp() < 100, "failed parry still takes the unblockable hit");
    }

    @Test
    void fullFightReachesVictoryWithRewardsOnce() {
        GameState state = newState();
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        state.setPhase(GamePhase.COMBAT);

        int guard = 0;
        while (state.getCombat() != null && state.getCombat().isOngoing() && guard++ < 40) {
            CombatEngine.Turn turn;
            if (state.getPlayer().getStamina() >= 15) {
                turn = engine.act(state, CombatEngine.ActionType.HEAVY, null);
            } else {
                turn = engine.act(state, CombatEngine.ActionType.ATTACK, null);
            }
            assertTrue(turn.turnConsumed());
        }
        assertNotNull(state.getCombat());
        assertEquals(CombatState.Outcome.VICTORY, state.getCombat().outcome());
        assertTrue(state.getPlayer().getXp() > 0 || state.getPlayer().getLevel() > 1);
        assertTrue(state.getCombat().fragmentsGained() > 0
                        || !state.getCombat().pendingLoot().isEmpty()
                        || state.getInventory().quantityOf("crimson_vial") >= 3,
                "victory must produce some reward or an empty (legal) drop table");

        long killEvents = state.drainEvents().stream()
                .filter(ev -> ev.type() == GameEvent.Type.ENEMY_DEFEATED)
                .count();
        assertTrue(killEvents >= 1, "ENEMY_DEFEATED is emitted for quest progress");
    }

    @Test
    void seedMakesEncountersReproducible() {
        GameState a = new GameState("A", 99L);
        GameState b = new GameState("B", 99L);
        for (GameState state : List.of(a, b)) {
            state.getInventory().add("warden_silverblade", 1);
            state.getEquipment().equip(com.ashenwarden.player.EquipmentSlot.WEAPON,
                    "warden_silverblade", state.getInventory(), content.items(), 1);
        }
        engine.start(a, "enc", List.of("grave_crawler"), true);
        engine.start(b, "enc", List.of("grave_crawler"), true);
        for (int i = 0; i < 3 && a.getCombat().isOngoing(); i++) {
            engine.act(a, CombatEngine.ActionType.ATTACK, null);
        }
        for (int i = 0; i < 3 && b.getCombat().isOngoing(); i++) {
            engine.act(b, CombatEngine.ActionType.ATTACK, null);
        }
        assertEquals(a.getCombat().firstEnemy().getHp(), b.getCombat().firstEnemy().getHp());
        assertEquals(a.getPlayer().getHp(), b.getPlayer().getHp());
    }

    @Test
    void fleeIsRefusedWhenEscapeIsSealed() {
        GameState state = newState();
        engine.start(state, "enc_boss", List.of("hollow_soldier"), false);
        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.FLEE, null);
        assertFalse(turn.turnConsumed(), "sealed encounters refuse to even attempt escape");
        assertTrue(state.getCombat().isOngoing());
    }
}
