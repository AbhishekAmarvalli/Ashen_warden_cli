package com.ashenwarden.combat;

import com.ashenwarden.cli.ItemText;
import com.ashenwarden.common.GameException;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEvent;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.enemy.Enemy;
import com.ashenwarden.enemy.EnemyAction;
import com.ashenwarden.enemy.EnemyBehavior;
import com.ashenwarden.enemy.EnemyDef;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.item.WeaponSpec;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turn-based combat resolution. One round: reveal intent -> validate player
 * action (invalid input never spends a turn) -> apply player action -> if the
 * enemy lives, resolve its telegraphed action -> end-of-round effects in a fixed
 * documented order -> pick the next intents. Enemy decisions come from rule
 * tables only (no AI/ML, seeded variation).
 */
public final class CombatEngine {

    public static final String ASH_FRAGMENTS = "ash_fragments";

    public enum ActionType {
        ATTACK, HEAVY, DEFEND, DODGE, PARRY, SKILL, ITEM, FLEE,
        INSPECT_ENEMY, INSPECT_ITEM, STATUS, HELP, LOG
    }

    /** Stamina a dodge costs — cheaper than a heavy strike, unlike a parry. */
    public static final int DODGE_STAMINA_COST = 12;

    /** Result of one command inside combat: lines to show + whether a turn was spent. */
    public record Turn(List<String> lines, boolean turnConsumed) {
    }

    private final ContentRepository content;
    private final BehaviorRegistry behaviors;
    private final SkillCatalog skillCatalog;

    public CombatEngine(ContentRepository content, BehaviorRegistry behaviors, SkillCatalog skillCatalog) {
        this.content = content;
        this.behaviors = behaviors;
        this.skillCatalog = skillCatalog;
    }

    // --- encounter lifecycle ------------------------------------------------

    public List<String> start(GameState state, String encounterCode, List<String> enemyCodes,
                              boolean escapeAllowed) {
        if (enemyCodes == null || enemyCodes.isEmpty()) {
            throw new GameException("Encounter '" + encounterCode + "' has no enemies configured.");
        }
        CombatState cs = new CombatState(encounterCode, escapeAllowed);
        for (String code : enemyCodes) {
            cs.addEnemy(new Enemy(content.enemy(code)));
        }
        state.setCombat(cs);
        state.setPhase(com.ashenwarden.engine.GamePhase.COMBAT);

        List<String> out = new ArrayList<>();
        for (Enemy e : cs.enemies()) {
            out.add(article(e.name()) + " " + e.name() + " bars your way. (" + e.def().region() + ")");
        }
        for (Enemy e : cs.enemies()) {
            chooseIntent(state, cs, e);
        }
        out.add(intentLine(cs));
        cs.log("Encounter started: " + encounterCode);
        return out;
    }

    /** Picks each living enemy's next action; the telegraph shown is always truthful. */
    private void chooseIntent(GameState state, CombatState cs, Enemy enemy) {
        if (enemy.isDead()) {
            return;
        }
        EnemyBehavior behavior = behaviors.require(enemy.def().behaviorCode());
        EnemyBehavior.Context ctx = new EnemyBehavior.Context(
                cs.round(),
                cs.playerDefendedLastRound(),
                state.getPlayer().getHp() * 100 / Math.max(1, state.getPlayer().getMaxHp()),
                state.getRng());
        enemy.setCurrentIntent(behavior.choose(enemy, ctx));
    }

    // --- command dispatch ---------------------------------------------------

