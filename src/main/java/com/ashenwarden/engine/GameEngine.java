package com.ashenwarden.engine;

import com.ashenwarden.command.CommandParser;
import com.ashenwarden.command.ParsedCommand;
import com.ashenwarden.combat.CombatEngine;
import com.ashenwarden.combat.CombatState;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.persistence.SaveService;
import com.ashenwarden.persistence.SaveSlotInfo;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.quest.QuestManager;
import com.ashenwarden.ui.InteractiveMenu;
import com.ashenwarden.ui.KeyInput;
import com.ashenwarden.world.DialogueManager;
import com.ashenwarden.world.ExplorationService;
import com.ashenwarden.world.Location;
import com.ashenwarden.cli.TerminalRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the game loop, phase transitions, menu state and rendering glue.
 * Services do the work; this class sequences them and never touches SQL
 * directly (persistence goes through SaveService).
 */
public final class GameEngine {

    /** Menu state while a selection screen is open (null = no menu). */
    public static final class MenuContext {
        public enum Kind { INVENTORY, LOOT, DIALOGUE, EQUIP_CONFIRM, LOAD_LIST, QUIT, SETTINGS }

        public Kind kind;
        public final List<String> codes = new ArrayList<>();
        public final List<Integer> quantities = new ArrayList<>();
        public DialogueManager.DialogueView dialogue;
        public String pendingEquipCode;
        public List<SaveSlotInfo> slots = new ArrayList<>();
        public boolean showEquipment;
        public String lootTitle = "LOOT";

        public MenuContext(Kind kind) {
            this.kind = kind;
        }
    }

    private final ContentRepository content;
    private final TerminalRenderer r;
    private final SaveService saves;
    private final CommandParser parser = new CommandParser();
    private final com.ashenwarden.command.CommandDispatcher dispatcher;
    private final CombatEngine combat;
    private final ExplorationService exploration;
    private final QuestManager quests;
    private final DialogueManager dialogues;

    private final KeyInput in;

    private GameState state;
    private MenuContext menu;
    private RawPrompt rawPrompt;
    private Location.Encounter activeEncounter;
    private boolean running = true;
    private boolean dbAvailable;
    private String dbProblem;

    enum RawPrompt { SLOT_NAME, PLAYER_NAME }

    public GameEngine(ContentRepository content, TerminalRenderer renderer, SaveService saves,
                      CombatEngine combat, ExplorationService exploration, QuestManager quests,
                      DialogueManager dialogues, KeyInput in) {
        this.content = content;
        this.r = renderer;
        this.saves = saves;
        this.combat = combat;
        this.exploration = exploration;
        this.quests = quests;
        this.dialogues = dialogues;
        this.in = in;
        this.dispatcher = new com.ashenwarden.command.CommandDispatcher(this);
    }

    // --- database availability ---------------------------------------------
    public void refreshDatabaseStatus() {
        try {
            saves.initializeSchema();
            dbAvailable = true;
            dbProblem = null;
        } catch (RuntimeException e) {
            dbAvailable = false;
            dbProblem = e.getMessage() == null ? "Database unavailable." : e.getMessage();
        }
    }

    public boolean isDbAvailable() {
        return dbAvailable;
    }

    // --- main loop ----------------------------------------------------------
    public void run() {
        refreshDatabaseStatus();
        if (!useKeyboard()) {
            renderMainMenu();
        }
        while (running) {
            if (useKeyboard()) {
                // arrow-key boot screen; falls back to the loop below whenever
                // the context needs typing (slot names, sub-screens, combat)
                runInteractiveMainMenu();
                continue;
            }
            renderStatusLine();
            r.prompt();
            String line = readLine();
            if (line == null) {
                // EOF (Ctrl+D / piped input): leave cleanly, never spin
                r.blank();
                r.info("Input closed — returning to menu.");
                if (state != null) {
                    state.setPhase(GamePhase.MAIN_MENU);
                    state = null;
                    menu = null;
                    renderMainMenu();
                    continue;
                }
                break;
            }
            if (rawPrompt != null) {
                r.beginBlock(); // naming a slot/character is a turn too
                handleRawInput(line);
                r.blank();
                continue;
            }
            GamePhase phase = state == null ? GamePhase.MAIN_MENU : state.getPhase();
            ParsedCommand cmd = parser.parse(line, phase);
            CommandResult result = dispatcher.dispatch(state, cmd);
            renderResult(result);
            if (result.isQuitRequested()) {
                running = false;
            } else {
                r.blank();
                r.suggestFooter(suggestions());
            }
        }
        r.blank();
        r.dim("Farewell, Warden.");
    }

    /** True while a keyboard-driven main-menu turn is possible (degrades off). */
    private boolean keyboard = true;

