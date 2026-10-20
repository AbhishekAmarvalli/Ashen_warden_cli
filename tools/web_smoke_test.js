/**
 * Smoke test for the ASHEN WARDEN browser front-end.
 *
 *   node tools/web_smoke_test.js <page.html> <captured-stream.txt>
 *
 * Loads the page's inline script in a sandbox with DOM stubs, replays real
 * captured game output as SSE events, then asserts that
 *   - the log renders the streamed game text as styled markup,
 *   - suggestion and menu-option chips are built with clean labels,
 *   - every chip is clickable and a click posts a command,
 *   - the keyboard cursor (arrow keys + Enter) presses chips too.
 * Exit code 0 = all checks passed.
 */
'use strict';

const fs = require('fs');
const vm = require('vm');

const pageFile = process.argv[2];
const sampleFile = process.argv[3];
if (!pageFile || !sampleFile) {
  console.error('usage: node tools/web_smoke_test.js <page.html> <stream.txt>');
  process.exit(2);
}

const html = fs.readFileSync(pageFile, 'utf8');
const start = html.indexOf('<script>') + '<script>'.length;
const end = html.indexOf('</script>', start);
const script = html.slice(start, end);

const CR = String.fromCharCode(13);
const LF = String.fromCharCode(10);
const TAG = String.fromCharCode(0x25c6) + ' ASHEN WARDEN'; // ◆ ASHEN WARDEN reply tag

function makeSandbox() {
  const chipsEl = {
    children: [],
    appendChild(button) { this.children.push(button); },
  };
  // real DOM clears children when innerHTML is reset
  Object.defineProperty(chipsEl, 'innerHTML', {
    get() { return this._html || ''; },
    set(v) { this._html = v; if (v === '') this.children.length = 0; },
  });
  const elements = {
    log: {innerHTML: '', scrollTop: 0, scrollHeight: 900, clientHeight: 400},
    chips: chipsEl,
    cmd: {value: '', focus() {}},
    form: {},
    send: {},
    statusbar: {textContent: ''},
  };
  const posted = [];
  const handlers = {};
  const sandbox = {
    elements,
    posted,
    document: {
      activeElement: null,
      getElementById(id) { return elements[id]; },
      createElement() {
        return {
          className: '', textContent: '', onclick: null,
          click() { if (this.onclick) this.onclick(); },
        };
      },
      addEventListener(type, fn) { handlers[type] = fn; },
    },
    EventSource: class {
      constructor(url) { sandbox.source = this; this.url = url; }
    },
    fetch(url, options) {
      posted.push(String((options && options.body) || ''));
      return Promise.resolve({ok: true});
    },
    console,
    encodeURIComponent,
    decodeURIComponent,
  };
  sandbox.key = k => {
    if (handlers.keydown) handlers.keydown({key: k, preventDefault() {}});
  };
  vm.createContext(sandbox);
  vm.runInContext(script, sandbox, {filename: 'page-inline-script'});
  return sandbox;
}

function play(sandbox, text) {
  sandbox.source.onmessage({data: text});
}

function labels(sandbox) {
  return sandbox.elements.chips.children.map(b => b.textContent);
}

const failures = [];
function check(name, condition) {
  console.log((condition ? 'PASS ' : 'FAIL ') + name);
  if (!condition) failures.push(name);
}

const raw = fs.readFileSync(sampleFile, 'utf8').split(CR).join('');
const BOX_CHARS = /[─-╿╭-╮╭-╯]/; // any box-drawing character

// 1) full session: the log must render the game text with styled markup
const main = makeSandbox();
for (const event of parseEvents(raw)) play(main, event);
check('log renders streamed game text',
  main.elements.log.innerHTML.indexOf('TOMB OF COLD ASH') >= 0
  || main.elements.log.innerHTML.indexOf('M A I N') >= 0);
check('ANSI colors become styled markup',
  main.elements.log.innerHTML.indexOf('<span style=') >= 0);
check('suggestion chips are built', labels(main).length > 0);
check('chip labels are clean (no border junk)',
  labels(main).every(t => !BOX_CHARS.test(t)));
check('every chip is clickable',
  main.elements.chips.children.every(b => typeof b.onclick === 'function'));