    public Turn act(GameState state, ActionType type, String arg) {
        CombatState cs = state.getCombat();
        if (cs == null) {
            return new Turn(List.of("There is no active encounter."), false);
        }
        // Free look actions: never consume a turn (spec rule).
        switch (type) {
            case INSPECT_ENEMY:
                return new Turn(inspectEnemy(cs), false);
            case INSPECT_ITEM:
                return new Turn(inspectItem(state, arg), false);
            case STATUS:
                return new Turn(statusLines(state, cs), false);
            case HELP:
                return new Turn(helpLines(), false);
            case LOG:
                return new Turn(logLines(cs), false);
            default:
                break;
        }
        if (!cs.isOngoing()) {
            return new Turn(List.of("The encounter has already ended."), false);
        }

        List<String> out = new ArrayList<>();
        Player player = state.getPlayer();

        // STUN skips exactly one action (status-driven, announced, not auto-battle).
        if (player.hasStatus(StatusType.STUN)) {
            player.removeStatus(StatusType.STUN);
            String line = "You are stunned — the moment slips past and your action is lost.";
            out.add(line);
            cs.log(line);
            return runEnemyAndRound(state, cs, out, true);
        }

        switch (type) {
            case ATTACK:
                applyAttack(state, cs, out);
                break;
            case HEAVY:
                if (!applyHeavy(state, cs, out)) {
                    return new Turn(out, false);
                }
                break;
            case DEFEND:
                applyDefend(state, cs, out);
                break;
            case DODGE:
                if (!applyDodge(state, cs, out)) {
                    return new Turn(out, false);
                }
                break;
            case PARRY:
                applyParry(state, cs, out);
                break;
            case SKILL:
                if (!applySkill(state, cs, arg, out)) {
                    return new Turn(out, false);
                }
                break;
            case ITEM:
                if (!applyItem(state, cs, arg, out)) {
                    return new Turn(out, false);
                }
                break;
            case FLEE:
                if (!applyFlee(state, cs, out)) {
                    return new Turn(out, false);
                }
                if (cs.outcome() == CombatState.Outcome.ESCAPED) {
                    return new Turn(out, true);
                }
                break;
            default:
                return new Turn(List.of("That command is not available in combat."), false);
        }

        if (!cs.isOngoing()) {
            return new Turn(out, true);
        }
        return runEnemyAndRound(state, cs, out, true);
    }

    /** Enemy action + end-of-round bookkeeping, then the next telegraph. */
    private Turn runEnemyAndRound(GameState state, CombatState cs, List<String> out, boolean consumed) {
        enemyPhase(state, cs, out);
        if (cs.isOngoing()) {
            endOfRound(state, cs, out);
        }
        if (cs.isOngoing()) {
            out.add(intentLine(cs));
        }
        return new Turn(out, consumed);
    }

    // --- player actions -----------------------------------------------------

    private EffectiveStats stats(GameState state, CombatState cs) {
        EffectiveStats base = EffectiveStats.compute(state.getPlayer(), state.getEquipment(), content.items());
        int bonus = cs.tempDefenseBuff();
        if (bonus == 0) {
            return base;
        }
        return new EffectiveStats(base.maxHp(), base.maxStamina(), base.maxAsh(),
                base.attack(), base.defense() + bonus);
    }

    private WeaponSpec equippedWeapon(GameState state) {
        String code = state.getEquipment().equippedCode(EquipmentSlot.WEAPON);
        if (code == null) {
            return null;
        }
        ItemDef def = content.items().find(code);
        return def == null ? null : def.weapon();
    }

    /** Equipped weapon damage including its tempering bonus (one home for it). */
    private int temperedPower(GameState state, WeaponSpec weapon) {
        if (weapon == null) {
            return 0;
        }
        String code = state.getEquipment().equippedCode(EquipmentSlot.WEAPON);
        return weapon.damage() + com.ashenwarden.item.WeaponUpgrader.bonusDamage(state.upgradeLevel(code));
    }

    private void applyAttack(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        Enemy enemy = cs.firstEnemy();
        WeaponSpec weapon = equippedWeapon(state);
        int weaponPower = temperedPower(state, weapon);
        String element = weapon == null ? WeaponSpec.ELEMENT_PHYSICAL : weapon.element();

        DamageCalculator.Hit hit = new DamageCalculator(state.getRng())
                .playerToEnemy(player, stats(state, cs), weaponPower, 1.0, element, enemy);
        int dmg = enemy.takeDamage(hit.finalDamage());

        String line = (weapon == null ? "You batter the " : "You slash the ") + enemy.name()
                + " for " + dmg + " damage.";
        out.add(line);
        cs.log(line);
        if (weapon == null) {
            String bare = "Your bare fists do little. Equip a weapon (inventory -> equip).";
            out.add(bare);
            cs.log(bare);
        }
        if (hit.critical()) {
            String crit = "A clean opening — critical hit!";
            out.add(crit);
            cs.log(crit);
        }
        onEnemyDamaged(state, cs, enemy, out);
    }

