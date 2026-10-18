package com.ashenwarden.persistence;

import com.ashenwarden.common.PersistenceException;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GamePhase;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.quest.QuestProgress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JDBC integration tests against the local MySQL service (DB_URL/DB_USER/
 * DB_PASSWORD). Skipped — clearly reported, never faked — when MySQL is down.
 */
class SaveServiceTest {
    private static ContentRepository content;
    private static SaveService saves;

    @BeforeAll
    static void setUp() {
        content = ContentRepository.loadDefault(new BehaviorRegistry());
        Database db = Database.fromEnv();
        assumeTrue(db.isAvailable(), "MySQL unavailable — DB integration tests SKIPPED (not passed)");
        saves = new SaveService(db, content);
        saves.initializeSchema();
    }

    private GameState sampleState(String slot) {
        GameState state = new GameState("Tester", 777L);
        state.setCurrentLocationId("tomb_passage");
        state.discover("tomb_chamber");
        state.discover("tomb_passage");
        state.getInventory().add("warden_silverblade", 1);
        state.getInventory().add("crimson_vial", 2);
        state.getEquipment().equip(EquipmentSlot.WEAPON, "warden_silverblade",
                state.getInventory(), content.items(), 1);
        state.getInventory().add("wraith_dagger", 1);
        state.getEquipment().equip(EquipmentSlot.WEAPON, "wraith_dagger",
                state.getInventory(), content.items(), 1);
        state.unlockAbility("bellbreaker");
        state.setFlag("keeper_testimony", "true");
        state.setFlag("equipped_weapon", "true");
        QuestProgress quest = state.ensureQuest("quest_first_steps");
        quest.setStatus(QuestProgress.Status.ACTIVE);
        quest.markObjective("read_journal");
        quest.getCounters().put("kill_soldier", 1);
        state.getPlayer().setFragments(1234);
        state.getPlayer().setHp(57);
        state.setPhase(GamePhase.EXPLORATION);
        return state;
    }

    @Test
    void roundTripPreservesEverythingThatMatters() {
        GameState original = sampleState("save-" + UUID.randomUUID());
        String slotName = "rt-" + UUID.randomUUID().toString().substring(0, 8);
        SaveService.SaveResult result = saves.save(original, slotName);
        assertTrue(result.saveId() > 0);
        assertEquals(original.getSaveId(), result.saveId());

        GameState loaded = saves.load(result.saveId()).state();
        assertEquals("Tester", loaded.getPlayer().getName());
        assertEquals(57, loaded.getPlayer().getHp());
        assertEquals(1234, loaded.getPlayer().getFragments());
        assertEquals("tomb_passage", loaded.getCurrentLocationId());
        assertEquals("wraith_dagger", loaded.getEquipment().equippedCode(EquipmentSlot.WEAPON));
        assertEquals(1, loaded.getInventory().quantityOf("warden_silverblade"),
                "the swapped-out sword stays in the bag");
        assertEquals(2, loaded.getInventory().quantityOf("crimson_vial"));
        assertTrue(loaded.hasAbility("bellbreaker"));
        assertEquals("true", loaded.getFlag("keeper_testimony"));
        assertTrue(loaded.getDiscoveredLocations().contains("tomb_chamber"));
        QuestProgress quest = loaded.quest("quest_first_steps");
        assertNotNull(quest);
        assertTrue(quest.isActive());
        assertTrue(quest.objectiveDone("read_journal"));
        assertEquals(1, quest.counter("kill_soldier"));
        assertEquals(GamePhase.EXPLORATION, loaded.getPhase());
        saves.deleteSlot(result.saveId());
    }

    @Test
    void slotsAreIndependent() {
        GameState first = sampleState("a");
        GameState second = sampleState("b");
        first.getPlayer().setFragments(111);
        second.getPlayer().setFragments(999);
        SaveService.SaveResult r1 = saves.save(first, "iso-1-" + UUID.randomUUID().toString().substring(0, 8));
        SaveService.SaveResult r2 = saves.save(second, "iso-2-" + UUID.randomUUID().toString().substring(0, 8));

        assertEquals(111, saves.load(r1.saveId()).state().getPlayer().getFragments());
        assertEquals(999, saves.load(r2.saveId()).state().getPlayer().getFragments());

        GameState loadedFirst = saves.load(r1.saveId()).state();
        loadedFirst.getPlayer().setFragments(5);
        saves.save(loadedFirst, null);
        assertEquals(999, saves.load(r2.saveId()).state().getPlayer().getFragments(),
                "changing one slot must never touch another");
        saves.deleteSlot(r1.saveId());
        saves.deleteSlot(r2.saveId());
    }

    @Test
    void duplicateSlotNameRollsBackAndReports() {
        String slotName = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        GameState one = sampleState("one");
        SaveService.SaveResult first = saves.save(one, slotName);

        GameState two = sampleState("two");
        two.getPlayer().setFragments(777777);
        assertThrows(PersistenceException.class, () -> saves.save(two, slotName),
                "duplicate slot names must be refused");
        // the original slot is untouched and the new state was never half-written
        assertEquals(one.getPlayer().getFragments(),
                saves.load(first.saveId()).state().getPlayer().getFragments());
        saves.deleteSlot(first.saveId());
    }

    @Test
    void invalidSaveIsRejectedBeforeHittingTheDatabase() {
        GameState state = sampleState("x");
        state.getInventory().add("nonexistent_item_code", 1);
        assertThrows(PersistenceException.class, () -> saves.save(state, "bad-" + UUID.randomUUID().toString().substring(0, 8)));
    }

    @Test
    void bossesAndMemoriesSurviveReload() {
        GameState state = sampleState("m");
        state.defeatBoss("hollow_soldier");
        state.acquireMemory("memory_oath");
        SaveService.SaveResult result = saves.save(state, "mem-" + UUID.randomUUID().toString().substring(0, 8));
        GameState loaded = saves.load(result.saveId()).state();
        assertTrue(loaded.getDefeatedBosses().contains("hollow_soldier"));
        assertEquals(1, loaded.getMemories().size());
        assertTrue(loaded.getMemories().contains("memory_oath"));
        saves.deleteSlot(result.saveId());
    }

    @Test
    void corruptSaveIsReportedNotSilentlyReset() {
        GameState state = sampleState("c");
        SaveService.SaveResult result = saves.save(state, "cor-" + UUID.randomUUID().toString().substring(0, 8));
        // simulate content mismatch by pointing the save at an unknown location
        try (var c = Database.fromEnv().open();
             var ps = c.prepareStatement("UPDATE save_slots SET location_code = 'nowhere_at_all' WHERE save_id = ?")) {
            ps.setInt(1, result.saveId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> saves.load(result.saveId()));
        assertTrue(thrown.getMessage().contains("nowhere_at_all"),
                "the error must name the problem, not wipe the save");
        saves.deleteSlot(result.saveId());
    }

    @Test
    void listSlotsShowsWhatExists() {
        String slotName = "ls-" + UUID.randomUUID().toString().substring(0, 8);
        SaveService.SaveResult result = saves.save(sampleState("l"), slotName);
        List<SaveSlotInfo> slots = saves.listSlots();
        assertTrue(slots.stream().anyMatch(s -> s.slotName().equals(slotName)));
        saves.deleteSlot(result.saveId());
    }
}
