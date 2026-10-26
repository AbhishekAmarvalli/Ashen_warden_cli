# ASHEN WARDEN — Architecture Document

Text-only turn-based CLI RPG · Java 21 · Maven · JLine 3 (keyboard UI) · MySQL 8 (JDBC)
Companion to `README.md` (setup) and `docs/COMMAND_REFERENCE.md` (player-facing commands).

---

## 1. High-level module diagram

```
                        ┌──────────────────────────────────────────────┐
                        │                  Main.java                   │
                        │  flags (--plain/--no-color/--width), startup │
                        │  KeyInputs.open(): JLine keys or plain lines │
                        └───────────────────┬──────────────────────────┘
                                            │
                 ┌──────────────────────────▼───────────────────────────┐
                 │                    engine/                           │
                 │  GameEngine ── phase machine (GamePhase)             │
                 │  MainMenuHandler · PlayHandler · MenuHandler         │
                 │  GameState · GameEvent · CommandResult               │
                 └───┬───────────────┬────────────────┬─────────────────┘
                     │               │                │
      ┌──────────────▼──┐   ┌────────▼───────┐  ┌─────▼──────────────────┐
      │    command/      │   │   world/       │  │      combat/           │
      │ CommandParser    │   │ Exploration    │  │ CombatEngine           │
      │ ParsedCommand    │   │ DialogueManager│  │ DamageCalculator (1x)  │
      │ CommandDispatcher│   │ WorldGraph     │  │ StatusInstance/Type    │
      │ (phase policy)   │   │ Location · Npc │  │ SkillCatalog           │
      └──────────────────┘   └────────┬───────┘  └─────┬──────────────────┘
                                      │                │
                 ┌────────────────────▼────────────────▼───────────────────┐
                 │  Domain: player/  item/  enemy/  quest/  story/         │
                 │  Player · Inventory · EquipmentManager · EffectiveStats │
                 │  ItemDef · WeaponSpec · ItemCatalog                     │
                 │  EnemyDef · EnemyBehavior* · EnemyAction                │
                 │  QuestManager · QuestDef/Progress · MemoryDef           │
                 └────────────┬───────────────────────────┬────────────────┘
                              │                           │
        ┌─────────────────────▼──────────┐   ┌────────────▼───────────────┐
        │         content/               │   │       persistence/         │
        │ ContentLoader (Jackson, UTF-8) │   │ Database (Hikari-less JDBC)│
        │ ContentRepository (validation) │   │ MigrationRunner (V*.sql)   │
        │ resources/content/*.json       │   │ SaveService (transactions) │
        └────────────────────────────────┘   │ 6 repositories → MySQL     │
                                             └────────────────────────────┘
        ┌───────────────────────────────────────────────────────────────┐
        │  cli/ — TerminalRenderer · Ansi · ItemText                     │
        │  PURE PRESENTATION: renders CommandResult lines; never writes  │
        │  game state, never touches SQL, never reads stdin.             │
        ├───────────────────────────────────────────────────────────────┤
        │  ui/ — KeyInputs · JLineKeyInput · PlainKeyInput · KeyDecoder │
        │  · InteractiveMenu: raw key reads (JLine) + in-place menu     │
        │  redraw. Degrades to typed commands when no console exists.   │
        └───────────────────────────────────────────────────────────────┘
```

### Request lifecycle (one player input)

```
stdin line
  → CommandParser.parse()            (tokenize; never throws on garbage)
  → CommandDispatcher.dispatch(cmd)  (phase policy: menu vs exploration vs
                                     combat vs submenu — wrong-phase input
                                     is rejected without consuming a turn)
  → service (ExplorationService | CombatEngine | QuestManager |
             DialogueManager | MenuHandler)
       - mutates GameState / Player / CombatState
       - emits GameEvent (quest triggers, autosaves, deaths)
       - returns CommandResult (lines + flags: enterPhase/menu/closeMenu…)
  → GameEngine applies phase transitions, runs autosave policy
  → TerminalRenderer.render(result)  (ANSI or plain)
```