    private boolean applyHeavy(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        WeaponSpec weapon = equippedWeapon(state);
        int cost = 15 + (weapon == null ? 0 : weapon.staminaMod());
        if (player.getStamina() < cost) {
            out.add("Not enough stamina for a heavy strike (needs " + cost
                    + ", has " + player.getStamina() + "). Your turn is not spent.");
            return false;
        }
        player.spendStamina(cost);
        Enemy enemy = cs.firstEnemy();
        String element = weapon == null ? WeaponSpec.ELEMENT_PHYSICAL : weapon.element();
        DamageCalculator.Hit hit = new DamageCalculator(state.getRng())
                .playerToEnemy(player, stats(state, cs), temperedPower(state, weapon),
                        1.6, element, enemy);
        int dmg = enemy.takeDamage(hit.finalDamage());

        String line = "You drive a heavy strike home for " + dmg + " damage (-" + cost + " stamina).";
        out.add(line);
        cs.log(line);
        if (hit.critical()) {
            String crit = "Critical hit!";
            out.add(crit);
            cs.log(crit);
        }
        if (dmg > 0 && !enemy.isDead()) {
            enemy.setStunned(true);
            String stagger = "The blow staggers the " + enemy.name() + " — it loses its action.";
            out.add(stagger);
            cs.log(stagger);
        }
        onEnemyDamaged(state, cs, enemy, out);
        return true;
    }

    private void applyDefend(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        cs.setPlayerDefendedThisRound(true);
        int before = player.getStamina();
        player.restoreStamina(15);
        int gained = player.getStamina() - before;
        String line = "You raise your guard and steady your breath. Incoming damage is halved while you hold it"
                + " (stamina +" + gained + ").";
        out.add(line);
        cs.log("You defend (stamina +" + gained + ").");
    }

    /**
     * Dodge: spend stamina to take a light stance for the round. Unlike parry
     * (which answers a telegraphed PARRYABLE blow with a guaranteed riposte),
     * dodge is probabilistic, works against any normal attack including spells,
     * and never counters. The roll itself happens per incoming hit in
     * {@link #enemyPhase}, and UNBLOCKABLE attacks cannot be evaded at all.
     */
    private boolean applyDodge(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        if (player.getStamina() < DODGE_STAMINA_COST) {
            out.add("Not enough stamina to dodge (needs " + DODGE_STAMINA_COST + ", has "
                    + player.getStamina() + "). Your turn is not spent.");
            return false;
        }
        player.spendStamina(DODGE_STAMINA_COST);
        cs.setPlayerDodgedThisRound(true);
        double chance = DamageCalculator.dodgeChance(stats(state, cs));
        String line = "You lighten your stance, ready to slip aside (-" + DODGE_STAMINA_COST
                + " stamina). About " + Math.round(chance * 100)
                + "% chance to evade each normal hit; (UNBLOCKABLE) attacks cannot be evaded.";
        out.add(line);
        cs.log("You dodge (" + DODGE_STAMINA_COST + " stamina).");
        return true;
    }

    private void applyParry(GameState state, CombatState cs, List<String> out) {
        Enemy enemy = cs.firstEnemy();
        EnemyAction intent = enemy.getCurrentIntent();

        if (intent == null || intent.isDefend()) {
            String line = "You wait for a blade that never comes — the " + enemy.name() + " is only guarding.";
            out.add(line);
            cs.log(line);
            return;
        }
        if (intent.is(EnemyAction.TAG_PARRYABLE)) {
            enemy.setStunned(true);
            DamageCalculator.Hit counter = new DamageCalculator(state.getRng()).playerToEnemy(
                    state.getPlayer(), stats(state, cs),
                    temperedPower(state, equippedWeapon(state)),
                    0.5,
                    equippedWeapon(state) == null ? WeaponSpec.ELEMENT_PHYSICAL : equippedWeapon(state).element(),
                    enemy);
            int dmg = enemy.takeDamage(counter.finalDamage());
            String line = "You parry the " + intent.name().toLowerCase(Locale.ROOT)
                    + ". Sparks bloom in the ash — riposte for " + dmg + " damage. "
                    + enemy.name() + " staggered!";
            out.add(line);
            cs.log(line);
            onEnemyDamaged(state, cs, enemy, out);
            return;
        }
        if (intent.is(EnemyAction.TAG_SPELL)) {
            String line = "Your blade sweeps empty air — spellcraft cannot be parried!";
            out.add(line);
            cs.log(line);
            return;
        }
        String line = "You chase the feint with your blade, but the " + intent.name().toLowerCase(Locale.ROOT)
                + " slips past your guard. Parry failed.";
        out.add(line);
        cs.log(line);
    }

