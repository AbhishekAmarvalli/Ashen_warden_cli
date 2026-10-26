package com.ashenwarden.combat;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.enemy.Enemy;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dodge rules: costs stamina (never free), never spends a turn it cannot pay
 * for, evades normal attacks probabilistically per seeded roll, applies no
 * status on a successful evasion, cannot evade (UNBLOCKABLE), and the stance
 * lasts exactly one round.
 */
class DodgeTest {
    private static ContentRepository content;
    private static CombatEngine engine;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        engine = new CombatEngine(content, new BehaviorRegistry(), new SkillCatalog());
    }

    private GameState newState(long seed) {
        GameState state = new GameState("Tester", seed);
        state.setCurrentLocationId("tomb_chamber");
        state.discover("tomb_chamber");
        state.getInventory().add("warden_silverblade", 1);
        state.getInventory().add("crimson_vial", 3);
        state.getEquipment().equip(com.ashenwarden.player.EquipmentSlot.WEAPON,
                "warden_silverblade", state.getInventory(), content.items(), 1);
        return state;
    }

    @Test
    void dodgeCostsStaminaAndEndsTheRoundWithRegeneration() {
        GameState state = newState(11L);
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Player player = state.getPlayer();
        int before = player.getStamina();

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.DODGE, null);

        assertTrue(turn.turnConsumed(), "a paid dodge spends the turn");
        // -12 for the stance, +5 end-of-round regeneration
        assertEquals(before - CombatEngine.DODGE_STAMINA_COST + 5, player.getStamina());
        assertFalse(state.getCombat().playerDodgedThisRound(),
                "the stance must be consumed by the round, never linger");
    }

    @Test
    void dodgeWithoutStaminaSpendsNoTurn() {
        GameState state = newState(12L);
        engine.start(state, "enc_test", List.of("hollow_soldier"), true);
        Player player = state.getPlayer();
        player.setStamina(CombatEngine.DODGE_STAMINA_COST - 1);
        int stamina = player.getStamina();
        int hp = player.getHp();
        int enemyHp = state.getCombat().firstEnemy().getHp();

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.DODGE, null);

        assertFalse(turn.turnConsumed(), "an unaffordable dodge must not spend the turn");
        assertEquals(stamina, player.getStamina());
        assertEquals(hp, player.getHp(), "the enemy must not act on a rejected action");
        assertEquals(enemyHp, state.getCombat().firstEnemy().getHp());
    }

    @Test
    void unblockableAttacksCannotBeEvaded() {
        GameState state = newState(13L);
        engine.start(state, "enc_test", List.of("grave_crawler"), true);
        Enemy enemy = state.getCombat().firstEnemy();
        enemy.setCurrentIntent(enemy.def().move("Ambush Bite")); // UNBLOCKABLE
        Player player = state.getPlayer();
        int hp = player.getHp();

        CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.DODGE, null);

        assertTrue(turn.turnConsumed());
        assertTrue(player.getHp() < hp, "an unblockable must land against a dodge");
        assertTrue(turn.lines().stream().anyMatch(l -> l.contains("too committed to evade")),
                "the player must be told why the dodge failed");
    }

    @Test
    void oneStanceStreamShowsBothEvasionAndFailure() {
        GameState state = newState(99L);
        engine.start(state, "enc_test", List.of("grave_crawler"), true);
        Player player = state.getPlayer();
        int evaded = 0;
        int landed = 0;
        // Many rounds from one seeded stream: consecutive Random(seed) first
        // draws are correlated, a real fight's stream is not.
        for (int round = 0; round < 30 && state.getCombat().isOngoing(); round++) {
            // keep each round clean so hp movement stays unambiguous
            for (StatusType type : List.of(StatusType.BLEED, StatusType.POISON, StatusType.BURN)) {
                player.removeStatus(type);
            }
            int hp = player.getHp();
            CombatEngine.Turn turn = engine.act(state, CombatEngine.ActionType.DODGE, null);
            boolean escaped = turn.lines().stream().anyMatch(l -> l.contains("slip past"));
            if (escaped) {
                evaded++;
                assertEquals(hp, player.getHp(), "an evaded strike deals no damage");
                assertTrue(player.getStatuses().isEmpty(), "an evaded strike applies no status");
            } else {
                landed++;
            }
        }
        assertTrue(evaded > 0, "the documented chance must actually dodge some hits");
        assertTrue(landed > 0, "dodge must never become a guaranteed avoid");
    }

    @Test
    void theStanceExpiresAfterOneRound() {
        GameState state = newState(14L);
        engine.start(state, "enc_test", List.of("grave_crawler"), true);
        Player player = state.getPlayer();

        engine.act(state, CombatEngine.ActionType.DODGE, null); // stance round
        int hpAfterDodgeRound = player.getHp();

        engine.act(state, CombatEngine.ActionType.ATTACK, null); // no stance now
        assertTrue(player.getHp() < hpAfterDodgeRound,
                "without a dodge the next round's attack must connect");
    }
}
