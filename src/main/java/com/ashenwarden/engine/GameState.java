package com.ashenwarden.engine;

import com.ashenwarden.combat.CombatState;
import com.ashenwarden.player.EquipmentManager;
import com.ashenwarden.player.Inventory;
import com.ashenwarden.player.Player;
import com.ashenwarden.quest.QuestProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The single authoritative in-memory session snapshot during play. All mutable
 * save-scoped state is keyed here by stable codes; repositories persist exactly
 * these fields, so what you see is what is stored.
 */
public final class GameState {
    public static final String GAME_VERSION = "1.0.0";

    private int saveId = -1;            // -1 = not yet persisted
    private String slotName;
    private String chapter = "Prologue — The Tomb of Cold Ash";

    private final Player player;
    private final Inventory inventory;
    private final EquipmentManager equipment;

    private String currentLocationId;
    private String sanctuaryId;

    private final Set<String> discoveredLocations = new LinkedHashSet<>();
    private final Set<String> unlockedAbilities = new LinkedHashSet<>();
    private final Set<String> defeatedBosses = new LinkedHashSet<>();
    private final Set<String> memories = new LinkedHashSet<>();
    private final Map<String, String> storyFlags = new LinkedHashMap<>();
    private final Map<String, QuestProgress> quests = new LinkedHashMap<>();
    /** Weapon tempering: item code -> upgrade tier (absent = untempered). */
    private final Map<String, Integer> upgradeLevels = new LinkedHashMap<>();

    private GamePhase phase = GamePhase.MAIN_MENU;
    private CombatState combat;

    private final long rngSeed;
    private final Random rng;

    private final List<GameEvent> pendingEvents = new ArrayList<>();
    private boolean dirty;

    public GameState(String playerName, long rngSeed) {
        this.player = new Player(playerName);
        this.inventory = new Inventory();
        this.equipment = new EquipmentManager();
        this.rngSeed = rngSeed;
        this.rng = new Random(rngSeed);
    }

    // --- identity ----------------------------------------------------------
    public int getSaveId() { return saveId; }
    public void setSaveId(int saveId) { this.saveId = saveId; }
    public boolean isPersisted() { return saveId > 0; }
    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    public String getGameVersion() { return GAME_VERSION; }

    // --- actors ------------------------------------------------------------
    public Player getPlayer() { return player; }
    public Inventory getInventory() { return inventory; }
    public EquipmentManager getEquipment() { return equipment; }

    // --- location ----------------------------------------------------------
    public String getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(String id) { this.currentLocationId = id; }
    public String getSanctuaryId() { return sanctuaryId; }
    public void setSanctuaryId(String id) { this.sanctuaryId = id; }

    // --- progress sets -----------------------------------------------------
    public Set<String> getDiscoveredLocations() { return discoveredLocations; }
    public Set<String> getUnlockedAbilities() { return unlockedAbilities; }
    public Set<String> getDefeatedBosses() { return defeatedBosses; }
    public Set<String> getMemories() { return memories; }

    public boolean hasAbility(String code) {
        return code != null && unlockedAbilities.contains(code);
    }

    public boolean discover(String locationCode) {
        boolean added = discoveredLocations.add(locationCode);
        if (added) {
            markDirty();
        }
        return added;
    }

    public boolean unlockAbility(String code) {
        boolean added = unlockedAbilities.add(code);
        if (added) {
            markDirty();
            pendingEvents.add(GameEvent.of(GameEvent.Type.ABILITY_UNLOCKED, code));
        }
        return added;
    }

    public boolean acquireMemory(String memoryCode) {
        boolean added = memories.add(memoryCode);
        if (added) {
            markDirty();
            pendingEvents.add(GameEvent.of(GameEvent.Type.MEMORY_ACQUIRED, memoryCode));
        }
        return added;
    }

    public boolean defeatBoss(String bossCode) {
        boolean added = defeatedBosses.add(bossCode);
        if (added) {
            markDirty();
            pendingEvents.add(GameEvent.of(GameEvent.Type.BOSS_DEFEATED, bossCode));
        }
        return added;
    }

    // --- flags / quests ----------------------------------------------------
    public Map<String, String> getStoryFlags() { return storyFlags; }

    // --- weapon tempering ----------------------------------------------------
    public Map<String, Integer> getUpgradeLevels() { return upgradeLevels; }

    /** Current tempering tier of an item code (0 = untempered). */
    public int upgradeLevel(String itemCode) {
        return upgradeLevels.getOrDefault(itemCode, 0);
    }

    /** Sets a tempering tier; 0 removes the entry so saves stay minimal. */
    public void setUpgradeLevel(String itemCode, int level) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        if (level <= 0) {
            upgradeLevels.remove(itemCode);
        } else {
            upgradeLevels.put(itemCode, Math.min(level, 99));
        }
        markDirty();
    }

    public void setFlag(String code, String value) {
        storyFlags.put(code, value);
        markDirty();
    }

    public String getFlag(String code) {
        return storyFlags.get(code);
    }

    public boolean flagIs(String code, String value) {
        return value != null && value.equals(storyFlags.get(code));
    }

    public Map<String, QuestProgress> getQuests() { return quests; }

    public QuestProgress quest(String code) {
        return quests.get(code);
    }

    public QuestProgress ensureQuest(String code) {
        return quests.computeIfAbsent(code, QuestProgress::new);
    }

    // --- phase / combat ----------------------------------------------------
    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public CombatState getCombat() { return combat; }
    public void setCombat(CombatState combat) { this.combat = combat; }

    // --- randomness (seeded; never Math.random in game code) ---------------
    public Random getRng() { return rng; }
    public long getRngSeed() { return rngSeed; }

    // --- events / dirty tracking -------------------------------------------
    public void addEvent(GameEvent event) { pendingEvents.add(event); }

    public List<GameEvent> drainEvents() {
        List<GameEvent> drained = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    public void markDirty() { dirty = true; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    // --- convenience -------------------------------------------------------
    public List<String> locationIds() {
        return Collections.unmodifiableList(new ArrayList<>(discoveredLocations));
    }
}
