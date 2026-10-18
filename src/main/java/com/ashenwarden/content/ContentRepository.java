package com.ashenwarden.content;

import com.ashenwarden.common.ContentException;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.enemy.EnemyDef;
import com.ashenwarden.item.ItemCatalog;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.quest.QuestDef;
import com.ashenwarden.story.MemoryDef;
import com.ashenwarden.world.Location;
import com.ashenwarden.world.Npc;
import com.ashenwarden.world.WorldGraph;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete static game content (items, world, NPCs, enemies, quests,
 * memories) loaded once and cross-validated at startup so duplicated or broken
 * IDs never reach a save file.
 */
public final class ContentRepository {
    private final ItemCatalog items;
    private final WorldGraph world;
    private final Map<String, String> config;
    private final Map<String, EnemyDef> enemies = new LinkedHashMap<>();
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<String, QuestDef> quests = new LinkedHashMap<>();
    private final Map<String, MemoryDef> memories = new LinkedHashMap<>();

    public static ContentRepository loadDefault(BehaviorRegistry behaviors) {
        List<ItemDef> itemList = ContentLoader.list("content/items.json", new TypeReference<>() {
        });
        List<Location> locationList = ContentLoader.list("content/locations.json", new TypeReference<>() {
        });
        List<Npc> npcList = ContentLoader.list("content/npcs.json", new TypeReference<>() {
        });
        List<EnemyDef> enemyList = ContentLoader.list("content/enemies.json", new TypeReference<>() {
        });
        List<QuestDef> questList = ContentLoader.list("content/quests.json", new TypeReference<>() {
        });
        List<MemoryDef> memoryList = ContentLoader.list("content/memories.json", new TypeReference<>() {
        });
        Map<String, String> config = readConfig();

        ContentRepository repo = new ContentRepository(new ItemCatalog(itemList), locationList, config);
        repo.loadNpcs(npcList);
        repo.loadEnemies(enemyList, behaviors);
        repo.loadQuests(questList);
        repo.loadMemories(memoryList);
        repo.validateCrossReferences();
        return repo;
    }

    private static Map<String, String> readConfig() {
        String raw = ContentLoader.text("content/config.properties");
        Map<String, String> config = new java.util.LinkedHashMap<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                config.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        if (!config.containsKey("startLocation")) {
            throw new ContentException("content/config.properties must define startLocation=");
        }
        return config;
    }

    private ContentRepository(ItemCatalog items, List<Location> locations, Map<String, String> config) {
        this.items = items;
        this.config = config;
        this.world = new WorldGraph(locations, config.get("startLocation"));
    }

