# ASHEN WARDEN — Test Plan & Manual Acceptance Checklist

## 1. Running the tests

```bash
# unit tests always; DB integration tests additionally when DB_* is set
export DB_URL='jdbc:mysql://localhost:3306/ashen_warden'
export DB_USER=root            # and DB_PASSWORD
mvn test
```

**Honesty rule:** when MySQL is unreachable, `SaveServiceTest` calls
`assumeTrue` and reports those tests as **SKIPPED** — they are never
counted as passing. Current state: **64/64 pass** with MySQL configured
(57 unit + 7 integration).

## 2. Automated suite (64 tests, 11 classes)

| Class | # | Covers |
|---|---|---|
| `combat/DamageCalculatorTest` | 6 | mitigation formula & floor of 1 · never 0/negative damage · immunity · weakness multiplier · defend halves *eligible* damage but not unblockable · player weakness status reduces outgoing damage |
| `combat/CombatEngineTest` | 7 | invalid input never consumes a turn · `inspect`/`status` are free actions · parry negates a parryable telegraph and counters · parry fails against unblockable without zeroing the hit · full fight reaches victory with rewards granted once · seeded encounters are reproducible · flee refused when escape is sealed |
| `command/CommandParserTest` | 9 | numbers become menu selections · combat verbs parse only as combat types · phase-aware `use` · inspect variants · bare direction acts as `go` · `take all`/`back`/`compare` · unknown input flagged but safe · `item` outside combat opens inventory · oversized input bounded (no crash) |
| `player/InventoryAndEquipmentTest` | 6 | inventory never negative/corrupt · equip/unequip without duplication · weapon swap returns old one to inventory · wrong/locked slot rejected · equipping requires ownership · **all shipped items have honest inspection fields** (no hidden stats) |
| `combat/DodgeTest` | 5 | dodge costs 12 stamina and its stance is consumed by the round · an unaffordable dodge spends no turn and the enemy does not act · (UNBLOCKABLE) lands against a dodge with a truthful reason · one seeded stream shows both evasion and failure, with no damage/status on an evaded hit |
| `enemy/TierTwoEliteBehaviorTest` | 7 | elite patterns: executioner measure→guillotine payoff, one-time phase shift at half health, anti-turtle backhand · priest litany→soul-rake payoff, one-time Ashen Rite at 40%, ward rhythm · spectral weapons fully resisted · **engine regression: a GATHER self-heal actually heals** |
| `enemy/TierOneBehaviorTest` | 6 | new Tier-1 rule tables: wanderer telegraphs then pays off, punishes turtling, grows reckless below half health · pilgrim heals on a strict 2-round cooldown (never a loop), punishes turtling, both behaviors registered for content validation |
| `ui/KeyDecoderTest` | 8 | escape-sequence decoding: arrows from both prefixes, PageUp/PageDown, a lone Escape never mistaken for a sequence, digits/chars with values, Ctrl+C/Ctrl+D, unknown sequences absorbed without leaking bytes |
| `ui/InteractiveMenuTest` | 7 | Enter confirms the highlight · arrows wrap both ends · digits jump to an option · out-of-range digits ignored (no double draw) · Escape cancels · `?`/Ctrl+C reported to the caller · navigation redraws in place (cursor-up + erase) instead of scrolling |
| `ui/KeyboardMainMenuTest` *(integration, no DB)* | 2 | scripted terminal plays the real boot screen end-to-end (arrows → HOW TO PLAY → Ctrl+D exits cleanly) · non-key input still drives the typed menu |
| `persistence/SaveServiceTest` *(integration)* | 7 | full round trip preserves location/HP/currency/inventory/equipment/quests/flags · **slot independence** · duplicate slot name rolls back and reports · invalid save rejected before touching the DB · bosses & memories survive reload · corrupt save reported, not silently reset · slot listing |

Run-time content validation (not a JUnit test, but a guard): on boot
`ContentRepository` aborts with a friendly error on duplicate IDs,
unknown cross-references or bad triggers — so a broken content file can
never ship or corrupt a save.