    /** True when this turn belongs to the interactive boot screen. */
    private boolean useKeyboard() {
        return keyboard && in.supportsKeys() && state == null && menu == null && rawPrompt == null;
    }

    /** Labels of the boot menu; each one maps to a main-menu command. */
    private static final List<String> MAIN_MENU_LABELS =
            List.of("BEGIN JOURNEY", "CONTINUE", "HOW TO PLAY", "SETTINGS", "EXIT");

    /**
     * One interactive main-menu turn: draw the boot screen, then act on the
     * player's key choice. The command goes through the exact same dispatcher
     * as a typed line, so keyboard and typing can never diverge in behaviour.
     */
    private void runInteractiveMainMenu() {
        renderBootScreen();
        try {
            InteractiveMenu menuScreen = new InteractiveMenu(r, in, "MAIN MENU", MAIN_MENU_LABELS);
            InteractiveMenu.Outcome outcome = menuScreen.select(0);
            switch (outcome.kind()) {
                case SELECTED -> runMainMenuCommand(mainMenuCommand(outcome.index()));
                case HELP -> runMainMenuCommand(new ParsedCommand(ParsedCommand.Type.HELP, ""));
                case ESCAPE -> {
                    r.blank();
                    r.dim("Escape does nothing at the root — choose an option, or press ? for help.");
                    r.blank();
                }
                case CLOSED -> running = false;
            }
        } catch (IOException e) {
            // a keyboard failure must never strand the player: switch to typed commands
            keyboard = false;
            r.warn("Keyboard input stopped working (" + (e.getMessage() == null ? "I/O error" : e.getMessage())
                    + ") — falling back to typed commands.");
            renderMainMenu();
        }
    }

    /** The five boot-menu choices, in on-screen order. */
    private static ParsedCommand mainMenuCommand(int index) {
        return switch (index) {
            case 0 -> new ParsedCommand(ParsedCommand.Type.NEW, "");
            case 1 -> new ParsedCommand(ParsedCommand.Type.CONTINUE, "");
            case 2 -> new ParsedCommand(ParsedCommand.Type.HELP, "");
            case 3 -> new ParsedCommand(ParsedCommand.Type.SETTINGS, "");
            default -> new ParsedCommand(ParsedCommand.Type.QUIT, "");
        };
    }

    /** Runs one main-menu command through the normal dispatcher path. */
    private void runMainMenuCommand(ParsedCommand cmd) {
        CommandResult result = dispatcher.dispatch(state, cmd);
        renderResult(result);
        if (result.isQuitRequested()) {
            running = false;
            return;
        }
        r.blank();
    }

    /**
     * Frame above the interactive menu — logo bar, banner, tagline and the
     * database state. The menu panel itself draws last, so it can redraw
     * itself in place when the player presses an arrow key.
     */
    private void renderBootScreen() {
        r.blank();
        r.header();
        r.blank();
        r.banner("ASHEN WARDEN");
        r.subLine("A kingdom that could not die. An oath that could not end.");
        r.blank();
        if (!dbAvailable) {
            r.warn("! Database unavailable: " + dbProblem);
            r.dim(saves.guidance());
        } else {
            r.dim("MySQL connected. Save slots are ready.");
        }
        r.blank();
    }

    // --- turn framing -------------------------------------------------------

    /** Compact status above the prompt: save chips in play, MAIN MENU at the boot screen. */
    private void renderStatusLine() {
        if (state == null) {
            r.statusBar(List.of("MAIN MENU"));
            return;
        }
        List<String> chips = new ArrayList<>();
        if (state.getSlotName() != null && !state.getSlotName().isBlank()) {
            chips.add(state.getSlotName());
        }
        Location loc = content.world().find(state.getCurrentLocationId());
        if (loc != null) {
            chips.add(loc.name());
        }
        var player = state.getPlayer();
        var stats = com.ashenwarden.player.EffectiveStats.compute(
                player, state.getEquipment(), content.items());
        chips.add("HP " + player.getHp() + "/" + stats.maxHp());
        chips.add(state.getPhase().name());
        r.statusBar(chips);
    }

    /**
     * Recommended follow-up commands for the context the player is in, shown
     * as the 'Suggested:' footer under every reply. Always valid commands,
     * safe to type verbatim.
     */
    private List<String> suggestions() {
        if (state == null) {
            return MENU_SUGGESTIONS;
        }
        if (menu != null) {
            return switch (menu.kind) {
                case INVENTORY -> List.of("inspect 1", "compare 1", "unequip weapon", "back");
                case EQUIP_CONFIRM -> List.of("1", "back");
                case LOOT -> menu.codes.isEmpty()
                        ? List.of("leave")
                        : List.of("take all", "inspect 1", "leave");
                case DIALOGUE -> List.of("ask <topic>", "leave");
                case LOAD_LIST -> List.of("1", "back");
                case QUIT -> List.of("1", "3");
                case SETTINGS -> List.of("width 100", "back");
            };
        }
        return switch (state.getPhase()) {
            case MAIN_MENU -> List.of("new", "continue", "load", "settings");
            case EXPLORATION -> explorationSuggestions();
            case COMBAT -> List.of("attack", "heavy", "status", "flee");
            case GAME_OVER -> List.of("load", "quit");
            default -> List.of("help", "quit");
        };
    }