    // --- new-game configuration ---------------------------------------------
    /** Starting inventory from config: "code:qty,code:qty" (validated against the catalogue). */
    public Map<String, Integer> startInventory() {
        Map<String, Integer> kit = new java.util.LinkedHashMap<>();
        String raw = config.getOrDefault("startInventory", "");
        for (String part : raw.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int colon = entry.indexOf(':');
            String code = colon < 0 ? entry : entry.substring(0, colon).trim();
            int qty = 1;
            if (colon >= 0) {
                try {
                    qty = Integer.parseInt(entry.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    throw new ContentException("Bad startInventory quantity: " + entry);
                }
            }
            if (!items.contains(code)) {
                throw new ContentException("startInventory references unknown item: " + code);
            }
            kit.put(code, qty);
        }
        return kit;
    }

    /** Item code equipped in the armor slot at game start (already worn), or null. */
    public String startArmor() {
        String code = config.get("startArmor");
        if (code == null || code.isBlank()) {
            return null;
        }
        if (!items.contains(code)) {
            throw new ContentException("startArmor references unknown item: " + code);
        }
        return code;
    }

    /** Quest code started automatically at game start, or null. */
    public String startQuest() {
        String code = config.get("startQuest");
        if (code == null || code.isBlank()) {
            return null;
        }
        if (!quests.containsKey(code)) {
            throw new ContentException("startQuest references unknown quest: " + code);
        }
        return code;
    }

    public String startChapter() {
        return config.getOrDefault("startChapter", "Prologue — The Tomb of Cold Ash");
    }

    /** Opening narration shown once when a new game begins. */
    public String prologueIntro() {
        return ContentLoader.text("content/text/prologue.txt");
    }

    private void loadNpcs(List<Npc> list) {
        for (Npc npc : list) {
            if (npc.code() == null || npc.code().isBlank()) {
                throw new ContentException("NPC definition missing code: " + npc.name());
            }
            if (npcs.putIfAbsent(npc.code(), npc) != null) {
                throw new ContentException("Duplicate NPC code: " + npc.code());
            }
            if (npc.node(npc.startNode()) == null) {
                throw new ContentException("NPC '" + npc.code() + "' has unknown start node: " + npc.startNode());
            }
            if (npc.nodes() != null) {
                for (Npc.DialogueNode node : npc.nodes()) {
                    if (node.options() == null) {
                        continue;
                    }
                    for (Npc.DialogueOption option : node.options()) {
                        if (option.nextId() != null && npc.node(option.nextId()) == null) {
                            throw new ContentException("NPC '" + npc.code() + "' option '" + option.label()
                                    + "' points to unknown node: " + option.nextId());
                        }
                    }
                }
            }
        }
    }

    private void loadEnemies(List<EnemyDef> list, BehaviorRegistry behaviors) {
        for (EnemyDef def : list) {
            if (def.code() == null || def.code().isBlank()) {
                throw new ContentException("Enemy definition missing code: " + def.name());
            }
            if (enemies.putIfAbsent(def.code(), def) != null) {
                throw new ContentException("Duplicate enemy code: " + def.code());
            }
            if (!behaviors.contains(def.behaviorCode())) {
                throw new ContentException("Enemy '" + def.code() + "' uses unknown behavior: " + def.behaviorCode());
            }
            if (def.moves() == null || def.moves().isEmpty()) {
                throw new ContentException("Enemy '" + def.code() + "' has no moves.");
            }
            if (def.drops() != null) {
                for (EnemyDef.Drop drop : def.drops()) {
                    if (!items.contains(drop.itemCode())) {
                        throw new ContentException("Enemy '" + def.code() + "' drops unknown item: " + drop.itemCode());
                    }
                }
            }
        }
    }

    private void loadQuests(List<QuestDef> list) {
        for (QuestDef def : list) {
            if (quests.putIfAbsent(def.code(), def) != null) {
                throw new ContentException("Duplicate quest code: " + def.code());
            }
        }
    }

    private void loadMemories(List<MemoryDef> list) {
        for (MemoryDef def : list) {
            if (memories.putIfAbsent(def.code(), def) != null) {
                throw new ContentException("Duplicate memory code: " + def.code());
            }
        }
    }

    /** Validates every cross-reference the game may look up during play. */
    private void validateCrossReferences() {
        for (Location loc : world.all()) {
            if (loc.npcCodes() != null) {
                for (String npcCode : loc.npcCodes()) {
                    if (!npcs.containsKey(npcCode)) {
                        throw new ContentException("Location '" + loc.code() + "' references unknown NPC: " + npcCode);
                    }
                }
            }
            if (loc.encounters() != null) {
                for (Location.Encounter enc : loc.encounters()) {
                    if (!enemies.containsKey(enc.enemyCode())) {
                        throw new ContentException("Encounter '" + enc.code() + "' references unknown enemy: " + enc.enemyCode());
                    }
                    boolean valid = Location.Encounter.TRIGGER_ON_ENTER.equals(enc.trigger())
                            || Location.Encounter.TRIGGER_ON_EXIT.equals(enc.trigger());
                    if (!valid) {
                        throw new ContentException("Encounter '" + enc.code() + "' has invalid trigger: " + enc.trigger());
                    }
                }
            }
            if (loc.interactables() != null) {
                for (Location.Interactable item : loc.interactables()) {
                    if (item.loot() != null) {
                        for (Location.Interactable.Loot loot : item.loot()) {
                            if (!items.contains(loot.itemCode())) {
                                throw new ContentException("Interactable '" + item.code()
                                        + "' contains unknown item: " + loot.itemCode());
                            }
                        }
                    }
                }
            }
        }

        for (QuestDef quest : quests.values()) {
            if (quest.objectives() != null) {
                for (QuestDef.Objective obj : quest.objectives()) {
                    validateObjectiveTarget(quest, obj);
                }
            }
            if (quest.rewards() != null) {
                for (QuestDef.Reward reward : quest.rewards()) {
                    if (reward.itemCode() != null && !items.contains(reward.itemCode())) {
                        throw new ContentException("Quest '" + quest.code() + "' rewards unknown item: " + reward.itemCode());
                    }
                    if (reward.memoryCode() != null && !memories.containsKey(reward.memoryCode())) {
                        throw new ContentException("Quest '" + quest.code() + "' rewards unknown memory: " + reward.memoryCode());
                    }
                }
            }
        }

        if (memories.size() != MemoryDef.TOTAL) {
            throw new ContentException("Expected " + MemoryDef.TOTAL + " Warden memories, found " + memories.size());
        }
    }

    private void validateObjectiveTarget(QuestDef quest, QuestDef.Objective obj) {
        String type = obj.type() == null ? "" : obj.type();
        String target = obj.target();
        boolean ok = switch (type) {
            case "KILL" -> enemies.containsKey(target);
            case "TALK" -> npcs.containsKey(target);
            case "LOCATION" -> world.find(target) != null;
            case "ITEM" -> items.contains(target);
            case "FLAG", "MEMORY", "" -> true;
            default -> false;
        };
        if (!ok) {
            throw new ContentException("Quest '" + quest.code() + "' objective '" + obj.id()
                    + "' has unknown " + type + " target: " + target);
        }
    }

    // --- accessors ---------------------------------------------------------
    public ItemCatalog items() { return items; }
    public WorldGraph world() { return world; }
    public Collection<EnemyDef> enemies() { return enemies.values(); }
    public Collection<Npc> npcs() { return npcs.values(); }
    public Collection<QuestDef> quests() { return quests.values(); }
    public Collection<MemoryDef> memories() { return memories.values(); }

    public ItemDef item(String code) { return items.require(code); }
    public EnemyDef enemy(String code) {
        EnemyDef def = enemies.get(code);
        if (def == null) {
            throw new ContentException("Unknown enemy code: " + code);
        }
        return def;
    }
    public Npc npc(String code) {
        Npc npc = npcs.get(code);
        if (npc == null) {
            throw new ContentException("Unknown NPC code: " + code);
        }
        return npc;
    }
    public QuestDef quest(String code) {
        QuestDef quest = findQuest(code);
        if (quest == null) {
            throw new ContentException("Unknown quest code: " + code);
        }
        return quest;
    }

    public QuestDef findQuest(String code) {
        return code == null ? null : quests.get(code);
    }

    public MemoryDef findMemory(String code) {
        return code == null ? null : memories.get(code);
    }
    public MemoryDef memory(String code) {
        MemoryDef memory = memories.get(code);
        if (memory == null) {
            throw new ContentException("Unknown memory code: " + code);
        }
        return memory;
    }
}