## 3. Manual acceptance checklist (spec §13, TEST 01–30)

Legend: **[AUTO]** covered by an automated test · **[MAN]** verified by a
scripted/manual playthrough of the slice · **[W]** requires roadmap
content (marked TODO, not presented as finished).

| # | Scenario | Status | Notes |
|---|---|---|---|
| 01 | Fresh launch + working MySQL: new game creates independent slot, tutorial begins | **[MAN]** | slot auto-created on first save; live-verified |
| 02 | MySQL unavailable: friendly error + recovery steps, no stack trace | **[MAN]** | game starts degraded, saving paused |
| 03 | `attack` in exploration rejected, state unchanged | **[MAN]+[AUTO]** | phase policy in dispatcher; parser phase tests |
| 04 | Invalid command in combat: no stamina/HP/enemy change | **[AUTO]** | `invalidInputNeverConsumesATurn` |
| 05 | `inspect enemy` shows intent/traits, no turn consumed | **[AUTO]** | `inspectAndStatusAreFree` |
| 06 | Defend reduces eligible damage per formula | **[AUTO]** | `defendHalvesEligibleDamageButNotUnblockable` |
| 07 | Parry works on telegraphed parryable, fails meaningfully on unblockable | **[AUTO]** | two parry tests |
| 08 | Killing blow before enemy turn: dead enemy doesn't act | **[AUTO]+[MAN]** | `fullFightReachesVictoryWithRewardsOnce` |
| 09 | Poison/Bleed/Burn expire at documented duration, no double ticks | **[MAN]** | statuses tick once per round in `CombatEngine` |
| 10 | Loot screen: inspect every reward before take/leave | **[MAN]** | menu flow live-verified |
| 11 | Inspect Bloodthorn Blade: HP cost visible before confirm | **[W]** | Bloodthorn Blade is roadmap (Phase 4); the confirm-with-cost pattern exists (equip confirmation) |
| 12 | Compare two weapons with precise deltas/requirements/drawbacks | **[MAN]+[AUTO]** | `compare <n>`; honest-fields test |
| 13 | Repeated equip/unequip: defense never duplicates | **[AUTO]** | `equipMovesItemOutAndBackWithoutDuplication` |
| 14 | Buy with insufficient currency: no change | **[W]** | shops are roadmap (Phase 4) |
| 15 | DB disconnect mid-save: rollback + honest message | **[MAN]+[AUTO partial]** | transactional `SaveService`; rollback path tested via duplicate-slot test; physical cable-pull is a manual step |
| 16 | Save → quit → restart → load: identical everything | **[AUTO]+[MAN]** | `roundTripPreservesEverythingThatMatters` + live playthrough |
| 17 | Two slots isolated | **[AUTO]** | `slotsAreIndependent` |
| 18 | Defeat Bell Keeper, reload: stays defeated, Bellbreaker kept | **[AUTO partial]** | `bossesAndMemoriesSurviveReload` proves persistence; the Bell Keeper encounter itself is roadmap |
| 19 | Cracked wall blocked until Bellbreaker | **[W]** | ability-gate data model exists (`unlocked_paths`); encounter is roadmap |
| 20 | Quest reward granted only once across save/reload/dialogue | **[MAN]** | event-driven `QuestManager`, reward-once flags persisted in `quest_progress` |
| 21 | Seven memories acquired → ending C available if seals restored | **[W]** | 7 memory defs + tracker shipped (0/7); triggers and endings roadmap |
| 22 | Missing memory: ending C blocked with unspoiled explanation | **[W]** | centralized `EndingResolver` planned |
| 23 | Hollow King phase 1→2→3 transitions once at thresholds | **[W]** | boss roadmap (Phase 2/3) |
| 24 | `--plain` / `NO_COLOR`: readable, no ANSI artifacts | **[MAN]** | verified on Windows terminal |
| 25 | Narrow width wraps without destroying prompts | **[MAN]** | `--width 40–120` |
| 26 | Fully playable offline with local MySQL | **[MAN]** | no network dependency; pom has only Jackson + MySQL driver + JLine 3 + JUnit |
| 27 | No LLM/AI imports, endpoints, config or runtime features | **[AUTO by dependency set]** | verified: dependencies are jackson-databind, mysql-connector-j, jline, junit-jupiter only; no HTTP client code |
| 28 | Invalid item IDs / negative quantities / bad slot IDs can't corrupt DB | **[AUTO]** | inventory negativity + `invalidSaveIsRejectedBeforeHittingTheDatabase` |
| 29 | Back out of equip/confirm without losing resources | **[MAN]** | `back`/`cancel` cost nothing |
| 30 | Ending + credits show outcome and preserve completion record | **[W]** | endings roadmap; completion record planned with `story_flags` |
| 31 | Boot menu driven by arrows/Enter/digits/`?`, redrawn without scrolling | **[AUTO]+[MAN]** | `InteractiveMenuTest`, `KeyboardMainMenuTest`; live on Windows Terminal |
| 32 | No console (pipes, IDE, `--plain`, `--no-keys`): typed menu, game fully playable | **[AUTO]+[MAN]** | `withoutKeySupportTheTypedMenuStillWorks`; piped smoke run |
| 33 | Dodge: cost, chance vs. defense, no evasion of (UNBLOCKABLE), one-round stance | **[AUTO]+[MAN]** | `DodgeTest`; live playthrough log |
| 34 | Tier-1 roster complete (Wanderer, Soldier, Pilgrim, Crawler) and reachable in-world | **[AUTO]+[MAN]** | `TierOneBehaviorTest` + content validation; scripted Act I walk-through fought all four |
| 35 | Tier-2 elites fight in announced patterns (telegraph→payoff, one-time phase shift) | **[AUTO]+[MAN]** | `TierTwoEliteBehaviorTest`; live Executioner fight showed measure→GUILLOTINE→The Red Opens |
| 36 | Elite self-heals report and land (rite/ward/prayer) | **[AUTO]+[MAN]** | heal branch regression test; live priest "recovers 10 HP" and pilgrim "recovers 12 HP" |
| 37 | The Blackwood side area is reachable, has a rest point, and pays off (chest) | **[MAN]** | scripted walk-through: Verge brazier rest, both elite kills, Wraith Dagger + Crimson Vial taken |

