# ASHEN WARDEN — Story Bible

Canonical narrative reference for the CLI game. Derived from
`The_Ashen_Warden_Complete_Game_Bible.txt` (master source, Part I).
Status tags: **[S]** implemented in the vertical slice · **[W]** specified,
roadmap content.

> A kingdom that could not die. An oath that could not end.

---

## 1. High concept

Veyrath is a kingdom that attempted to conquer death and instead trapped
its people between life and oblivion. The last Warden wakes in a
battlefield of ash with a fractured memory, a silver sword and a dying
amber flame in his chest. He travels through interlinked regions,
mastering combat and uncovering that his own resurrection is connected to
the catastrophe. At the throne he must decide whether to destroy, claim,
or redeem the Eternal Flame.

Core fantasy: a solitary armored knight pushes through overwhelming
ruins, survives through skill, and uncovers the truth about his order.
Narrative emerges through environments, item descriptions, NPC dialogue
and short memory scenes — never endless exposition.

**Themes:** preserving life vs. refusing to accept death. Every ruin,
cursed soldier, broken weapon and ending echoes that conflict.

---

## 2. Canonical timeline of Veyrath

| Era | Event |
|---|---|
| **Age of Embers** | Settlers discover an unnatural subterranean flame beneath the future capital. Priests name it the **Eternal Flame** and build a protective order around it. |
| **Age of Crowns** | The monarchy establishes Veyrath. The **Ashen Wardens** are sworn to guard the Flame and prevent its exploitation; the Order is independent of the crown in forbidden-magic matters. |
| **The King's Decline** | The aging king fears death and authorizes experiments to bind the Flame to royal blood. The court hides disappearances and failed experiments. |
| **The Night of Seven Bells** | Priests begin a ritual requiring a living vessel. The Warden Order attacks the palace to stop it. The king betrays the Warden captain and steals the ritual. **Seven seals fail; the Ashen Blight spreads.** |
| **The Long Ash** | Settlements empty, soldiers rise as hollows, cathedrals become cult sanctuaries, the Hollow King remains trapped in the capital. Survivors pass down distorted stories blaming the Wardens. |
| **The Final Toll** | Centuries later the seals weaken; the protagonist reawakens with an amber flame lodged in his chest. **[S]** — the game begins here. |

## 3. The curse and its rules (canon constraints)

- The **Ashen Blight** is a failed metaphysical binding between life,
  memory and the Eternal Flame — not an ordinary infection.
- Victims lose **identity before bodily form**: some stay intelligent,
  some become instinct-driven hollows, some manifest as spectral remnants.
- **Ash** is the physical residue of the curse.
- Color language: **crimson** = blood used as a stabilizing conduit;
  **spectral blue** = displaced memory; **amber** = the original Flame.
- The player is neither fully living nor conventionally undead. The Flame
  can heal and empower, but excessive reliance threatens identity.
- These rules must govern enemies, magic, UI and endings — never contradicted.

---

## 4. Acts and regions

### Prologue — The knight who should have died **[S]**
Black screen, one distant bell, a cracked visor beneath gray ash, a weak
amber glow inside the helmet. The Warden rises on a battlefield strewn
with the Order's insignia. A whisper: *"When the final bell tolls, return
to the throne."* **[S]** — implemented as the Warden's Tomb: rise, learn
the rules, read the damaged journal, take the Silverblade, fight a Hollow
Soldier, meet the Lantern Keeper, reach the Forgotten Outskirts.

### Act I — The Land of Forgotten Dead **[part S]**
Region: **Forgotten Outskirts** **[S]** → **Cathedral of Broken Bells** **[W]**.
The Lantern Keeper identifies the Warden crest; survivors fear the dead
and distrust the Order. Evidence contradicts the official royal account
(the royal proclamation item **[S]**). The Warden follows an impossible
bell toll to the ruined cathedral. Boss: **Bell Keeper** (guardian fused
with chains and a great bell) defends the first seal. Victory grants the
memory of the Order's final march and the **Bellbreaker** traversal power
— destroying cracked stone walls and opening shortcuts.

### Act II — The Crimson Faith **[W]**
Regions: **Blackwood Forest** (unlock *Wraith Step*) and **Crimson
Cathedral** (unlock *Ashen Flame*). A cult teaches the curse is sacred
transformation. **Sister Elara** reveals ritual sacrifices hidden as
"holy offerings." The **Crimson Bishop** claims the Warden was a willing
participant; archives prove the Order was sacrificed to stabilize the
ritual. Boss: **Crimson Bishop** (blood magic, three phases).