**Invariant:** every failure path (invalid command, unaffordable action,
wrong phase) returns a `CommandResult` with a corrective message **before**
any state mutation — this is what guarantees TEST 04 "invalid input never
spends a turn".

---

## 2. Package boundaries (who may depend on whom)

| Package | Responsibility | May depend on | Must NOT depend on |
|---|---|---|---|
| `cli` | rendering, ANSI, plain fallback | `engine` value types only | `persistence`, services |
| `ui` | keyboard input (JLine raw keys), interactive menus | `cli` renderer, `java.io` | `persistence`, services |
| `command` | parsing + phase policy dispatch | `engine`, services | JDBC |
| `engine` | loop, phases, menus, glue | everything below | — |
| `combat` | turn resolution, damage, statuses | `enemy`, `player`, `item` | `persistence`, `cli` |
| `enemy` | rule tables (`EnemyBehavior`) | `combat` types, `item` | `engine`, `persistence` |
| `world` | graph, exploration, dialogue | `player`, `item`, `quest` | `combat` internals |
| `quest`, `story` | events, flags, memories | `engine` events | `persistence`, `cli` |
| `player`, `item` | stats, inventory, equipment | `common` | everything else |
| `content` | JSON load + startup validation | `item`, `world`, `enemy`, `quest`, `story` | `engine`, `persistence` |
| `persistence` | JDBC, migrations, transactions | domain types (DTO mapping) | `cli`, `command` |
| `common` | exception types | — | — |

All random variation flows through a single **seedable** `Random` owned by
`GameState`; combat formulas live **only** in `DamageCalculator`.

---

## 3. Key design decisions

### 3.1 Data-driven content, validated at startup
Long text and definitions (dialogues, lore, enemy configs, item stats,
locations, quests, memories) live in `src/main/resources/content/*.json`
(parsed by Jackson — no hand-rolled parser). `ContentRepository` validates
on boot: duplicate codes, unknown cross-references, bad triggers ⇒
**startup abort with a friendly error**, never a corrupt save later.
MySQL stores only *mutable save state*; static definitions stay in
version-controlled resources and reference items by stable string IDs.
No Java serialization in SQL.

### 3.2 Persistence & atomicity
- `MigrationRunner` applies `db/migration/V*__*.sql` in order, tracked in
  `schema_version` (Flyway-style convention, dependency-free), so schema
  changes stay compatible with old saves.
- `SaveService.save(slotId)` runs all repository writes in **one JDBC
  transaction** (`setAutoCommit(false)` → commit/rollback). A dropped
  connection rolls everything back and the player is told the save did not
  complete (TEST 15).
- Every mutable row is keyed by `save_id`, so slots are fully isolated
  (TEST 17).
- Without a reachable MySQL the game starts in a degraded mode with
  recovery instructions — no stack trace by default (TEST 02).

### 3.3 Combat determinism
- One round = player action → enemy action, resolved in `CombatEngine`.
- Enemy behavior = priority rule tables implementing `EnemyBehavior`
  (no AI/ML, no network) selected via `BehaviorRegistry`; given the same
  seed the same sequence of actions repeats.
- Enemy **intents are computed first and displayed truthfully**; the tag
  shown (`(Parryable)`, `UNBLOCKABLE`, `(guarding)`) is exactly what the
  enemy executes.
- `DamageCalculator` is the single home of the damage formula
  (attack/defense scaling, defend halving, parry negation, minimum-1 rule).

### 3.4 Inspection-first itemization
`MenuHandler` implements a generic submenu loop
(`inspect <n>` → `compare <n>` → `equip/take/buy <n>` → `back`) used by
inventory, loot, shop and upgrade screens. `back`/`cancel` never costs
resources, and a selection is unreachable without first being inspectable
by the player (the command exists and prints full stats, drawbacks,
requirements and live deltas).

### 3.5 Phase machine
`GamePhase` ∈ { MAIN_MENU, EXPLORATION, COMBAT, SUBMENU, DIALOGUE, LOOT,
DEATH, ENDING }. `CommandDispatcher` is the single gate: a command valid
only in phase X is rejected with a one-liner when the engine is in phase Y
(TEST 03: `attack` in exploration is rejected, state unchanged).