    private boolean applySkill(GameState state, CombatState cs, String arg, List<String> out) {
        Player player = state.getPlayer();
        List<SkillDef> skills = skillCatalog.available(state.getEquipment(), content.items(),
                state.getUnlockedAbilities());
        SkillDef skill = findSkill(skills, arg);
        if (skill == null) {
            if (skills.isEmpty()) {
                out.add("You know no technique right now. Equip a weapon to gain its special.");
            } else {
                out.add("You know no such technique. Known: " + String.join(", ",
                        skills.stream().map(SkillDef::name).toList()));
            }
            return false;
        }
        int cooling = cs.cooldownOf(skill.code());
        if (cooling > 0) {
            out.add(skill.name() + " is on cooldown (" + cooling + " more round" + (cooling == 1 ? "" : "s")
                    + "). Your turn is not spent.");
            return false;
        }
        WeaponSpec weapon = equippedWeapon(state);
        int staminaMod = skill.code().startsWith("special:") && weapon != null ? weapon.staminaMod() : 0;
        int staminaCost = Math.max(0, skill.staminaCost() + staminaMod);
        if (player.getStamina() < staminaCost) {
            out.add("Not enough stamina for " + skill.name() + " (needs " + staminaCost + ", has "
                    + player.getStamina() + "). Your turn is not spent.");
            return false;
        }
        if (player.getAsh() < skill.ashCost()) {
            out.add("Not enough Ash energy for " + skill.name() + " (needs " + skill.ashCost() + ", has "
                    + player.getAsh() + "). Your turn is not spent.");
            return false;
        }
        player.spendStamina(staminaCost);
        player.spendAsh(skill.ashCost());

        Enemy enemy = cs.firstEnemy();
        String element = weapon == null ? WeaponSpec.ELEMENT_PHYSICAL : weapon.element();
        DamageCalculator.Hit hit = new DamageCalculator(state.getRng()).playerToEnemy(
                player, stats(state, cs), temperedPower(state, weapon),
                skill.multiplier(), element, enemy);
        int dmg = enemy.takeDamage(hit.finalDamage());
        String line = skill.name() + " hits the " + enemy.name() + " for " + dmg + " damage (-"
                + staminaCost + " stamina" + (skill.ashCost() > 0 ? ", -" + skill.ashCost() + " ash" : "") + ").";
        out.add(line);
        cs.log(line);
        if (hit.critical()) {
            out.add("Critical hit!");
            cs.log("Critical hit!");
        }
        String effect = skill.effect();
        if (effect != null && !enemy.isDead()) {
            if (effect.toLowerCase(Locale.ROOT).contains("burn")) {
                enemy.addStatus(new StatusInstance(StatusType.BURN, 6, 3));
                String s = enemy.name() + " suffers Burn (3 rounds).";
                out.add(s);
                cs.log(s);
            }
            if (effect.toLowerCase(Locale.ROOT).contains("staggers")) {
                enemy.setStunned(true);
                String s = "The impact staggers the " + enemy.name() + ".";
                out.add(s);
                cs.log(s);
            }
        }
        cs.setCooldown(skill.code(), skill.cooldown());
        onEnemyDamaged(state, cs, enemy, out);
        return true;
    }

    private SkillDef findSkill(List<SkillDef> skills, String arg) {
        if (arg == null || arg.isBlank()) {
            String known = String.join(", ", skills.stream().map(SkillDef::name).toList());
            return null;
        }
        String want = arg.trim().toLowerCase(Locale.ROOT);
        for (SkillDef s : skills) {
            if (s.code().equalsIgnoreCase(want) || s.name().equalsIgnoreCase(want)) {
                return s;
            }
        }
        SkillDef uniquePrefix = null;
        for (SkillDef s : skills) {
            if (s.name().toLowerCase(Locale.ROOT).startsWith(want)) {
                if (uniquePrefix != null) {
                    return null; // ambiguous
                }
                uniquePrefix = s;
            }
        }
        return uniquePrefix;
    }

