# ASHEN WARDEN: THE LAST EMBER

An interactive, turn-based gothic dark-fantasy RPG for the terminal.
Java 21 · Maven · JLine 3 keyboard UI · MySQL 8 (JDBC) · no AI, no network.

> *"A kingdom that could not die. An oath that could not end."*

You are the Ashen Warden, a forgotten warrior bound to an ancient oath.
Explore ruined settlements and cursed cathedrals, fight turn-based battles,
uncover forgotten history, and confront the source of the curse.

## Requirements

- **JDK 21 or newer** (`java -version`)
- **MySQL 8+** — optional: the game starts without it and plays normally,
  saving/loading is paused with recovery instructions until it is reachable
- A modern terminal (Windows Terminal, PowerShell, CMD, bash) — keyboard
  menus need a real console; everywhere else the typed-command menu is used

## Run it

```bash
# build once (Maven wrapper-free: any Maven 3.8+, or tools/maven if present)
mvn package

# play
java -jar target/ashen-warden.jar
```

Windows CMD shortcut (sets UTF-8, checks Java and the jar first):

```bat
play.bat
```

### Launch flags

| Flag | Effect |
|---|---|
| `--plain` | ASCII output, no ANSI escapes, typed-command menu (screen-reader friendly) |
| `--no-color` | Colors off, layout unchanged |
| `--no-keys` | Force typed-command menus even in a real console |
| `--width <40–120>` | Wrap width (default: detected terminal width) |
| `--web [port]` | Play in the browser (local server, default `http://127.0.0.1:8777/`) |
| `--help` | Usage text |

`NO_COLOR` is honored automatically.

## Controls

The boot screen is a keyboard-navigated menu:

| Key | Action |
|---|---|
| `↑` / `↓` (Tab, PageUp/PageDown) | Move the highlight |
| `Enter` | Choose the highlighted option |
| `1`–`5` | Jump straight to an option |
| `?` | Show the help screen |
| `Escape` | Leave the current screen (nothing is lost) |
| `Ctrl+C` / `Ctrl+D` | Quit cleanly |

In play you can always type commands (`look`, `go north`, `inventory`,
`attack`, `save`...) — every screen also accepts its printed numbers, and
`help` explains wherever you are. Invalid input never costs a turn.

## Database configuration

Credentials come from the environment only (nothing is committed):

```bash
export DB_URL='jdbc:mysql://localhost:3306/ashen_warden?useSSL=false&characterEncoding=utf-8'
export DB_USER=root
export DB_PASSWORD=...
```

Schema is created and migrated automatically on first launch
(`src/main/resources/db/migration/V*__*.sql`, Flyway-style `schema_version`).

## Tests

```bash
mvn test
```

Combat math, command parsing, inventory/equipment, the keyboard UI and the
save round trip are covered. The save/load integration tests skip (never
silently pass) when MySQL is not reachable.

## Project layout

```
src/main/java/com/ashenwarden/
  Main.java          entry point, flags, terminal/input selection
  engine/            game loop, phases, menus, state
  command/           parser + phase-policy dispatcher
  ui/                keyboard input (JLine) and interactive menus
  cli/               pure rendering (panels, banners, bars, ANSI/ASCII)
  combat/            turn resolution, damage formula, statuses
  enemy/ item/ player/ quest/ world/ story/   domain rules
  content/           JSON content loading + startup validation
  persistence/       JDBC, migrations, save transactions
  web/               optional browser front-end
docs/                architecture, command reference, story bible, test plan
```

See `docs/ARCHITECTURE.md` for the module map and `docs/COMMAND_REFERENCE.md`
for the full gameplay command list.

## Roadmap

- Node.js `npx ashen-warden` launcher (thin wrapper that checks for Java and
  runs the jar — the engine stays 100% Java)
- `index.js` at the repository root is the **legacy Node prototype** of the
  game, kept for reference; it is not the Java game.