    private List<String> explorationSuggestions() {
        List<String> out = new ArrayList<>();
        out.add("look");
        Location loc = content.world().find(state.getCurrentLocationId());
        if (loc != null && loc.exits() != null && !loc.exits().isEmpty()) {
            out.add("go " + loc.exits().get(0).label());
        }
        out.add("inventory");
        out.add("rest");
        return out;
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private void handleRawInput(String line) {
        RawPrompt prompt = rawPrompt;
        rawPrompt = null;
        String value = line == null ? "" : line.strip();
        if (prompt == RawPrompt.SLOT_NAME && state != null) {
            // in-game "save" to a fresh slot: name it and commit immediately
            renderSingle(doSaveWithName(value));
            return;
        }
        switch (prompt) {
            case SLOT_NAME -> onSlotName(value);
            case PLAYER_NAME -> onPlayerName(value);
        }
    }

    private CommandResult doSaveWithName(String slotName) {
        if (slotName == null || slotName.isBlank()) {
            return CommandResult.rejected("Cancelled — no slot name given, nothing was saved.");
        }
        try {
            SaveService.SaveResult result = saves.save(state, slotName);
            refreshDatabaseStatus();
            return CommandResult.ok().line("Game saved to slot '" + result.slotName() + "' (id "
                    + result.saveId() + ") — commit complete.");
        } catch (RuntimeException e) {
            refreshDatabaseStatus();
            return CommandResult.rejected("SAVE DID NOT COMPLETE — " + e.getMessage());
        }
    }

    private void renderSingle(CommandResult result) {
        renderResult(result);
    }

    // --- rendering ----------------------------------------------------------
    /** Draws one reply as a tagged block: header rule, plain lines as prose,
     *  card blocks as bordered screens. */
    private void renderResult(CommandResult result) {
        if (!result.blocks().isEmpty()) {
            r.beginBlock();
        }
        for (CommandResult.Block block : result.blocks()) {
            if (block.isCard()) {
                r.card(block.cardTitle(), block.lines());
                continue;
            }
            for (String line : block.lines()) {
                r.narrative(line);
            }
        }
        if (state != null && state.getPhase() == GamePhase.COMBAT && state.getCombat() != null
                && state.getCombat().isOngoing()) {
            r.blank(); // air between the round log and the encounter frame
            renderCombatHeader();
            r.keyHints(List.of("[1] Attack", "[2] Heavy", "[3] Defend", "[4] Parry",
                    "[5] Skill", "[6] Item", "[7] Inspect enemy", "[8] Inspect item",
                    "[9] Flee", "[10] Dodge", "[0] Help"));
        }
        if (menu != null && menu.kind != MenuContext.Kind.SETTINGS) {
            r.blank(); // air between the screen and its command hints
            renderMenuOptions();
        }
        if (pendingAfterRender != null) {
            Runnable pending = pendingAfterRender;
            pendingAfterRender = null;
            pending.run();
        }
    }

    private void renderMenuOptions() {
        switch (menu.kind) {
            case INVENTORY -> r.hint("Commands: inspect <n> | compare <n> | equip <n> | unequip <slot> | back");
            case LOOT -> r.hint("Commands: inspect <n> | take <n> | take all | leave");
            case DIALOGUE -> r.hint("Choose with a number, 'ask <topic>', or 'leave'");
            case EQUIP_CONFIRM -> r.hint("Type 1 to confirm, or 'back' to cancel — nothing is lost either way.");
            case LOAD_LIST -> r.hint("Choose a slot number, or 'back'.");
            case QUIT -> r.hint("Choose an option — nothing happens until you pick one.");
            case SETTINGS -> { /* settings renders its own lines */ }
        }
        if (menu.kind != MenuContext.Kind.SETTINGS) {
            r.hint("Tip: 'help' explains this screen, 'back' closes it, 'quit' saves and exits.");
        }
    }

    private void renderCombatHeader() {
        CombatState cs = state.getCombat();
        var player = state.getPlayer();
        var stats = com.ashenwarden.player.EffectiveStats.compute(
                player, state.getEquipment(), content.items());
        List<String> body = new ArrayList<>();
        body.add(player.getName() + " Lv." + player.getLevel()
                + "   HP " + r.bar(player.getHp(), stats.maxHp(), 10) + " " + player.getHp() + "/" + stats.maxHp()
                + "   ST " + player.getStamina() + "/" + stats.maxStamina()
                + "   ASH " + player.getAsh() + "/" + stats.maxAsh());
        String weaponCode = state.getEquipment().equippedCode(com.ashenwarden.player.EquipmentSlot.WEAPON);
        body.add("Weapon: " + (weaponCode == null ? "bare hands"
                : content.item(weaponCode).name())
                + "    Crimson Vials: " + state.getInventory().quantityOf("crimson_vial")
                + "    Round: " + cs.round());
        for (com.ashenwarden.enemy.Enemy enemy : cs.enemies()) {
            if (enemy.isDead()) {
                continue;
            }
            body.add(enemy.name().toUpperCase() + "   "
                    + r.bar(enemy.getHp(), enemy.getMaxHp(), 10) + " " + enemy.getHp() + "/" + enemy.getMaxHp()
                    + (enemy.getStatuses().isEmpty() ? "" : "   Status: " + enemy.getStatuses()));
        }
        if (!player.getStatuses().isEmpty()) {
            body.add("You: " + player.getStatuses());
        }
        r.panel("ENCOUNTER", body);
    }

    /** Commands offered on the main menu, in the boot footer and after every menu turn. */
    private static final List<String> MENU_SUGGESTIONS =
            List.of("new", "continue", "settings", "help");

    /**
     * Boot screen: title art, then the menu as a card so the main menu is part
     * of the same tagged-block interface as the rest of the game. Database
     * state stays outside the card so its warning keeps its color.
     */
    public void renderMainMenu() {
        if (useKeyboard()) {
            return; // the interactive loop draws its own boot screen each turn
        }
        r.blank();
        r.header(); // logo bar, like the header of a web interface
        r.blank();
        r.banner("ASHEN WARDEN");
        r.subLine("A kingdom that could not die. An oath that could not end.");
        r.blank();
        List<String> body = new ArrayList<>();
        body.add("  [1] New Game");
        body.add("  [2] Continue");
        body.add("  [3] Load Slot");
        body.add("  [4] Settings");
        body.add("  [5] Exit");
        body.add("");
        body.add("  Type a number, or a command: new | continue | load | settings | help | quit");
        r.card("MAIN MENU", body);
        if (!dbAvailable) {
            r.warn("! Database unavailable: " + dbProblem);
            r.dim(saves.guidance());
        } else {
            r.dim("MySQL connected. Save slots are ready.");
        }
        r.blank();
        r.suggestFooter(MENU_SUGGESTIONS);
    }

    public boolean isMenuActive() {
        return menu != null;
    }

    public GameState currentState() {
        return state;
    }

    // --- command routing entry points (used by CommandDispatcher) -----------
    public CommandResult handleMainMenu(ParsedCommand cmd) {
        return MainMenuHandler.handle(this, cmd);
    }

    public CommandResult handlePlay(ParsedCommand cmd) {
        return PlayHandler.handle(this, cmd);
    }

    public CommandResult handleMenuCommand(ParsedCommand cmd) {
        return MenuHandler.handle(this, cmd);
    }

    // --- package-wid used services ------------------------------------------
    ContentRepository content() {
        return content;
    }

    TerminalRenderer renderer() {
        return r;
    }

    GameState state() {
        return state;
    }

    void setState(GameState state) {
        this.state = state;
    }

    MenuContext menu() {
        return menu;
    }

    void setMenu(MenuContext menu) {
        this.menu = menu;
    }

    SaveService saves() {
        return saves;
    }

    ExplorationService exploration() {
        return exploration;
    }

    QuestManager quests() {
        return quests;
    }

    DialogueManager dialogues() {
        return dialogues;
    }

    CombatEngine combat() {
        return combat;
    }

    void requestRawPrompt(RawPrompt prompt) {
        this.rawPrompt = prompt;
    }

    void setDbProblem(String problem) {
        this.dbProblem = problem;
    }

    Location.Encounter activeEncounter() {
        return activeEncounter;
    }

    void setActiveEncounter(Location.Encounter encounter) {
        this.activeEncounter = encounter;
    }

    void requestQuit() {
        this.running = false;
    }

    CommandParser parser() {
        return parser;
    }

    // =================== new game =========================================
    void onSlotName(String value) {
        if (value == null || value.isBlank()) {
            renderMainMenu();
            r.warn("A slot name is required. New Game cancelled.");
            return;
        }
        String slotName = value;
        if (slotName.length() > SaveService.MAX_SLOT_NAME) {
            r.warn("Slot names are limited to " + SaveService.MAX_SLOT_NAME + " characters. Pick a shorter one:");
            requestRawPrompt(RawPrompt.SLOT_NAME);
            return;
        }
        try {
            boolean taken = saves.listSlots().stream().anyMatch(s -> s.slotName().equalsIgnoreCase(slotName));
            if (taken) {
                r.warn("A slot named '" + slotName + "' already exists — choose 'Continue' to play it, or another name:");
                requestRawPrompt(RawPrompt.SLOT_NAME);
                return;
            }
        } catch (RuntimeException e) {
            r.error("Could not verify slot names: " + e.getMessage());
            renderMainMenu();
            return;
        }
        pendingSlotName = slotName;
        r.info("Welcome, Warden. Name your character (display name; lore identity is always the Warden).");
        requestRawPrompt(RawPrompt.PLAYER_NAME);
    }

    private String pendingSlotName;

    void onPlayerName(String value) {
        String playerName = (value == null || value.isBlank()) ? "Warden" : value.strip();
        if (playerName.length() > 40) {
            playerName = playerName.substring(0, 40);
        }
        String slotName = pendingSlotName;
        pendingSlotName = null;
        GameState fresh = new GameState(playerName, System.nanoTime());
        applyNewGame(fresh);

        // persistence first (slot creation), then play regardless, with a clear warning
        try {
            SaveService.SaveResult result = saves.save(fresh, slotName);
            r.success("Save slot '" + result.slotName() + "' created in MySQL (id " + result.saveId() + ").");
            refreshDatabaseStatus();
        } catch (RuntimeException e) {
            r.warn("The slot could not be written: " + e.getMessage());
            r.dim(saves.guidance());
            r.warn("You can still play — 'save' will retry once MySQL is reachable.");
        }
        state = fresh;
        state.setPhase(GamePhase.EXPLORATION);
        menu = null;
        r.blank();
        boolean firstLine = true;
        for (String line : content.prologueIntro().split("\n")) {
            if (firstLine && !line.isBlank()) {
                r.section(line); // story title: "PROLOGUE — THE TOMB OF COLD ASH"
                firstLine = false;
            } else {
                r.narrative(line);
            }
        }
        afterExplorationChange();
        checkEnterEncounter();
    }

    /** Starting kit and flags come from content/config.properties (validated). */
    private void applyNewGame(GameState fresh) {
        fresh.setChapter(content.startChapter());
        String start = content.world().startLocationCode();
        fresh.setCurrentLocationId(start);
        fresh.discover(start);
        if (content.world().get(start).sanctuary()) {
            fresh.setSanctuaryId(start);
        }
        fresh.getInventory().replaceAll(content.startInventory());
        String armor = content.startArmor();
        if (armor != null) {
            fresh.getEquipment().loadSnapshot(java.util.Map.of(
                    com.ashenwarden.player.EquipmentSlot.ARMOR, armor));
        }
        if (content.startQuest() != null) {
            // quest lines are printed by afterExplorationChange callers below
            newGameQuestLines = quests.startQuest(fresh, content.startQuest());
        }
        clampVitals(fresh);
        fresh.clearDirty();
    }

    private List<String> newGameQuestLines = List.of();

    // =================== exploration helpers ================================
    void afterExplorationChange() {
        // drain events raised by movement/dialogue and let quests react
        List<String> questLines = processEvents();
        for (String line : questLines) {
            r.narrative(line);
        }
        for (String line : newGameQuestLines) {
            r.narrative(line);
        }
        newGameQuestLines = List.of();
        for (String line : exploration.look(state)) {
            r.narrative(line);
        }
    }

    /** Drains events through the quest manager (bounded, acyclic). */
    List<String> processEvents() {
        List<String> out = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            List<com.ashenwarden.engine.GameEvent> batch = state.drainEvents();
            if (batch.isEmpty()) {
                break;
            }
            out.addAll(quests.process(state, batch));
        }
        out.addAll(quests.evaluatePassiveObjectives(state));
        for (com.ashenwarden.engine.GameEvent event : state.drainEvents()) {
            out.addAll(quests.process(state, List.of(event)));
        }
        return out;
    }