    private boolean applyItem(GameState state, CombatState cs, String arg, List<String> out) {
        Player player = state.getPlayer();
        ItemDef item = resolveInventoryItem(state, arg);
        if (item == null) {
            out.add("You carry no such item. Check 'inventory' — your turn is not spent.");
            return false;
        }
        if (!item.isConsumable()) {
            out.add(item.name() + " is not a consumable. Your turn is not spent.");
            return false;
        }
        if (!state.getInventory().has(item.code(), 1)) {
            out.add("You have none left.");
            return false;
        }
        var effect = item.consumable();
        List<String> effects = new ArrayList<>();

        if (effect.healHp() > 0) {
            int amount = effect.healHp();
            if (player.hasStatus(StatusType.POISON)) {
                amount = amount * 3 / 4;
                effects.add("Poison reduces the healing.");
            }
            int before = Math.min(player.getHp(), player.getMaxHp());
            player.heal(amount);
            int healed = player.getHp() - before;
            effects.add("Restored " + healed + " HP.");
        }
        if (effect.restoreAsh() > 0) {
            int before = player.getAsh();
            player.restoreAsh(effect.restoreAsh());
            effects.add("Restored " + (player.getAsh() - before) + " Ash energy.");
        }
        if (effect.restoreStamina() > 0) {
            int before = player.getStamina();
            player.restoreStamina(effect.restoreStamina());
            effects.add("Restored " + (player.getStamina() - before) + " stamina.");
        }
        if (effect.cureStatus() != null && !effect.cureStatus().isBlank()) {
            try {
                StatusType type = StatusType.valueOf(effect.cureStatus().toUpperCase(Locale.ROOT));
                if (player.hasStatus(type)) {
                    player.removeStatus(type);
                    effects.add("Cured " + type.label() + ".");
                }
            } catch (IllegalArgumentException ignored) {
                effects.add("No matching ailment to cure.");
            }
        }
        if (effect.buffDefense() > 0) {
            cs.applyTempDefense(effect.buffDefense(), effect.buffTurns());
            effects.add("Defense +" + effect.buffDefense() + " for " + effect.buffTurns() + " rounds.");
        }

        state.getInventory().remove(item.code(), 1);
        String line = "You use " + item.name() + ".";
        out.add(line);
        cs.log(line);
        for (String e : effects) {
            out.add("  " + e);
            cs.log("  " + e);
        }
        state.markDirty();
        return true;
    }

    private ItemDef resolveInventoryItem(GameState state, String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        String want = arg.trim().toLowerCase(Locale.ROOT);
        ItemDef prefixMatch = null;
        for (String code : state.getInventory().snapshot().keySet()) {
            ItemDef def = content.items().find(code);
            if (def == null) {
                continue;
            }
            if (def.code().equalsIgnoreCase(want) || def.name().equalsIgnoreCase(want)) {
                return def;
            }
        }
        for (String code : state.getInventory().snapshot().keySet()) {
            ItemDef def = content.items().find(code);
            if (def != null && (def.name().toLowerCase(Locale.ROOT).startsWith(want)
                    || def.code().toLowerCase(Locale.ROOT).startsWith(want))) {
                if (prefixMatch != null) {
                    return null; // ambiguous
                }
                prefixMatch = def;
            }
        }
        return prefixMatch;
    }

    private boolean applyFlee(GameState state, CombatState cs, List<String> out) {
        if (!cs.escapeAllowed()) {
            out.add("There is no escape from this encounter — the way is sealed. Your turn is not spent.");
            return false;
        }
        if (state.getRng().nextInt(100) < 55) {
            cs.setOutcome(CombatState.Outcome.ESCAPED);
            String line = "You break away and slip into the ash.";
            out.add(line);
            cs.log(line);
            return true;
        }
        String line = "You try to break away, but the enemy cuts off your escape!";
        out.add(line);
        cs.log(line);
        return true;
    }

    // --- enemy resolution ---------------------------------------------------

    /** Correct English article for an introduction: "A Hollow Soldier", "An Ashen Wanderer". */
    private static String article(String name) {
        if (name == null || name.isEmpty()) {
            return "A";
        }
        return "AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "An" : "A";
    }

    /**
     * Applies a taken dodge attempt to one incoming attack.
     *
     * @return true when the attack was evaded entirely (caller skips it)
     */
    private boolean dodgeOutcome(GameState state, CombatState cs, EffectiveStats stats,
                                 DamageCalculator calc, Enemy enemy, EnemyAction intent,
                                 boolean unblockable, List<String> out) {
        String move = intent.name().toLowerCase(Locale.ROOT);
        if (unblockable) {
            String line = "There is no slipping aside — the " + move + " is too committed to evade.";
            out.add(line);
            cs.log(line);
            return false;
        }
        if (calc.rollsDodge(stats)) {
            String line = "You slip past the " + enemy.name() + "'s " + move
                    + " — the strike finds only ash.";
            out.add(line);
            cs.log(line);
            return true;
        }
        String line = "Your feet betray you — the " + move + " clips you as you move.";
        out.add(line);
        cs.log(line);
        return false;
    }

