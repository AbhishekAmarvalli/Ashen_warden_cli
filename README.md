# ASHEN WARDEN: THE LAST EMBER

A kingdom that could not die. An oath that could not end.

A **text-only, turn-based gothic dark-fantasy RPG** for the terminal. Java 17+,
Maven, MySQL 8+ via JDBC. No GUI, no AI, no network at runtime — enemy behavior is
strictly rule-based with seeded, reproducible variation.

> Status: **Phase 1 vertical slice (playable end to end).**
> The prologue (Warden's Tomb), the Forgotten Outskirts hub and the road to the
> Cathedral are implemented: exploration, inspect-first itemization, two enemies,
> full turn combat with telegraphs/parry/defend/status effects, a quest, an NPC
> dialogue tree, sanctuaries, death with recoverable fragments, MySQL save slots
> (atomic, isolated) and autosave. Act I-V content (Bell Keeper boss, six regions,
> seven memories, three endings) is planned — see Roadmap.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK         | 17+ (tested on 25) | `java -version` |
| Maven       | 3.9+     | `mvn -version` |
| MySQL       | 8.0+     | local service, e.g. `net start MySQL80` on Windows |

## Database setup (once)

```sql
CREATE DATABASE ashen_warden CHARACTER SET utf8mb4;
-- optional dedicated user (recommended):
CREATE USER 'ashen'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON ashen_warden.* TO 'ashen'@'localhost';
```

Schema migrations under `src/main/resources/db/migration/` apply automatically
on first launch (tracked in `schema_version`).

## Configuration (environment variables)

| Variable | Default | Meaning |
|----------|---------|---------|
| `DB_URL` | `jdbc:mysql://localhost:3306/ashen_warden?...` | JDBC URL |
| `DB_USER` | `root` | database user |
| `DB_PASSWORD` | *(none)* | **never committed — set it yourself** |

No credentials are stored in the repo or logged. Without a reachable MySQL the
game still starts and tells you exactly how to fix it; saving is disabled until
it is back.

## Run

```bash
# Windows (PowerShell)
$env:DB_USER="ashen"; $env:DB_PASSWORD="your-password"
mvn package
java -jar target/ashen-warden.jar

# Linux / macOS
export DB_USER=ashen DB_PASSWORD=your-password
mvn package
java -jar target/ashen-warden.jar
```

Flags: `--plain` (ASCII + no colors, screen-reader friendly), `--no-color`,
`--width <40-120>`, `--help`. `NO_COLOR` is honored.

## Tests

```bash
mvn test          # unit tests always; MySQL integration tests run when DB_* is set
```

If MySQL is down, DB integration tests are **skipped and reported as skipped** —
never counted as passing.

---

## Command reference

**Main menu:** `1` New Game · `2` Continue · `3` Load Slot · `4` Settings · `5` Exit

**Exploration:** `look` · `go <way>` (or an exit number) · `map` · `paths` ·
`inspect <thing>` · `interact <thing>` · `search` · `talk <who>` · `ask <topic>` ·
`choose <n>` · `leave` · `rest` · `journal` · `quests` · `memories` · `abilities` ·
`inventory` · `equipment` · `stats` · `save` · `load` · `help` · `quit`

**Combat:** `attack` · `heavy` (15 stamina, 160%, staggers) · `defend` (halves
damage, +15 stamina) · `parry` (negates a *(Parryable)* telegraph and counters;
fails against spells/grabs) · `skill <name>` · `item <name>` · `inspect enemy`
· `inspect item <name>` · `status` · `log` · `flee` (never in sealed fights)

**Menus:** numbers, `inspect <n>`, `compare <n>`, `equip <n>`, `unequip <slot>`,
`take <n>`, `take all`, `back`/`cancel`.

Rules you can rely on:

- **Inspection-first:** every item choice (loot, equip, shop, upgrades) shows
  full details, stats, drawbacks, requirements and a live comparison *before*
  you commit. `back` always costs nothing.
- **Invalid input is always safe:** unknown commands or unaffordable actions
  never spend a combat turn and never change state.
- **Telegraphs are truthful:** the intent shown at the start of a round is the
  action the enemy performs. Parryable attacks say so; unblockable attacks say so.
- **Save policy:** manual `save` outside combat (named slots, atomic commit),
  autosave after victories, rests, deaths and quest completions.
- **Death:** you wake at your last sanctuary; a fraction of your Ash Fragments
  drops where you died and can be recovered once with `search`. Boss wins,
  opened routes, memories and completed quests are never lost.

---

## Architecture

```
CLI input -> CommandParser -> CommandDispatcher (phase policy)
        -> GameEngine (phase machine, menus, rendering glue)
            -> ExplorationService / CombatEngine / QuestManager / DialogueManager
            -> Domain: GameState, Player, Enemy, Location, ItemDef
            -> SaveService -> repositories -> JDBC -> MySQL
TerminalRenderer <- command lines (pure presentation; no SQL, no state writes)
```

- `engine/` game loop, phases, menus, death, save/load sequencing
- `command/` typed parsing + phase-aware dispatch
- `combat/` turn resolution, the single DamageCalculator, status effects, skills
- `enemy/` rule tables (`EnemyBehavior`) — deterministic, seeded, no AI/ML
- `world/` world graph, ability gates, encounters, NPC dialogue
- `quest/`, `story/` event-driven quests, memories, flags
- `item/`, `player/` catalogue, inventory, equipment, derived stats
- `cli/` renderer, ANSI with plain fallback, inspection text
- `persistence/` Database, MigrationRunner, SaveService + repositories
- `content/` UTF-8 JSON content loaded with Jackson and validated at startup
  (duplicate IDs, unknown references and bad triggers abort startup, never saves)

### Database ER summary

`save_slots (1) —< player_state (1:1)`, `—< inventory_entries`, `—< equipped_items`,
`—< discovered_locations`, `—< unlocked_paths`, `—< quest_progress`,
`—< story_flags`, `—< acquired_memories`, `—< defeated_bosses`.
All mutable state is keyed by `save_id`, so slots are fully independent.

---

## Roadmap (MUST / SHOULD / COULD)

- **MUST (done):** game loop, turn-based combat, inspect-first itemization,
  JDBC atomic save/load with named slots, prologue slice, one quest, one NPC.
- **MUST (next):** Bell Keeper boss + Bellbreaker gate, Act I-V regions,
  seven memories with triggers, three endings via a centralized EndingResolver,
  shops/upgrades, all 15 enemies, 7 weapons complete with armor/accessories.
- **SHOULD:** optional bosses (Mourning Knight, Pale Widow, Ash Serpent),
  Sister Elara trust branch, Archivist decoding, Lost Child trail.
- **COULD:** achievements, expanded crafting, richer terminal motifs.

Known slice limitations (marked TODO, not presented as finished):
memories track 0/7 with no acquisition triggers yet; the Cathedral gate is a
visible story gate; shop/upgrade flows are not implemented.

## University demo script

See the master specification, section 14. The slice covers steps 1-9, 11 and
part of 12 (save/reload via MySQL is live and covered by integration tests).

## Documentation

| Document | Contents |
|----------|----------|
| [`docs/COMMAND_REFERENCE.md`](docs/COMMAND_REFERENCE.md) | every CLI command, menus, a complete first session, troubleshooting |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | module/data-flow diagrams, OOP patterns, DB ER model, package boundaries |
| [`docs/STORY_BIBLE.md`](docs/STORY_BIBLE.md) | canonical history, characters, bestiary, weapons, seven memories, three endings |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | automated suite inventory + the 30-scenario manual acceptance checklist |
| [`docs/TEAM_ROLES.md`](docs/TEAM_ROLES.md) | four members mapped to classes, tasks, SQL and demo steps |

## License / credits

Original concept and story bible by the project owner. Built as a four-member
team project; roles are mapped in [`docs/TEAM_ROLES.md`](docs/TEAM_ROLES.md).