    void checkEnterEncounter() {
        if (state == null || state.getPhase() != GamePhase.EXPLORATION) {
            return;
        }
        Location.Encounter enc = exploration.enterEncounterAtCurrent(state);
        if (enc != null) {
            startEncounter(enc);
        }
    }

    void clampVitals(GameState s) {
        var stats = com.ashenwarden.player.EffectiveStats.compute(
                s.getPlayer(), s.getEquipment(), content.items());
        if (s.getPlayer().getHp() > stats.maxHp()) {
            s.getPlayer().setHp(stats.maxHp());
        }
        if (s.getPlayer().getStamina() > stats.maxStamina()) {
            s.getPlayer().setStamina(stats.maxStamina());
        }
        if (s.getPlayer().getAsh() > stats.maxAsh()) {
            s.getPlayer().setAsh(stats.maxAsh());
        }
    }

    // =================== combat glue ========================================
    void startEncounter(Location.Encounter enc) {
        for (String line : beginEncounter(enc)) {
            r.info(line);
        }
    }

    /** Prepares an encounter without printing (handlers merge the lines). */
    List<String> beginEncounter(Location.Encounter enc) {
        menu = null; // no menu may survive into combat
        activeEncounter = enc;
        boolean boss = content.enemy(enc.enemyCode()).boss();
        return combat.start(state, enc.code(), List.of(enc.enemyCode()), !boss);
    }