### Act III — Beneath the Hollow Crown **[W]**
Regions: **Royal Crypts** (unlock *Double Jump*; recover critical
memories). Tombs of former comrades including **Sir Vaelor's** history;
the **Fallen Executioner** guards the execution chamber. Royal records
reveal the protagonist was selected as the Flame's vessel; the king
betrayed him to seize the power. He was buried with a fragment of the
Flame and awakens when the seals weaken.

### Act IV — The Throne of Ashes **[W]**
Region: **Palace of the Hollow Crown**. Elite corrupted knights guard a
court frozen in death. The **Hollow King** waits beneath a crown fused to
his skull: phase 1 royal sword technique, phase 2 flame + sword, phase 3
the Eternal Flame as a barely controlled entity. Victory opens the final
choice, conditioned on memories collected and seals restored.

**Metroidvania route:** Outskirts → Bell Cathedral → Blackwood → Crimson
Cathedral → Crypts → Palace, with backtracking loops. Each ability must
unlock at least one mandatory path plus optional secrets.

---

## 5. Factions and characters

| Character | Role | Status |
|---|---|---|
| **The Ashen Warden** (protagonist) | Silent, disciplined, suspicious of easy answers, capable of compassion. Blackened plate, amber visor, torn crimson cloak, silver longsword. | **[S]** |
| **Lantern Keeper** | Elderly woman, gray hood, warm lantern. Guide, memory keeper, emotional anchor. Trusts the Warden after proof of good deeds. First dialogue tree implemented (`greet`, `who`, `what`, `proof`, `ember`). | **[S]** |
| **Ashen Smith** | Broad blacksmith, iron mask, burn scars. Forges and upgrades weapons. | **[W]** |
| **Sister Elara** | Former priestess, torn white vestments. Healing and moral quests; her story reveals the Church's complicity. Trust branch. | **[W]** |
| **Nameless Merchant** | Hooded wanderer, oversized pack, concealed face. Sells strange items, offers cryptic warnings. | **[W]** |
| **Lost Child** | Pale child at impossible locations; hidden quest and memory catalyst. Nature uncertain until optional quest completion. | **[W]** |
| **Sir Vaelor** | Injured former Warden who survived the betrayal; ally or optional boss. | **[W]** |
| **Archivist** | Undead scholar; lore and puzzle clues. | **[W]** |

**Antagonists:** Bell Keeper (tragedy: sacrificed his body to keep the
seal intact, no longer knows friend from foe) · Crimson Bishop (serene,
persuasive, fanatical — believes he saved humanity by accepting the
curse) · Hollow King (terror of mortality, self-justifying tyranny).

**First quest [S] — "First Steps"** (`quest_first_steps`): read the
damaged journal → equip the Warden's Silverblade → defeat a Hollow
Soldier → speak with the Lantern Keeper → leave the tomb. Objectives are
event-driven and reward-once.

---

## 6. Bestiary (15 enemies + bosses)

**Implemented [S]:**
- **Hollow Soldier** — decayed royal infantryman, rusted plate, chipped
  shield, broken sword. Slow, recognizable threat that teaches combat
  fundamentals: patrol → detect → approach → attack → recovery →
  guard. Drops Ash Fragments, rarely Armor Shards.
- **Grave Crawler** — low skeletal quadruped, stretched limbs, pale eyes.
  Hides, ambushes and lunges; applies **Bleed**.

**Specified [W]:** Plague Rat · Ash Hound · Bone Archer · Blood Cultist ·
Cathedral Acolyte · Bellbound Guardian · Tomb Sentinel · Fallen
Executioner · Blood Leech · Wraith · Ashen Knight · Royal Sorcerer ·
Crownless Noble (+ 2 more to reach the roster of 15).

**Bosses [W]:**
| Boss | Arena | Phases | Reward |
|---|---|---|---|
| Bell Keeper | Cathedral of Broken Bells | 1 hammer/chains → 2 bell resonance → 3 fractured overlapping shockwaves | **Bellbreaker** ability + first seal |
| Crimson Bishop | Crimson Cathedral | 1 blood orbs/staff → 2 summons/teleport → 3 blood apparition | **Ashen Flame** rune activation |
| Hollow King | Throne of Ashes | 1 "Fallen Monarch" sword → 2 "Burning Crown" flame+sword → 3 "Empty Throne" Flame entity | final choice |

**Optional bosses [COULD]:** Mourning Knight (mirrored duel with a former
comrade), Pale Widow (spectral queen, illusion duplicates), Ash Serpent
(colossal beast born from discarded Flame).

