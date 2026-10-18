package com.ashenwarden.persistence;

import com.ashenwarden.common.PersistenceException;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.player.EquipmentManager;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.quest.QuestProgress;
import com.ashenwarden.world.Location;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates complete save/load transactions across repositories.
 * Game state is authoritative during play; repositories persist at save events.
 * A save commits only when every operation succeeded — otherwise rollback and a
 * truthful "save did not complete" message (never a false success).
 */
public final class SaveService {
    public static final int MAX_SLOT_NAME = 40;

    private final Database database;
    private final ContentRepository content;
    private final SaveRepository slots = new SaveRepository();
    private final PlayerRepository players = new PlayerRepository();
    private final InventoryRepository inventories = new InventoryRepository();
    private final EquipmentRepository equipments = new EquipmentRepository();
    private final StoryRepository stories = new StoryRepository();

    public SaveService(Database database, ContentRepository content) {
        this.database = database;
        this.content = content;
    }

    public boolean isAvailable() {
        return database.isAvailable();
    }

    public String guidance() {
        return database.recoveryGuidance();
    }

    /** Applies pending migrations; safe to call on every launch. */
    public int initializeSchema() {
        try (Connection c = database.open()) {
            return MigrationRunner.run(c);
        } catch (PersistenceException e) {
            throw e;
        } catch (SQLException e) {
            throw new PersistenceException("Could not initialize database schema: " + e.getMessage(), e);
        }
    }

    public List<SaveSlotInfo> listSlots() {
        try (Connection c = database.open()) {
            return slots.listSlots(c);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read save slots: " + e.getMessage(), e);
        }
    }

    public void deleteSlot(int saveId) {
        try (Connection c = database.open()) {
            c.setAutoCommit(false);
            try {
                slots.deleteSlot(c, saveId);
                c.commit();
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw new PersistenceException("Could not delete the slot: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not delete the slot: " + e.getMessage(), e);
        }
    }

    public record SaveResult(int saveId, String slotName, boolean created) {
    }

    /**
     * Atomic save: upsert slot metadata + player state + inventory + equipment +
     * world/story/quest/memory/boss progress in ONE transaction. On any failure
     * everything rolls back and state keeps its previous saveId.
     */
    public SaveResult save(GameState state, String requestedSlotName) {
        if (state == null || state.getPlayer() == null) {
            throw new PersistenceException("There is no game state to save.");
        }
        String slotName = state.getSlotName() != null ? state.getSlotName() : requestedSlotName;
        if (slotName == null || slotName.isBlank()) {
            throw new PersistenceException("A save slot needs a name (1-" + MAX_SLOT_NAME + " characters).");
        }
        slotName = slotName.trim();
        if (slotName.length() > MAX_SLOT_NAME) {
            throw new PersistenceException("Slot name is too long (max " + MAX_SLOT_NAME + " characters).");
        }
        if (state.getInventory().snapshot().keySet().stream().anyMatch(code -> !content.items().contains(code))) {
            throw new PersistenceException("Refusing to save: inventory holds an unknown item code.");
        }

        boolean created = !state.isPersisted();
        try (Connection c = database.open()) {
            c.setAutoCommit(false);
            int saveId = state.getSaveId();
            try {
                if (saveId > 0) {
                    if (slots.slotNameTaken(c, saveId, slotName)) {
                        throw new PersistenceException("A different save already uses the slot name '" + slotName + "'.");
                    }
                    slots.updateSlot(c, saveId, state.getPlayer().getName(), state.getChapter(),
                            state.getCurrentLocationId(), state.getSanctuaryId(), GameState.GAME_VERSION);
                } else {
                    saveId = slots.insertSlot(c, slotName, state.getPlayer().getName(), state.getChapter(),
                            state.getCurrentLocationId(), state.getSanctuaryId(), GameState.GAME_VERSION);
                }

                players.upsert(c, saveId, state);
                inventories.replaceAll(c, saveId, state.getInventory().snapshot());

                Map<String, String> equipped = new java.util.LinkedHashMap<>();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    String code = state.getEquipment().equippedCode(slot);
                    if (code != null) {
                        equipped.put(slot.dbKey(), code);
                    }
                }
                equipments.replaceAll(c, saveId, equipped);

                Set<String> paths = new LinkedHashSet<>(state.getUnlockedAbilities());
                if (state.getEquipment().isAccessory2Unlocked()) {
                    paths.add("accessory2");
                }
                stories.replacePaths(c, saveId, paths);
                stories.replaceLocations(c, saveId, state.getDiscoveredLocations());
                stories.replaceFlags(c, saveId, state.getStoryFlags());
                stories.replaceQuests(c, saveId, state.getQuests());
                stories.replaceMemories(c, saveId, state.getMemories());
                stories.replaceBosses(c, saveId, state.getDefeatedBosses());

                c.commit(); // success message may only be shown after this line
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw friendly(e, slotName);
            } catch (PersistenceException e) {
                rollbackQuietly(c);
                throw e;
            }
            state.setSaveId(saveId);
            state.setSlotName(slotName);
            state.clearDirty();
            return new SaveResult(saveId, slotName, created);
        } catch (PersistenceException e) {
            throw e;
        } catch (SQLException e) {
            throw new PersistenceException("Save did not complete: " + e.getMessage()
                    + ". Your game state is unchanged — try 'save' again.", e);
        }
    }