---

## 4. Database — ER relationships

```
save_slots (id PK, slot_name UNIQUE, created_at, updated_at, play_time…)
    │ 1
    │
    ├────1:1──── player_state      (name, hp, max_hp, stamina, level, xp,
    │                               ash_fragments, gold, location_id,
    │                               respawn_location, seed…)
    ├────1:n──── inventory_entries (save_id FK, item_code, qty, …)
    ├────1:n──── equipped_items    (save_id FK, slot, item_code)
    ├────1:n──── discovered_locations (save_id FK, location_code)
    ├────1:n──── unlocked_paths    (save_id FK, path_code)
    ├────1:n──── quest_progress    (save_id FK, quest_code, state, objectives_json)
    ├────1:n──── story_flags       (save_id FK, flag_key, flag_value)
    ├────1:n──── acquired_memories (save_id FK, memory_code, acquired_at)
    └────1:n──── defeated_bosses   (save_id FK, boss_code, defeated_at)
```

- Referenced codes (`item_code`, `location_code`, `quest_code`, `memory_code`)
  are **stable string IDs** shared with the JSON content files.
- All child tables cascade on slot delete; nothing references another slot.
- Migrations: `V1__initial_schema.sql` (10 tables above). Version numbers
  are append-only; never edit an applied migration.

---

## 5. OOP principles in this codebase

| Principle | Where |
|---|---|
| **Abstraction / interfaces** | `EnemyBehavior`, `GameCommand`-level dispatch, repository interfaces behind `SaveService`, `ConsumableEffect` |
| **Polymorphism** | per-enemy behavior classes (`HollowSoldierBehavior`, `GraveCrawlerBehavior`, `BasicMeleeBehavior`) chosen at runtime; command handlers per phase; item effects |
| **Composition** | `Player` *has* `Inventory` + `EquipmentManager`; `Location` *has* exits/interactables/encounters; `GameEngine` *has* services; `CombatState` *has* statuses + log |
| **Inheritance** | shallow only — shared exception base (`GameException` ← `ContentException`, `PersistenceException`); behavior classes share one abstract base |
| **Encapsulation** | domain mutation through service methods; renderer receives immutable `CommandResult` |
| **Exceptions as domains** | `GameException` (player-facing message), `ContentException` (boot failure), `PersistenceException` (save failure → friendly UI text, log for tech detail) |
| **Collections/DSA** | adjacency `Map` for the world graph, `Map` for inventory/quest state, event list for `GameEvent`, `enum` for phases/rarities/slots, ordered list for the combat log |

---

## 6. Threading & I/O model

Single-threaded. One blocking read loop in `PlayHandler`/`MainMenuHandler`;
EOF (`Ctrl+D`/`Ctrl+Z`) exits cleanly instead of spinning. No GUI, no
network sockets, no background timers — the only external system is the
local MySQL connection, opened lazily and pooled minimally in `Database`.

---

## 7. Error-handling policy

| Layer | Failure | Player sees | Log gets |
|---|---|---|---|
| Parse | garbage input | one-line correction | nothing |
| Dispatch | wrong phase / unknown verb | rejection, state untouched | nothing |
| Service | unaffordable / illegal action | why it's illegal | nothing |
| Content | bad JSON / dup ID at boot | "content error at startup: …" | full cause |
| DB | unreachable / dropped mid-save | friendly recovery steps | exception detail |

Logs are for technical errors, concise messages for players (spec §15).

---

## 8. Testing architecture

- Pure-logic unit tests need no DB: `DamageCalculatorTest`,
  `CommandParserTest`, `InventoryAndEquipmentTest`, `CombatEngineTest`.
- `SaveServiceTest` is an **integration suite** that runs only when
  `DB_URL`/`DB_USER`/`DB_PASSWORD` are set — otherwise it reports *skipped*,
  never passed.
- See `docs/TEST_PLAN.md` for the mapping to the 30 acceptance scenarios.
