# ASHEN WARDEN — Four-Member Team Roles

Maps each team member to ownership, Java classes, tasks, SQL objects and
their demo segment (spec §11, deliverable 8). Difficulty is descending:
Member 1 carries the hardest responsibility.

**Integration contract:** Member 1 defines all shared interfaces, stable
IDs, phases and events first. Member 2 supplies enemies to combat, Member
3 supplies locations/events/quests, Member 4 supplies player/items/
rendering. No member invents an incompatible model or schema. Branches:
`feature/core-engine`, `feature/enemy-behavior`, `feature/world-story`,
`feature/player-inventory-cli` — PRs reviewed by the leader.

---

## MEMBER 1 — Team Leader (difficulty 10/10)

**Ownership:** architecture, core engine, turn-based combat, command
dispatch, database foundation, save/load transactions, integration,
release quality.

| Area | Classes |
|---|---|
| Engine | `Main`, `GameEngine`, `GameState`, `GamePhase`, `GameEvent`, `CommandResult`, `PlayHandler`, `MainMenuHandler`, `MenuHandler` |
| Commands | `CommandParser`, `ParsedCommand`, `CommandDispatcher` |
| Combat core | `CombatEngine`, `CombatState`, `DamageCalculator`, `SkillCatalog`/`SkillDef`, `StatusInstance`/`StatusType` |
| Persistence | `Database`, `MigrationRunner`, `SaveService`, `SaveSlotInfo`, all 6 repositories |
| Exceptions | `common/GameException`, `ContentException`, `PersistenceException` |
| Tests | `CombatEngineTest`, `SaveServiceTest` (integration), `DamageCalculatorTest` |

**SQL:** `V1__initial_schema.sql` (all 10 tables), `schema_version`
tracking, transaction conventions in `SaveService`.

**Definition of done:** new game → explore → encounter → fight → loot →
quest update → save → exit → reload → ending path, with no data
corruption.

**Demo segment:** steps 1–2 and 9 of the walkthrough (menu + MySQL
persistence), the turn-order/telegraph explanation in step 5, and the
rollback demonstration (TEST 15).

---

## MEMBER 2 — Enemies, Bosses and Effects (8/10)

**Ownership:** 15 enemy definitions, three main bosses, optional bosses,
rule-based behavior, telegraphs, statuses, encounters, enemy/boss
persistence.

| Area | Classes |
|---|---|
| Core | `Enemy`, `EnemyDef`, `EnemyAction`, `EnemyBehavior` (interface) |
| Behaviors | `BasicMeleeBehavior`, `HollowSoldierBehavior`, `GraveCrawlerBehavior`, `BehaviorRegistry`, `Behaviors` |
| Statuses | `StatusType`, `StatusInstance` (stacking/ticking, parryable/unblockable tags) |
| Roadmap | `Boss`/`BossPhase` classes, remaining 13 behaviors, `EncounterFactory` |

**SQL:** `defeated_bosses` table; encounter state support in
`player_state`; content codes in `content/enemies.json`.

**Rules:** explicit priority-based behavior tables only — no machine
learning, no network. Given the seed, behavior reproduces exactly.

**Definition of done:** all required enemies behave distinctly; telegraphs
match actions; boss phases transition once; loot and defeat state survive
save/load.

**Demo segment:** step 5 (telegraph honesty, parry vs. unblockable) and
step 10 (boss phase logic / scripted encounter).

---

## MEMBER 3 — World, Story, NPCs and Quests (7/10)

**Ownership:** explorable world graph, all acts, dialogues, NPCs,
memories, quests, ability-gated paths, three endings.

| Area | Classes |
|---|---|
| World | `WorldGraph`, `Location`, `ExplorationService` |
| NPCs | `Npc`, `DialogueManager` (topics, choices, choice flags) |
| Quests | `QuestManager`, `QuestDef`, `QuestProgress` (event-driven, reward-once) |
| Story | `MemoryDef` + memories tracker (7 defs shipped), roadmap: `StoryManager`, `EndingResolver` |
| Roadmap | remaining 5 regions, 6 NPCs, ability gates, 3 endings |

**SQL:** `discovered_locations`, `unlocked_paths`, `quest_progress`,
`story_flags`, `acquired_memories`. Content in `content/locations.json`,
`npcs.json`, `quests.json`, `memories.json`.

**Rules:** stable string IDs everywhere; no route may soft-lock; memory
collection is once-only; endings gated centrally.

**Definition of done:** all six regions reachable in order, optional
routes work, seven memories collect once, endings correctly gated.

**Demo segment:** steps 3 (tomb exploration + Lantern Keeper), 8 (quest,
memories tracker, map), 11 (locked route + ability), 12 (ending
requirements via a dedicated test save).

---

## MEMBER 4 — Player, Items, Equipment and CLI (6/10)

**Ownership:** player stats/progression, inventory, item definitions,
equipment, merchant/upgrade UI, terminal rendering, player/inventory
repositories.

| Area | Classes |
|---|---|
| Player | `Player`, `EffectiveStats`, `Inventory`, `EquipmentManager`, `EquipmentSlot` |
| Items | `ItemDef`, `ItemType`, `Rarity`, `WeaponSpec`, `ItemCatalog`, `ConsumableEffect` |
| CLI | `TerminalRenderer`, `Ansi`, `ItemText` (inspection/comparison text) |
| Content | `content/items.json` (9 shipped items), `config.properties` start kit |
| Tests | `InventoryAndEquipmentTest`, `CommandParserTest` |

**SQL:** `player_state`, `inventory_entries`, `equipped_items` (via
`PlayerRepository`, `InventoryRepository`, `EquipmentRepository`).

**Rules:** inspection-first detail/compare/back in **every**
item-selection screen; equipment modifies derived stats exactly once;
`--plain`/`NO_COLOR` mode must stay readable; saved inventory reloads
identically.

**Definition of done:** every item inspectable, no stat double-stacking,
CLI usable on Windows PowerShell, inventory round-trips through MySQL.

**Demo segment:** steps 4 (inspect before equip), 6 (inspect loot before
taking), 7 (compare two weapons).

---

## Shared responsibilities

- **All members:** contribute Java + JDBC code, write tests for their
  module, keep documentation current, run `mvn test` before every merge.
- **Leader reviews all PRs** and owns interface mismatches.
- **Weekly integration** starting week 1 — not the final week.
- **No AI/LLM integration, telemetry, ads or accounts** anywhere (spec §15).