    CommandResult combatAction(ParsedCommand cmd) {
        CombatEngine.ActionType type = mapCombat(cmd);
        if (type == null) {
            return CommandResult.rejected("That command is not available in combat. Type 'help'.");
        }
        // a number key selects the action; only typed words carry a name argument
        String arg = cmd.type() == ParsedCommand.Type.SELECT ? "" : cmd.arg();
        CombatEngine.Turn turn = combat.act(state, type, arg);
        CommandResult out = CommandResult.ok().lines(turn.lines());
        if (!turn.turnConsumed()) {
            return out; // invalid/look actions never spend the turn
        }
        CombatState cs = state.getCombat();
        switch (cs.outcome()) {
            case VICTORY -> {
                if (activeEncounter != null) {
                    exploration.markEncounterDone(state, activeEncounter);
                    activeEncounter = null;
                }
                List<String> questLines = processEvents();
                out.lines(questLines);
                autosaveSilently(out);
                if (cs.pendingLoot().isEmpty()) {
                    closeCombat();
                    out.phase(GamePhase.EXPLORATION);
                } else {
                    out.cardLines(openLootMenu());
                    out.phase(GamePhase.LOOT);
                }
            }
            case DEFEAT -> {
                out.lines(handleDeath());
                out.phase(GamePhase.EXPLORATION);
            }
            case ESCAPED -> {
                closeCombat();
                out.line("You slip back into the shadows of "
                        + content.world().get(state.getCurrentLocationId()).name() + ".");
                out.phase(GamePhase.EXPLORATION);
            }
            case ONGOING -> { /* header rendered by renderResult */ }
        }
        return out;
    }