// exploration moment: chips must carry the Suggested footer's real commands
const stripAnsi = s => s.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '');
const explore = makeSandbox();
let seen = '';
for (const event of parseEvents(raw)) {
  play(explore, event);
  seen += event;
  if (stripAnsi(seen).indexOf('go the passage north') >= 0) break;
}
check('suggestions include a known command',
  labels(explore).some(t => t === 'look' || t.indexOf('inventory') >= 0 || t === 'help'));

if (main.elements.chips.children.length > 0) {
  main.elements.chips.children[0].onclick();
  check('clicking a chip posts the command',
    main.posted.length === 1 && main.posted[0].indexOf('cmd=') === 0);
  check('sent command shows as a player bubble',
    main.elements.log.innerHTML.indexOf('msg player') >= 0);
} else {
  check('clicking a chip posts the command', false);
  check('sent command shows as a player bubble', false);
}
const turns = (main.elements.log.innerHTML.match(/msg agent/g) || []).length;
check('replies render as chat messages, not a raw wall', turns >= 1);
check('each reply becomes its own chat turn', turns >= 3);

// 2) boot menu only: numbered options must become clickable chips.
// The capture's first chunk is the history dump at connect time; cut the raw
// stream at the first REPLY tag so the menu screen is the last (only) screen.
// The boot logo bar ("◆ ASHEN WARDEN · THE LAST EMBER") contains the tag
// substring too, so skip occurrences followed by the subtitle.
const plainRaw = raw.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '');
const LOGO = TAG + ' \u00b7 THE LAST EMBER';
let cutAt = -1;
for (let from = 0; cutAt < 0;) {
  const i = plainRaw.indexOf(TAG, from);
  if (i < 0) break;
  if (plainRaw.substr(i, LOGO.length) !== LOGO) cutAt = i;
  from = i + TAG.length;
}
const bootOnly = cutAt >= 0 ? plainRaw.slice(0, cutAt) : plainRaw;
const menu = makeSandbox();
for (const event of parseEvents(bootOnly)) play(menu, event);
const menuLabels = labels(menu);
if (process.env.DEBUG_CHIPS) console.log('DEBUG menu labels:', JSON.stringify(menuLabels));
check('menu option chips appear', menuLabels.some(t => t.indexOf('[1]') === 0));
check('menu chips carry no border junk',
  menuLabels.every(t => !BOX_CHARS.test(t)));
const numbered = menu.elements.chips.children.find(b => b.textContent.indexOf('[1]') === 0);
if (numbered) {
  numbered.onclick();
  check('option chip sends its number',
    menu.posted.length === 1 && menu.posted[0] === 'cmd=1');
} else {
  check('option chip sends its number', false);
}

// 3) keyboard cursor: arrows move the selection, Enter presses the chip
const key = makeSandbox();
for (const event of parseEvents(bootOnly)) play(key, event);
const kidCount = key.elements.chips.children.length;
key.key('ArrowRight');
const firstLit = kidCount > 0
  && key.elements.chips.children[0].className.indexOf('cursor') >= 0;
check('ArrowRight lights the first chip', firstLit);
key.key('ArrowRight');
const secondLit = kidCount > 1
  && key.elements.chips.children[1].className.indexOf('cursor') >= 0
  && key.elements.chips.children[0].className.indexOf('cursor') < 0;
check('ArrowRight moves the cursor on', secondLit);
key.key('Enter');
check('Enter presses the selected chip',
  key.posted.length === 1 && key.posted[0] === 'cmd=2');

/** Splits a captured event stream into events the way EventSource would. */
function parseEvents(text) {
  const events = [];
  let current = [];
  for (const line of text.split(LF)) {
    if (line === '') {
      if (current.length) {
        events.push(current.join(LF));
        current = [];
      }
      continue;
    }
    if (line.indexOf('data: ') === 0) {
      current.push(line.slice(6));
    } else if (line === 'data:') {
      current.push('');
    }
    // comment lines (": keep-alive") are ignored
  }
  if (current.length) events.push(current.join(LF));
  return events;
}

console.log(failures.length === 0
  ? 'ALL CHECKS PASSED (' + labels(main).length + ' chips from a live session)'
  : failures.length + ' CHECK(S) FAILED');
process.exit(failures.length === 0 ? 0 : 1);