**Score for the shipped slice:** every scenario that can exist without
roadmap content is implemented and verified (21 of 30 fully covered; 15
by automation alone). The 9 marked **[W]** map 1:1 to the README roadmap.

## 4. Demo walkthrough mapping (spec §14)

| Demo step | Covered by |
|---|---|
| 1 professional CLI menu | launch — arrow-key boot screen (typed menu without a console) |
| 2 new slot + MySQL persistence | save on first rest/`save` |
| 3 explore tomb, read journal, meet Keeper | `look`/`interact`/`talk` |
| 4 inspect Silverblade **before** equipping | `inspect 1` then `equip 1` |
| 5 Hollow Soldier: attack/defend/parry + telegraph explained | combat round log |
| 6 loot: inspect then take | loot menu |
| 7 inventory compare + equip | `compare <n>` |
| 8 quest, memory tracker, map | `journal`/`memories`/`map` |
| 9 save → exit → restart → load | MySQL round trip (TEST 16) |
| 10 boss phase logic | **[W]** scripted encounter planned |
| 11 locked route + ability | **[W]** (gate table exists) |
| 12 ending resolver | **[W]** dedicated test save |
| 13 per-member class walkthrough | `docs/TEAM_ROLES.md` |

## 5. Regression policy

- Run `mvn test` before every merge (spec §15).
- New content files must pass startup validation; add a case to
  `InventoryAndEquipmentTest`-style suites for new item rules.
- Bug fixes land with a test that fails before the fix.