    private void closeCombat() {
        state.setCombat(null);
        activeEncounter = null;
        state.setPhase(GamePhase.EXPLORATION);
    }

    void closeCombatState() {
        closeCombat();
    }

    /** Save used by the quit menu: only reports success after commit. */
    CommandResult saveNowForQuit() {
        return commitSave(null);
    }

    private CombatEngine.ActionType mapCombat(ParsedCommand cmd) {
        return switch (cmd.type()) {
            case ATTACK -> CombatEngine.ActionType.ATTACK;
            case HEAVY -> CombatEngine.ActionType.HEAVY;
            case DEFEND -> CombatEngine.ActionType.DEFEND;
            case DODGE -> CombatEngine.ActionType.DODGE;
            case PARRY -> CombatEngine.ActionType.PARRY;
            case SKILL -> CombatEngine.ActionType.SKILL;
            case ITEM -> CombatEngine.ActionType.ITEM;
            case FLEE -> CombatEngine.ActionType.FLEE;
            case INSPECT_ENEMY -> CombatEngine.ActionType.INSPECT_ENEMY;
            case INSPECT_ITEM -> CombatEngine.ActionType.INSPECT_ITEM;
            case STATUS -> CombatEngine.ActionType.STATUS;
            case HELP -> CombatEngine.ActionType.HELP;
            case LOG -> CombatEngine.ActionType.LOG;
            case SELECT -> numberedCombatAction(cmd.number());
            default -> null;
        };
    }

    /**
     * The combat number keys printed in the action bar, so a player never has
     * to memorize a word to act: 1..9 and 0 as advertised, 10 = dodge.
     */
    private static CombatEngine.ActionType numberedCombatAction(int number) {
        return switch (number) {
            case 1 -> CombatEngine.ActionType.ATTACK;
            case 2 -> CombatEngine.ActionType.HEAVY;
            case 3 -> CombatEngine.ActionType.DEFEND;
            case 4 -> CombatEngine.ActionType.PARRY;
            case 5 -> CombatEngine.ActionType.SKILL;
            case 6 -> CombatEngine.ActionType.ITEM;
            case 7 -> CombatEngine.ActionType.INSPECT_ENEMY;
            case 8 -> CombatEngine.ActionType.INSPECT_ITEM;
            case 9 -> CombatEngine.ActionType.FLEE;
            case 10 -> CombatEngine.ActionType.DODGE;
            case 0 -> CombatEngine.ActionType.HELP;
            default -> null;
        };
    }

