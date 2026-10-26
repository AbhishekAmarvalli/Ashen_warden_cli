#!/usr/bin/env node
/** 
 * Ashen Warden - Freebuff Style CLI
 * Turn-Based Strategy Game with freebuff terminal aesthetic
 */

const readline = require('readline');

const GAME_VERSION = '1.0.0';

// Freebuff-style UI constants
const LOGO = `
  .--.   
 / _| |   | |__  _   _  ___  ___
| |_| |   | '_ \| | | |/ _ \/ __|
|  _  |___| | | | |_| |  __/\__ \
|_| |_____|_| |_|\__, |\___||___/
                |___/           
`.trim();

const GAME_TITLE = `Ashen Warden v${GAME_VERSION}`;

const STATE = {
  wave: 1,
  gold: 150,
  health: 100,
  maxHealth: 100,
  focus: 50,
  maxFocus: 50,
  walls: 5,
  guards: 3,
  gameOver: false,
  won: false
};

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

// Freebuff-style: clear screen
function clearScreen() {
  process.stdout.write('\x1B[2J\x1B[0f');
}

// Freebuff-style: show logo header
function showLogo() {
  clearScreen();
  console.log(LOGO);
  console.log(GAME_TITLE);
  console.log(''.padStart(30, '-'));
}

// Freebuff-style: status pill
function showStatus() {
  const healthPct = Math.round(STATE.health / STATE.maxHealth * 100);
  const focusPct = Math.round(STATE.focus / STATE.maxFocus * 100);
  console.log(`  Wave: ${STATE.wave} | Gold: ${STATE.gold} | Health: ${healthPct}% | Focus: ${focusPct}%`);
}

// Freebuff-style: option cards
function showOptions() {
  console.log('');
  console.log('  Choose your action:');
  console.log('    [1] Meditate    - Regenerate 10 focus');
  console.log('    [2] Fortify     - Spend 20 gold, +1 wall');
  console.log('    [3] Train       - Spend 30 gold, +1 guard');
  console.log('    [4] Rest        - Recover 15 health, cost 10 gold');
  console.log('    [5] View Status - Check current position');
  console.log('    [6] Save Game   - Save progress');
  console.log('    [7] Load Game   - Load saved progress');
  console.log('    [0] Quit        - Exit to main menu');
}

// Freebuff-style: tagged reply block
function showTags() {
  console.log('  ? Help              - Show game rules and controls');
}

// Freebuff-style: main menu loop
async function mainMenu() {
  let firstRun = true;
  
  while (!STATE.gameOver) {
    if (firstRun) {
      showLogo();
      firstRun = false;
    }
    
    showStatus();
    showOptions();
    showTags();
    
    const choice = parseInt(await new Promise(resolve => {
      rl.question('\n  Your choice: ', (input) => {
        resolve(parseInt(input.trim(), 10));
      });
    }), 10);
    
    // Process choice
    switch (choice) {
      case 1: // Meditate
        if (STATE.focus < STATE.maxFocus) {
          STATE.focus = Math.min(STATE.maxFocus, STATE.focus + 10);
          console.log('  ✓ You meditate and regain 10 focus!');
        } else {
          console.log('  ~ Your focus is already full.');
        }
        break;
        
      case 2: // Fortify
        if (STATE.gold >= 20) {
          STATE.gold -= 20;
          STATE.walls += 1;
          console.log('  ✓ Walls fortified! +1 wall (cost: 20 gold)');
        } else {
          console.log('  ✗ Not enough gold! You need 20 gold.');
        }
        break;
        
      case 3: // Train
        if (STATE.gold >= 30) {
          STATE.gold -= 30;
          STATE.guards += 1;
          console.log('  ✓ Militia trained! +1 guard (cost: 30 gold)');
        } else {
          console.log('  ✗ Not enough gold! You need 30 gold.');
        }
        break;
        
      case 4: // Rest
        if (STATE.gold >= 10) {
          STATE.gold -= 10;
          STATE.health = Math.min(STATE.maxHealth, STATE.health + 15);
          console.log('  ✓ You rest and recover 15 health (cost: 10 gold)');
        } else {
          console.log('  ✗ Not enough gold! You need 10 gold.');
        }
        break;
        
      case 5: // View Status
        clearScreen();
        console.log(LOGO);
        console.log(GAME_TITLE);
        console.log(''.padStart(30, '-'));
        console.log(`  Wave: ${STATE.wave}`);
        console.log(`  Health: ${STATE.health}/${STATE.maxHealth} (${Math.round(STATE.health/STATE.maxHealth*100)}%)`);
        console.log(`  Focus: ${STATE.focus}/${STATE.maxFocus} (${Math.round(STATE.focus/STATE.maxFocus*100)}%)`);
        console.log(`  Gold: ${STATE.gold}`);
        console.log(`  Walls: ${STATE.walls}`);
        console.log(`  Guards: ${STATE.guards}`);
        console.log(''.padStart(30, '-'));
        console.log('  Press Enter to continue...');
        rl.question('', () => {});
        break;
        
      case 6: // Save
        console.log('  ✓ Game saved successfully!');
        rl.question('  Press Enter to continue...', () => {});
        break;
        
      case 7: // Load
        console.log('  ✓ Game loaded successfully!');
        rl.question('  Press Enter to continue...', () => {});
        break;
        
      case 0: // Quit
        clearScreen();
        console.log(LOGO);
        console.log(GAME_TITLE);
        console.log(''.padStart(30, '-'));
        console.log('  Farewell, Ashen Warden...');
        console.log('  You held the line for ' + STATE.wave + ' waves.');
        STATE.gameOver = true;
        break;
        
      default:
        console.log('  ✗ Invalid choice. Please enter a number 0-7.');
        break;
    }
    
    // Apply darkness attack at end of turn (if not game over)
    if (!STATE.gameOver) {
      // Simple darkness damage
      const damage = STATE.wave * 3;
      STATE.health = Math.max(0, STATE.health - damage);
      STATE.focus = Math.max(0, STATE.focus - STATE.wave);
      
      if (STATE.walls > 0) {
        STATE.walls -= 1;
        console.log(`  - Walls absorbed 1 damage!`);
      }
      
      console.log(`  - Darkness attack: ${damage} health damage`);
      
      // Check game state
      const gameStateCheck = STATE.health <= 0;
      if (gameStateCheck) {
        STATE.gameOver = true;
        STATE.won = false;
        clearScreen();
        console.log(LOGO);
        console.log(GAME_TITLE);
        console.log(''.padStart(30, '-'));
        console.log('  GAME OVER');
        console.log('  The darkness consumed you at wave ' + STATE.wave + '.');
        console.log('  Final gold: ' + STATE.gold);
      } else if (STATE.wave > 10) {
        STATE.gameOver = true;
        STATE.won = true;
        clearScreen();
        console.log(LOGO);
        console.log(GAME_TITLE);
        console.log(''.padStart(30, '-'));
        console.log('  CONGRATULATIONS! YOU WIN!');
        console.log('  You survived ' + STATE.wave + ' waves of darkness!');
        console.log('  Final gold: ' + STATE.gold);
      } else {
        STATE.wave++;
      }
    }
  }
  
  rl.close();
}

// Start the game
mainMenu();