    private PersistenceException friendly(SQLException e, String slotName) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("Duplicate entry") && msg.contains("slot_name")) {
            return new PersistenceException("A save slot named '" + slotName + "' already exists.", e);
        }
        return new PersistenceException("Save did not complete and was rolled back: " + msg
                + ". Your progress in memory is unchanged — try 'save' again.", e);
    }

    /** Result of a load: the rebuilt session plus human-readable notes. */
    public record LoadedGame(GameState state, List<String> notes) {
    }

    /**
     * Loads a coherent snapshot, validates every referenced code against shipped
     * content and rebuilds derived equipment state. Corrupt/unsupported saves
     * produce a helpful error instead of silent data loss.
     */
    public LoadedGame load(int saveId) {
        try (Connection c = database.open()) {
            SaveSlotInfo info = slots.findSlot(c, saveId);
            if (info == null) {
                throw new PersistenceException("Save slot " + saveId + " no longer exists.");
            }
            long seed = players.loadSeed(c, saveId);
            GameState state = new GameState(info.playerName(), seed);
            players.load(c, saveId, state);
            state.setSaveId(saveId);
            state.setSlotName(info.slotName());
            state.setChapter(info.chapter());

            List<String> notes = new ArrayList<>();
            if (!GameState.GAME_VERSION.equals(info.gameVersion())) {
                notes.add("Note: this save was written by game version " + info.gameVersion()
                        + "; running " + GameState.GAME_VERSION + ".");
            }

            // inventory (codes validated against the shipped catalogue)
            Map<String, Integer> items = inventories.load(c, saveId);
            for (String code : items.keySet()) {
                if (!content.items().contains(code)) {
                    throw new PersistenceException("This save references item '" + code
                            + "', which does not exist in the shipped catalogue (content mismatch).");
                }
            }
            state.getInventory().replaceAll(items);

            // equipment (validated: exists, equipable, right slot)
            Map<String, String> equipped = equipments.load(c, saveId);
            Map<EquipmentSlot, String> slotsMap = new EnumMap<>(EquipmentSlot.class);
            for (Map.Entry<String, String> entry : equipped.entrySet()) {
                EquipmentSlot slot = EquipmentSlot.fromDbKey(entry.getKey());
                ItemDef def = content.items().find(entry.getValue());
                if (slot == null || def == null || !def.isEquipable()
                        || !state.getEquipment().matchesSlot(slot, def)) {
                    throw new PersistenceException("This save has an invalid equipped item '" + entry.getValue()
                            + "' in slot '" + entry.getKey() + "'.");
                }
                slotsMap.put(slot, entry.getValue());
            }
            state.getEquipment().loadSnapshot(slotsMap);

            // abilities / routes
            Set<String> paths = stories.loadPaths(c, saveId);
            state.getUnlockedAbilities().addAll(paths);
            if (paths.contains("accessory2")) {
                state.getEquipment().unlockAccessory2();
            }

            // world progress
            Set<String> locations = stories.loadLocations(c, saveId);
            for (String code : locations) {
                if (content.world().find(code) == null) {
                    throw new PersistenceException("This save references unknown location '" + code + "'.");
                }
                state.getDiscoveredLocations().add(code);
            }
            String current = info.locationCode();
            if (content.world().find(current) == null) {
                throw new PersistenceException("This save is positioned in unknown location '" + current + "'.");
            }
            state.setCurrentLocationId(current);
            String sanctuary = loadSanctuary(c, saveId);
            if (sanctuary != null && content.world().find(sanctuary) != null) {
                state.setSanctuaryId(sanctuary);
            } else {
                state.setSanctuaryId(current);
            }
            state.discover(current);

            // story / quests / memories / bosses
            state.getStoryFlags().putAll(stories.loadFlags(c, saveId));
            Map<String, QuestProgress> quests = stories.loadQuests(c, saveId);
            for (String code : quests.keySet()) {
                if (content.findQuest(code) == null) {
                    throw new PersistenceException("This save references unknown quest '" + code + "'.");
                }
            }
            state.getQuests().putAll(quests);

            Set<String> memories = stories.loadMemories(c, saveId);
            for (String code : memories) {
                if (content.findMemory(code) == null) {
                    throw new PersistenceException("This save references unknown memory '" + code + "'.");
                }
                state.getMemories().add(code);
            }
            state.getDefeatedBosses().addAll(stories.loadBosses(c, saveId));

            state.setPhase(com.ashenwarden.engine.GamePhase.EXPLORATION);
            state.clearDirty();
            return new LoadedGame(state, notes);
        } catch (PersistenceException e) {
            throw e;
        } catch (SQLException e) {
            throw new PersistenceException("Load failed while reading the database: " + e.getMessage()
                    + ". The save was not modified.", e);
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("This save contains a corrupted status value: " + e.getMessage()
                    + ". The save was not modified.", e);
        }
    }

    private String loadSanctuary(Connection c, int saveId) throws SQLException {
        try (var ps = c.prepareStatement("SELECT sanctuary_code FROM save_slots WHERE save_id = ?")) {
            ps.setInt(1, saveId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Convenience for death/respawn and route checks: whether a location exists. */
    public Location location(String code) {
        return content.world().find(code);
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }
}