    /** Defeat: lose a fraction of fragments at the death location (recoverable once),
     *  respawn at the last sanctuary. Milestones (bosses/quests/memories) are kept. */
    private List<String> handleDeath() {
        com.ashenwarden.player.Player player = state.getPlayer();
        List<String> out = new ArrayList<>();
        String deathLocation = state.getCurrentLocationId();
        int loss = player.getFragments() * 3 / 10;
        if (loss > 0) {
            player.setFragments(player.getFragments() - loss);
            player.setLostFragments(loss, deathLocation);
        }
        String respawn = state.getSanctuaryId() != null ? state.getSanctuaryId()
                : content.world().startLocationCode();
        state.setCurrentLocationId(respawn);
        state.discover(respawn);
        player.setHp(Integer.MAX_VALUE);
        player.setStamina(Integer.MAX_VALUE);
        player.setAsh(Integer.MAX_VALUE);
        player.clearStatuses();
        state.setCombat(null);
        activeEncounter = null;
        state.setPhase(GamePhase.EXPLORATION);
        state.markDirty();

        out.add("The ember in your chest flares — and you awaken where you last rested: "
                + content.world().get(respawn).name() + ".");
        if (loss > 0) {
            out.add("You dropped " + loss + " Ash Fragments where you fell ("
                    + content.world().get(deathLocation).name() + "). 'search' there to recover them once.");
        }
        out.add("Boss victories, opened routes, memories and completed quests are untouched.");
        autosaveSilentlyInto(out);
        return out;
    }

    // =================== persistence ========================================
    CommandResult doSave() {
        if (state.getPhase() == GamePhase.COMBAT || state.getPhase() == GamePhase.LOOT) {
            return CommandResult.rejected("You cannot save during combat. Finish or flee the encounter first.");
        }
        if (!isDbAvailable()) {
            return CommandResult.rejected("Save failed: the database is unreachable.\n" + saves.guidance());
        }
        if (state.isPersisted()) {
            return commitSave(null);
        }
        requestRawPrompt(RawPrompt.SLOT_NAME);
        return CommandResult.ok().line("Name the save slot for this playthrough (1-40 characters):");
    }

    private CommandResult commitSave(String requestedName) {
        try {
            SaveService.SaveResult result = saves.save(state, requestedName);
            refreshDatabaseStatus();
            return CommandResult.ok().line("Game saved to slot '" + result.slotName() + "' (id "
                    + result.saveId() + ") — commit complete.");
        } catch (RuntimeException e) {
            refreshDatabaseStatus();
            return CommandResult.rejected("SAVE DID NOT COMPLETE — " + e.getMessage());
        }
    }

    private void autosaveSilently(CommandResult out) {
        if (!isDbAvailable() || !state.isPersisted()) {
            return;
        }
        try {
            saves.save(state, null);
            out.line("[autosaved]");
        } catch (RuntimeException e) {
            out.line("[autosave failed: " + e.getMessage() + "]");
        }
    }

    private void autosaveSilentlyInto(List<String> out) {
        if (!isDbAvailable() || !state.isPersisted()) {
            return;
        }
        try {
            saves.save(state, null);
            out.add("[autosaved]");
        } catch (RuntimeException e) {
            out.add("[autosave failed: " + e.getMessage() + "]");
        }
    }

    CommandResult doLoad(int saveId) {
        try {
            SaveService.LoadedGame loaded = saves.load(saveId);
            state = loaded.state();
            menu = null;
            activeEncounter = null;
            state.setPhase(GamePhase.EXPLORATION);
            CommandResult out = CommandResult.ok();
            out.line("Loaded slot '" + state.getSlotName() + "' — " + state.getPlayer().getName()
                    + " at " + content.world().get(state.getCurrentLocationId()).name() + ".");
            for (String note : loaded.notes()) {
                out.line(note);
            }
            for (String line : exploration.look(state)) {
                out.line(line);
            }
            // refreshDatabaseStatus not needed for loads
            checkEnterEncounterAfterDispatch(out);
            return out;
        } catch (RuntimeException e) {
            return CommandResult.rejected("Load failed: " + e.getMessage());
        }
    }

    private void checkEnterEncounterAfterDispatch(CommandResult out) {
        // encounter lines are printed directly by startEncounter; the returned
        // result is rendered first, so defer via a marker line order:
        pendingAfterRender = this::checkEnterEncounter;
    }

    private Runnable pendingAfterRender;

    // =================== menu opening ======================================
    /** Opens the inventory/equipment menu (inspection-first: list + inspect before equip). */
    CommandResult openInventoryMenu(boolean showEquipmentHeader) {
        MenuContext ctx = new MenuContext(MenuContext.Kind.INVENTORY);
        ctx.showEquipment = showEquipmentHeader;
        menu = ctx;
        return CommandResult.ok().cardLines(buildInventoryLines());
    }