    private void enemyPhase(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        EffectiveStats stats = stats(state, cs);
        DamageCalculator calc = new DamageCalculator(state.getRng());
        boolean defended = cs.playerDefendedThisRound();

        for (Enemy enemy : cs.enemies()) {
            if (enemy.isDead()) {
                continue;
            }
            if (enemy.isStunned()) {
                enemy.setStunned(false);
                String line = enemy.name() + " is staggered — its action is lost.";
                out.add(line);
                cs.log(line);
                continue;
            }
            EnemyAction intent = enemy.getCurrentIntent();
            if (intent == null) {
                continue;
            }
            // healing happens for every kind of action (a prayer, a ward, a
            // brace), before the kind-specific branches below — never skipped
            if (intent.healAmount() > 0) {
                int before = enemy.getHp();
                enemy.heal(intent.healAmount());
                String line = enemy.name() + " recovers " + (enemy.getHp() - before) + " HP.";
                out.add(line);
                cs.log(line);
            }
            if ("DEFEND".equals(intent.kind())) {
                String line = enemy.name() + " keeps its guard up (damage it takes is halved while guarding).";
                out.add(line);
                cs.log(line);
                continue;
            }
            if ("GATHER".equals(intent.kind()) || "BUFF".equals(intent.kind())) {
                String line = intent.effectText() != null && !intent.effectText().isBlank()
                        ? intent.effectText()
                        : enemy.name() + " gathers itself.";
                out.add(line);
                cs.log(line);
                continue;
            }
            if (intent.isDamaging()) {
                boolean unblockable = intent.is(EnemyAction.TAG_UNBLOCKABLE);
                if (cs.playerDodgedThisRound() && dodgeOutcome(state, cs, stats, calc, enemy, intent,
                        unblockable, out)) {
                    continue; // evaded: no damage, no status, nothing else to apply
                }
                DamageCalculator.Hit hit = calc.enemyToPlayer(enemy, intent.multiplier(), defended,
                        unblockable, stats);
                int dmg = player.takeDamage(hit.finalDamage());
                String line = enemy.name() + " uses " + intent.name() + " — " + dmg + " damage to you";
                if (hit.critical()) {
                    line += " (critical)";
                }
                line += ".";
                out.add(line);
                cs.log(line);
                if (unblockable && defended) {
                    String warning = "Your guard means nothing against it!";
                    out.add(warning);
                    cs.log(warning);
                }
                if (intent.status() != null && !intent.status().isBlank()
                        && state.getRng().nextInt(100) < intent.statusChance()) {
                    try {
                        StatusType type = StatusType.valueOf(intent.status().toUpperCase(Locale.ROOT));
                        player.addStatus(new StatusInstance(type, intent.statusPower(), intent.statusTurns()));
                        String line2 = "You are afflicted with " + type.label() + "!";
                        out.add(line2);
                        cs.log(line2);
                    } catch (IllegalArgumentException ignored) {
                        // malformed content status is ignored rather than crashing combat
                    }
                }
            } else if (intent.effectText() != null && !intent.effectText().isBlank()) {
                out.add(intent.effectText());
                cs.log(intent.effectText());
            }
            if (player.isDead()) {
                defeat(state, cs, out);
                return;
            }
        }
    }

    /** Fixed end-of-round order: player DoT -> enemy DoT -> buffs -> cooldowns ->
     *  regen -> defend reaction commit -> next round intents. */
    private void endOfRound(GameState state, CombatState cs, List<String> out) {
        Player player = state.getPlayer();
        DamageCalculator calc = new DamageCalculator(state.getRng());

        for (StatusInstance s : player.tickStatuses()) {
            if (s.type() == StatusType.BLEED || s.type() == StatusType.BURN || s.type() == StatusType.POISON) {
                int dmg = calc.dotTick(s.power());
                player.takeDamage(dmg);
                String line = s.type().label() + " bites for " + dmg + " damage.";
                out.add(line);
                cs.log(line);
            }
            if (player.isDead()) {
                defeat(state, cs, out);
                return;
            }
        }
        for (Enemy enemy : cs.enemies()) {
            if (enemy.isDead()) {
                continue;
            }
            for (StatusInstance s : enemy.tickStatuses()) {
                if (s.type() == StatusType.BLEED || s.type() == StatusType.BURN || s.type() == StatusType.POISON) {
                    int dmg = calc.dotTick(s.power());
                    enemy.takeDamage(dmg);
                    String line = enemy.name() + " suffers " + dmg + " from " + s.type().label() + ".";
                    out.add(line);
                    cs.log(line);
                    if (enemy.isDead()) {
                        victory(state, cs, out);
                        return;
                    }
                }
            }
        }

        cs.tickTempDefense();
        cs.tickCooldowns();
        if (!player.isDead()) {
            player.restoreStamina(5);
            player.restoreAsh(2);
        }
        cs.commitDefendReaction();
        cs.clearDodgeReaction();
        cs.advanceRound();
        for (Enemy enemy : cs.enemies()) {
            chooseIntent(state, cs, enemy);
        }
    }