**Combat canon rule:** every dangerous attack telegraphs before its
active damage window, and the telegraph is truthful — what is shown
(`(Parryable)`, `UNBLOCKABLE`) is what happens.

---

## 7. Weapons, armor, consumables

**Seven weapons** (codes in brackets where implemented):
1. **Warden's Silverblade** `[warden_silverblade]` **[S]** — balanced
   silver longsword, black crossguard, faded amber rune. Standard combo,
   charged slash, parry. *"An oath sworn in silver outlasts the tongue
   that spoke it."*
2. **Bloodthorn Blade** — fast bleeding slashes; special attack **consumes
   the wielder's HP** for burst damage (cost must always be shown before
   confirmation).
3. **Bellbreaker Sword** — forged from the Keeper's bell; slow, high
   stagger, shockwaves.
4. **Ashen Greatsword** — heavy blade with amber cracks; burning damage.
5. **Wraith Dagger** `[wraith_dagger]` **[S]** — translucent, very fast,
   low reach, teleport/backstab specialty.
6. **Crimson Staff** — blood projectiles and shields.
7. **Executioner's Axe** — high single-hit damage, slow recovery.

**Armor sets [partial S:** `warden_armor` starter set **]:** Starter Warden
Armor · Ashen Guard · Wraithbound Mantle · Crimson Penitent · Royal
Remnant. **Accessories:** Bell Charm, Ember Sigil, Wraith Ring, Soldier's
Knot, Martyr's Seal.

**Consumables & currency:** Crimson Vial (heal) `[S]` · Azure Tear
(ability energy) · Ash Fragments (common currency) `[S]` · Ancient Coin
`[S]` · Armor Shards · Bloodfire Essence · Wraith Relic · Iron Tonic
`[S]` · Forged Steel `[S]` · Crown of Ashes (legendary story artifact) ·
Ancient Warden Scroll.

Rarity language: common = minimal shine, uncommon = subtle aura, rare =
strong signature color, legendary = unique treatment. **Never make all
loot equally luminous.**

---

## 8. The seven Warden memories **[S: tracked 0/7, triggers W]**

| # | Memory | Reveals |
|---|---|---|
| 1 | **The Oath** `[memory_oath]` | The protagonist joins the Order and learns its vow. |
| 2 | **The Warning** `[memory_warning]` | The Order discovers the king's forbidden experiments. |
| 3 | **The Betrayal** `[memory_betrayal]` | Royal guards attack the Wardens under false orders. |
| 4 | **The Sacrifice** `[memory_sacrifice]` | Prisoners are offered to the Flame. |
| 5 | **The Vessel** `[memory_vessel]` | He was selected to contain the power. |
| 6 | **The Final Assault** `[memory_final_assault]` | The Order fights to reach the ritual chamber. |
| 7 | **The Choice** `[memory_choice]` | He refused immortality and tried to save the kingdom. |

Each memory: collectible relic, brief vignette, distinct ambience,
optional lore text, persistent journal entry. The journal tracker and all
seven definitions ship in the slice; acquisition triggers are roadmap.

---

## 9. The three endings

| Ending | Name | Condition | Tone |
|---|---|---|---|
| **A** | **The Last Ember** | Absorb the Flame and extinguish it at the cost of the Warden's existence. | Bitter but hopeful: the undead rest, survivors see sunrise, empty armor remains near the throne. |
| **B** | **The Hollow Throne** | Seize the crown and control the Flame. | Ambiguous, ominous: the kingdom appears restored, but a new immortal ruler sits where the old one fell. |
| **C** | **Dawn Beyond Ash** | Requires **all seven memories** *and* restored seals; a final ritual separates Flame from bloodline without annihilating its bearer. | Hardest ending: the Warden lives, no longer bound to a king. |

Requirement rules: if a prerequisite is missing, the option is unavailable
and the requirement is explained **without a full spoiler** (TEST 22).
Endings are resolved by one centralized `EndingResolver` (roadmap) so
conditions are first-class game state, never scattered flags.

---

## 10. Canon consistency rules for contributors

1. Never contradict the timeline in §2 or the curse rules in §3.
2. Every item's lore text must be consistent with its mechanics (hidden
   costs are always shown).
3. New dialogue/quests must use stable string IDs and go through
   `ContentRepository` validation — no ad-hoc flags.
4. Amber = Flame, crimson = blood magic, blue = displaced memory.
5. Tragic antagonists: bosses were people who made consequential choices.
6. If a feature is incomplete it is marked TODO in the roadmap — never
   presented as finished.