    /** Rebuilt from live inventory each time, so lists are never stale after equip/take. */
    List<String> buildInventoryLines() {
        List<String> out = new ArrayList<>();
        MenuContext ctx = menu;
        boolean showEquipment = ctx != null && ctx.showEquipment;
        out.add(showEquipment ? "EQUIPMENT & INVENTORY" : "INVENTORY — inspect an item before choosing");
        if (showEquipment) {
            var eq = state.getEquipment();
            out.add("  Weapon:    " + nameFor(eq.equippedCode(EquipmentSlot.WEAPON)));
            out.add("  Armor:     " + nameFor(eq.equippedCode(EquipmentSlot.ARMOR)));
            out.add("  Accessory1: " + nameFor(eq.equippedCode(EquipmentSlot.ACCESSORY_1)));
            out.add("  Accessory2: " + (eq.isAccessory2Unlocked()
                    ? nameFor(eq.equippedCode(EquipmentSlot.ACCESSORY_2)) : "(locked)"));
            out.add("  Unequip with: unequip weapon|armor|accessory1|accessory2");
        }
        if (ctx == null || ctx.codes.isEmpty()) {
            // repopulate from live inventory
            if (ctx != null) {
                ctx.codes.clear();
                ctx.codes.addAll(state.getInventory().snapshot().keySet());
            }
        } else {
            ctx.codes.clear();
            ctx.codes.addAll(state.getInventory().snapshot().keySet());
        }
        if (ctx == null || ctx.codes.isEmpty()) {
            out.add("  (empty)");
            return out;
        }
        var eqSlots = state.getEquipment();
        for (int i = 0; i < ctx.codes.size(); i++) {
            String code = ctx.codes.get(i);
            var def = content.items().find(code);
            if (def == null) {
                continue;
            }
            boolean equipped = (def.isEquipable()) && isEquipped(code);
            out.add("  [" + (i + 1) + "] " + com.ashenwarden.cli.ItemText.row(def, state.getInventory())
                    + (equipped ? "   [EQUIPPED]" : ""));
        }
        out.add("  (" + ctx.codes.size() + " distinct items | Ash Fragments: "
                + state.getPlayer().getFragments() + ")");
        return out;
    }

    private boolean isEquipped(String code) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (code.equals(state.getEquipment().equippedCode(slot))) {
                return true;
            }
        }
        return false;
    }

    private String nameFor(String code) {
        return code == null ? "-" : content.items().find(code).name();
    }

    /** Combat drops enter the loot screen (inspect each item before taking). */
    List<String> openLootMenu() {
        CombatState cs = state.getCombat();
        MenuContext ctx = new MenuContext(MenuContext.Kind.LOOT);
        ctx.lootTitle = "LOOT — the fallen drop:";
        for (CombatState.PendingLoot loot : cs.pendingLoot()) {
            ctx.codes.add(loot.itemCode());
            ctx.quantities.add(loot.quantity());
        }
        menu = ctx;
        List<String> out = new ArrayList<>();
        out.add("The fallen drop:");
        out.addAll(lootRows());
        return out;
    }

    /** Container loot (chests/urns) enters the same loot screen. */
    List<String> openLootFromInteractable(List<Location.Interactable.Loot> loot) {
        MenuContext ctx = new MenuContext(MenuContext.Kind.LOOT);
        ctx.lootTitle = "LOOT — you found:";
        for (Location.Interactable.Loot entry : loot) {
            ctx.codes.add(entry.itemCode());
            ctx.quantities.add(entry.quantity());
        }
        menu = ctx;
        List<String> out = new ArrayList<>();
        out.add("You found:");
        out.addAll(lootRows());
        return out;
    }

    List<String> lootRows() {
        List<String> out = new ArrayList<>();
        if (menu == null || menu.codes.isEmpty()) {
            out.add("  (nothing)");
            return out;
        }
        for (int i = 0; i < menu.codes.size(); i++) {
            var def = content.items().find(menu.codes.get(i));
            String name = def == null ? menu.codes.get(i) : def.name();
            out.add("  [" + (i + 1) + "] " + name + " x" + menu.quantities.get(i)
                    + (def == null ? "" : "  (" + def.rarity().label() + ")"));
        }
        return out;
    }

    // =================== milestones ========================================
    /** Autosave after major milestones (quest completion, rest, boss, memory). */
    void autoSaveAfterMilestone(CommandResult out) {
        if (!isDbAvailable() || state == null || !state.isPersisted()) {
            return;
        }
        try {
            saves.save(state, null);
            out.line("[autosaved]");
        } catch (RuntimeException e) {
            out.line("[autosave failed: " + e.getMessage() + "]");
        }
    }
}