    private void onEnemyDamaged(GameState state, CombatState cs, Enemy enemy, List<String> out) {
        if (enemy.isDead() && cs.isOngoing()) {
            victory(state, cs, out);
        }
    }

    private void victory(GameState state, CombatState cs, List<String> out) {
        cs.setOutcome(CombatState.Outcome.VICTORY);
        Player player = state.getPlayer();

        List<String> fallen = new ArrayList<>();
        int xp = 0;
        for (Enemy enemy : cs.enemies()) {
            if (!enemy.isDead()) {
                continue;
            }
            fallen.add(enemy.name());
            xp += enemy.def().xp();
            state.addEvent(GameEvent.of(GameEvent.Type.ENEMY_DEFEATED, enemy.code()));
            if (enemy.def().boss()) {
                state.defeatBoss(enemy.code());
            }
            if (enemy.def().drops() != null) {
                for (EnemyDef.Drop drop : enemy.def().drops()) {
                    if (state.getRng().nextInt(100) >= drop.chance()) {
                        continue;
                    }
                    if (ASH_FRAGMENTS.equals(drop.itemCode())) {
                        cs.addFragments(drop.quantity());
                    } else {
                        cs.addPendingLoot(drop.itemCode(), drop.quantity());
                    }
                }
            }
        }

        String title = "VICTORY — " + String.join(" and ", fallen) + " falls.";
        out.add(title);
        cs.log(title);

        if (cs.fragmentsGained() > 0) {
            player.gainFragments(cs.fragmentsGained());
            out.add("Ash Fragments +" + cs.fragmentsGained() + " (now " + player.getFragments() + ").");
            state.markDirty();
        }
        if (xp > 0) {
            int levels = player.gainXp(xp);
            out.add("XP +" + xp + (levels > 0
                    ? " — LEVEL UP! Now Lv. " + player.getLevel() + " (resources fully restored)."
                    : " (" + player.getXp() + "/" + Player.xpToNext(player.getLevel()) + " to next level)."));
        }
        for (CombatState.PendingLoot loot : cs.pendingLoot()) {
            ItemDef def = content.items().find(loot.itemCode());
            String name = def == null ? loot.itemCode() : def.name();
            out.add("Dropped: " + name + " x" + loot.quantity()
                    + "  (inspect it before you take it — you are now in the loot screen)");
        }
        state.markDirty();
    }

    private void defeat(GameState state, CombatState cs, List<String> out) {
        cs.setOutcome(CombatState.Outcome.DEFEAT);
        String line = "Your knees buckle; the amber ember in your chest gutters. Darkness takes you.";
        out.add(line);
        cs.log(line);
        state.addEvent(GameEvent.of(GameEvent.Type.PLAYER_DIED, state.getCurrentLocationId()));
    }

    // --- free look actions --------------------------------------------------

