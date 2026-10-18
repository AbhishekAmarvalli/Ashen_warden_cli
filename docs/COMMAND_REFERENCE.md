# ASHEN WARDEN — CLI Command Reference & Gameplay Guide

Everything is typed at the `>` prompt (or the numbered main menu).
Commands are **case-insensitive**; unknown input is always safe — you get a
one-line correction and nothing changes.

---

## 1. Main menu

| Key | Action |
|---|---|
| `1` | **New Game** — asks for a save-slot name (stored in MySQL), then your character's name |
| `2` | **Continue** — resumes the most recently updated slot |
| `3` | **Load Slot** — lists slots (name, location, level, last played) to pick from |
| `4` | **Settings** — color / width / plain-text mode |
| `5` | **Exit** |

## 2. Launch flags

| Flag | Effect |
|---|---|
| `--plain` | ASCII output, no ANSI colors (screen-reader friendly) |
| `--no-color` | Colors off, layout unchanged |
| `--width <40–120>` | Wrap width for descriptions (default: terminal width, 80 fallback) |
| `--help` | Usage text |

The `NO_COLOR` environment variable is honored automatically.

---

## 3. Exploration commands

| Command | Alias | What it does |
|---|---|---|
| `look` | `l` | Describe where you are: exits, interactables, threats |
| `go <way>` | `move` | Travel — by direction (`north`/`south`/…) **or** an exit number from `look` |
| `map` | | Compact map of discovered locations and their links |
| `paths` | | Your current routes; locked paths say what's missing |
| `inspect <thing>` | | **Full details with no side effects**: items (`inspect 1` in inventory, `inspect <name>`), the current enemy, objects in the room |
| `interact <thing>` | | Use a world object: chests, journals, proclamations, levers… |
| `search` | | Examine the area closely — including **recovering dropped Ash Fragments after a death** |
| `talk <who>` | `speak` | Start a dialogue (e.g. `talk keeper`) |
| `ask <topic>` | | Ask the current speaker about a topic (`ask who`, `ask what`, `ask proof`, `ask ember`) |
| `choose <n>` | | Pick a numbered dialogue option |
| `leave` | | End the current dialogue |
| `rest` | | Rest at a sanctuary: restore HP, set respawn point, autosave |
| `journal` | | Story journal: current chapter, recent entries |
| `quests` | | Active/completed quests and their objectives |
| `memories` | | The seven Warden memories tracker (0/7 until triggers land) |
| `abilities` | | Abilities you hold (e.g. future Bellbreaker) |
| `inventory` | `inv`, `i` | List items with quantities |
| `equipment` | | Show equipped slots |
| `stats` | `stat` | HP, stamina, level, XP, Ash Fragments, derived stats |
| `save` | | Save to a named slot (outside combat; atomic commit) |
| `load` | | Load a slot (returns to the main menu flow) |
| `help` | `?` | Context-sensitive command list — **works at any time** |
| `quit`, `exit` | | Leave the game (offers to save if unsaved changes exist) |

## 4. Combat commands

Combat is strictly turn-based: you act, then the enemy acts. At the start
of every round the enemy's **intent** is shown — and it is always truthful.

| Command | Alias | Cost / effect |
|---|---|---|
| `attack` | `strike` | Standard weapon strike, no stamina cost |
| `heavy` | | **15 stamina** — ~160% damage, staggers the enemy |
| `defend` | `block`, `guard` | Halves incoming damage this round **and** restores +15 stamina |
| `parry` | | Negates a **(Parryable)** telegraph and counters for free damage. Fails meaningfully against spells/grabs marked `UNBLOCKABLE` |
| `skill <name>` | | Weapon/art skill (stamina or resource cost shown before use) |
| `item <name>` | `items` | Use a consumable (e.g. `item crimson vial`) — costs your turn |
| `inspect enemy` | | **Free (no turn):** traits, weaknesses, resistances, and whether the next hit is parryable |
| `inspect item <name>` | | **Free:** full item details mid-combat |
| `status` | | Current statuses on you and the enemy (Bleed, Poison, Burn… with remaining duration) |
| `log` | | Recent combat log lines |
| `flee` | | Attempt to escape — refused in sealed/boss fights |

**Guarantees:**
- Invalid input or an unaffordable action **never consumes a turn** and
  changes nothing.
- Telegraph tags: `(Parryable)` = parry works · `UNBLOCKABLE` = parry fails,
  defend only · `(guarding)` = the enemy is reducing incoming damage.
- If you kill the enemy with your action, it does not get to act.
- Status effects tick exactly once per round and expire at their
  documented duration.

## 5. Menus (inventory, loot, shops, upgrades)

Menus accept the same safe-input rule; `back`/`cancel` always costs nothing.

| Command | Action |
|---|---|
| `1`, `2`, … | Select the numbered entry (opens its detail page) |
| `inspect <n>` | Full detail page: stats, lore, requirements, drawbacks, **live comparison** against what you have equipped |
| `compare <n>` | Precise deltas vs. the currently equipped item in that slot |
| `equip <n>` | Equip the inspected item (asks for confirmation when it has a cost or drawback) |
| `unequip <slot>` | Remove a piece of equipment |
| `take <n>` | Take one loot item |
| `take all` | Take everything (you may still inspect first) |
| `back` / `cancel` | Leave the menu — no resources lost |

**Inspection-first rule:** every choice screen (loot, equip, shop,
upgrade) shows full details before you can commit, and stepping back is
always free.

---

## 6. A complete first session (copy-paste)

```
1                        New Game
tomb-01                  save-slot name
Ash                      your character's name
look                     where am I?
interact journal         read the damaged journal → quest begins
inventory                you start with Warden's Silverblade + 3 Crimson Vials
inspect 1                full item details BEFORE any choice
equip 1                  equip the Silverblade → confirm with 1
go north                  Hollow Soldier blocks the way → combat!
inspect enemy            free: traits, weakness, parryable?
parry                    counter the telegraphed slash
defend                   recover stamina while it guards
heavy                    finish it
take all                 loot screen — inspect, then take
go north                  second fight: Grave Crawler (bleed, burrows!)
item crimson vial         heal — costs your turn
rest                      heal + set respawn at the sanctuary
talk keeper              dialogue → choose 1..n, ask proof, leave
journal | quests | memories
stats | abilities | map
save                     atomic MySQL commit
quit
```

Restart, choose `2` Continue — identical progress loads from MySQL.

---

## 7. Death rule

On death you wake at your **last sanctuary** with your quest progress,
boss wins, opened routes and memories intact. A fraction of your Ash
Fragments drops where you died — return once and `search` to recover
them. Unrecovered drops are lost the next time you die.

## 8. Troubleshooting quick keys

| Symptom | Fix |
|---|---|
| `help` mid-command | lists only commands valid *right now* |
| Colors look wrong | relaunch with `--plain` or `--no-color`, or set `NO_COLOR=1` |
| Text wrapping oddly | `--width 100` |
| "Cannot reach database" | see README → *Troubleshooting*; the game still runs, saving is paused |
| Want a clean slate | delete a slot from the in-game slot list or drop/recreate the `ashen_warden` database (migrations recreate it) |