    private String intentLine(CombatState cs) {
        StringBuilder sb = new StringBuilder();
        for (Enemy enemy : cs.enemies()) {
            if (enemy.isDead()) {
                continue;
            }
            EnemyAction intent = enemy.getCurrentIntent();
            if (intent == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(enemy.name()).append(" intends: ").append(intent.intent());
            // tag hints are appended only when the intent text does not already say it
            String text = intent.intent() == null ? "" : intent.intent().toLowerCase(Locale.ROOT);
            if (intent.is(EnemyAction.TAG_PARRYABLE) && !text.contains("parry")) {
                sb.append(" (Parryable)");
            } else if (intent.is(EnemyAction.TAG_SPELL) && !text.contains("spell")) {
                sb.append(" (Spell — cannot parry)");
            } else if (intent.is(EnemyAction.TAG_UNBLOCKABLE) && !text.contains("unblock")) {
                sb.append(" (UNBLOCKABLE — guarding will not save you)");
            } else if ("DEFEND".equals(intent.kind()) && !text.contains("guard")) {
                sb.append(" (guarding — damage reduced)");
            }
        }
        return sb.length() == 0 ? "No further intent." : sb.toString();
    }

    private List<String> inspectEnemy(CombatState cs) {
        List<String> out = new ArrayList<>();
        Enemy enemy = cs.firstEnemy();
        if (enemy == null) {
            return List.of("There is no enemy to inspect.");
        }
        out.add(enemy.name().toUpperCase() + " — HP " + enemy.getHp() + "/" + enemy.getMaxHp());
        out.add("Region: " + enemy.def().region() + (enemy.def().boss() ? " | BOSS" : ""));
        out.add("Attack: " + enemy.def().attack() + " | Defense: " + enemy.def().defense()
                + " | XP: " + enemy.def().xp());
        out.add("Lore: " + enemy.def().lore());
        out.add("Resistances: " + resistancesText(enemy));
        out.add("Weakness: " + (enemy.def().weaknesses() == null || enemy.def().weaknesses().isEmpty()
                ? "None known" : String.join(", ", enemy.def().weaknesses())));
        EnemyAction intent = enemy.getCurrentIntent();
        if (intent != null) {
            String parry;
            if (intent.is(EnemyAction.TAG_PARRYABLE)) {
                parry = "PARRYABLE — parry to negate and counter.";
            } else if (intent.is(EnemyAction.TAG_SPELL)) {
                parry = "Spell — parry fails; defend or interrupt.";
            } else if (intent.is(EnemyAction.TAG_UNBLOCKABLE)) {
                parry = "UNBLOCKABLE — neither defend nor parry works; out-damage it.";
            } else {
                parry = "Defendable, but not parryable.";
            }
            out.add("Current intent: " + intent.name() + " (\"" + intent.intent() + "\") — " + parry);
        }
        out.add("Counterplay: " + enemy.def().counterplayHint());
        if (!enemy.getStatuses().isEmpty()) {
            out.add("Status: " + enemy.getStatuses());
        } else {
            out.add("Status: none");
        }
        return out;
    }

    private String resistancesText(Enemy enemy) {
        if (enemy.def().resistances() == null || enemy.def().resistances().isEmpty()) {
            return "None known";
        }
        StringBuilder sb = new StringBuilder();
        enemy.def().resistances().forEach((element, value) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(element).append(" ").append((int) Math.round(value * 100)).append("%");
        });
        return sb.toString();
    }

    private List<String> inspectItem(GameState state, String arg) {
        if (arg == null || arg.isBlank()) {
            return List.of("Usage: inspect item <name> — always free, never spends your turn.");
        }
        ItemDef def = resolveInventoryItem(state, arg);
        if (def == null) {
            return List.of("You carry no such item.");
        }
        return ItemText.describe(def, state.getInventory(), state.getEquipment(), content.items());
    }

    private List<String> statusLines(GameState state, CombatState cs) {
        List<String> out = new ArrayList<>();
        Player p = state.getPlayer();
        EffectiveStats stats = stats(state, cs);
        out.add(state.getPlayer().getName() + " Lv." + p.getLevel() + "  HP " + p.getHp() + "/" + stats.maxHp()
                + "  ST " + p.getStamina() + "/" + stats.maxStamina()
                + "  ASH " + p.getAsh() + "/" + stats.maxAsh());
        out.add("Attack " + stats.attack() + " | Defense " + stats.defense()
                + (cs.tempDefenseBuff() > 0 ? " (incl. +" + cs.tempDefenseBuff() + " tonic)" : ""));
        out.add("Round " + cs.round() + " | Status: " + (p.getStatuses().isEmpty() ? "none" : p.getStatuses()));
        if (!cs.cooldowns().isEmpty()) {
            out.add("Cooldowns: " + cs.cooldowns());
        }
        for (Enemy enemy : cs.enemies()) {
            out.add(enemy.name() + ": " + (enemy.isDead() ? "defeated" : enemy.getHp() + "/" + enemy.getMaxHp() + " HP")
                    + (enemy.getStatuses().isEmpty() ? "" : " | " + enemy.getStatuses()));
        }
        return out;
    }

    private List<String> helpLines() {
        return List.of(
                "COMBAT COMMANDS (invalid input never spends a turn):",
                "  attack            0 stamina, 100% weapon damage",
                "  heavy             15 stamina + weapon modifier, 160% damage, staggers the enemy",
                "  defend            halves incoming damage this round, restores 15 stamina",
                "  dodge             " + DODGE_STAMINA_COST + " stamina, chance to evade each normal hit; never vs (UNBLOCKABLE)",
                "  parry             negates a (Parryable) attack and counters; fails vs spells/grabs",
                "  skill <name>      special technique — costs shown by 'status'",
                "  item <name>       use a consumable (costs the turn)",
                "  inspect enemy     traits, resistances, parryable? — free",
                "  inspect item <n>  full details before using — free",
                "  status | help | log   free, never spend your turn",
                "  flee              escape if allowed (never in boss fights); may fail and cost the turn"
        );
    }

    private List<String> logLines(CombatState cs) {
        if (cs.logLines().isEmpty()) {
            return List.of("The combat log is empty.");
        }
        return cs.logLines();
    }
}
