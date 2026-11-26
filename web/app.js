import { DIFFICULTIES, GAME_DEFAULTS, Game, isPeer } from './sudoku.js';

// ---------- Einstellungen (Auswahl aus Settings.kt) ----------

const t = (key, label, def, hint) => ({ key, label, kind: 'toggle', def, hint });
const c = (key, label, def, options, hint) => ({ key, label, kind: 'choice', def, options, hint });
const s = (key, label, def, min, max, step, suffix = '', hint) => ({ key, label, kind: 'slider', def, min, max, step, suffix, hint });

const CATEGORIES = [
  {
    title: 'Farben', defs: [
      c('theme', 'Modus', 'system', [['system', 'Auto'], ['light', 'Hell'], ['dark', 'Dunkel']]),
      s('accent', 'Akzent-Farbton', 258, 0, 360, 1, '°'),
      c('boxPattern', 'Farbmuster', 'quilt', [['quilt', 'Quilt'], ['check', 'Schachbrett'], ['rows', 'Reihen'], ['cols', 'Spalten'], ['one', 'Einfarbig']]),
      c('tint', 'Blockfarben', '1.0', [['1.0', 'Bunt'], ['0.4', 'Sanft'], ['0.0', 'Aus']]),
      s('hueShift', 'Farben drehen', 0, 0, 359, 1, '°'),
      c('errHue', 'Fehlerfarbe', '25', [['25', 'Rot'], ['350', 'Magenta'], ['60', 'Orange'], ['300', 'Violett']], 'Falls Rot schwer zu erkennen ist'),
    ],
  },
  {
    title: 'Markierung', defs: [
      c('hlMode', 'Hervorheben', 'all', [['all', 'Zeile, Spalte, Block'], ['lines', 'Zeile, Spalte'], ['off', 'Aus']]),
      t('hlSame', 'Gleiche Ziffern markieren', true),
      t('hlNotes', 'Passende Notizen hervorheben', true),
    ],
  },
  {
    title: 'Eingabe', defs: [
      c('inputMode', 'Eingabemodus', 'cell', [['cell', 'Feld zuerst'], ['digit', 'Ziffer zuerst']], 'Bei „Ziffer zuerst“ wählst du erst die Ziffer und tippst dann Felder an'),
      c('autoAdvance', 'Automatisch weiterspringen', 'off', [['off', 'Aus'], ['next', 'Immer'], ['correct', 'Nur bei richtig']]),
      t('autoNotes', 'Notizen automatisch aufräumen', true, 'Entfernt die gesetzte Ziffer aus den Notizen der Nachbarfelder'),
      c('doneKeys', 'Fertige Ziffern', 'grey', [['grey', 'Abgrauen'], ['hide', 'Ausblenden']]),
      c('padLayout', 'Ziffernfeld', 'row', [['row', 'Eine Reihe'], ['grid', '3 × 3']]),
    ],
  },
  {
    title: 'Fehler und Tipps', defs: [
      c('errMode', 'Fehler erkennen', 'solution', [['solution', 'Gegen Lösung'], ['rules', 'Nur Regelverstöße']]),
      s('maxMistakes', 'Fehlerlimit', 0, 0, 10, 1, '', '0 = unbegrenzt, danach ist das Spiel verloren'),
      t('showMist', 'Fehlerzähler anzeigen', false),
      s('hintLimit', 'Tipp-Limit', 0, 0, 10, 1, '', '0 = unbegrenzt'),
      c('hintMode', 'Tipp-Verhalten', 'fill', [['fill', 'Ziffer eintragen'], ['show', 'Feld nur zeigen']]),
    ],
  },
  {
    title: 'Oberfläche', defs: [
      t('showTimer', 'Stoppuhr anzeigen', true),
      c('toolsPos', 'Werkzeugleiste', 'below', [['below', 'Unter dem Brett'], ['above', 'Über dem Brett']]),
      t('toolLabels', 'Beschriftung der Werkzeuge', true),
    ],
  },
  {
    title: 'Spiel', defs: [
      s('customClues', 'Vorgaben bei „Eigene“', 34, 22, 50, 1, '', 'Weniger Vorgaben sind schwerer'),
      t('pauseHidden', 'Uhr pausiert im Hintergrund', true),
      t('pauseSettings', 'Uhr pausiert in den Einstellungen', true),
    ],
  },
];
const ALL_DEFS = CATEGORIES.flatMap((cat) => cat.defs);
const DEFAULTS = Object.fromEntries(ALL_DEFS.map((d) => [d.key, d.def]));

// ---------- Speicher (fehlertolerant, z. B. im privaten Modus) ----------

const store = {
  get(key) {
    try { return JSON.parse(localStorage.getItem(key)); } catch { return null; }
  },
  set(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* egal */ }
  },
};

let prefs = { ...DEFAULTS, ...(store.get('sudoku-settings') || {}) };
const setting = (key) => (key in prefs ? prefs[key] : GAME_DEFAULTS[key]);
function setPref(key, value) {
  prefs = { ...prefs, [key]: value };
  store.set('sudoku-settings', prefs);
  render();
}

// ---------- Farben wie Look.kt ----------

const BOX_HUES = [15, 55, 95, 135, 175, 215, 255, 295, 335];
const ok = (l, ch, h, a) => `oklch(${l} ${ch} ${h}${a != null ? ` / ${a}` : ''})`;
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');

function isDark() {
  const th = setting('theme');
  return th === 'dark' || (th === 'system' && darkQuery.matches);
}

function boxHue(b) {
  const br = Math.floor(b / 3), bc = b % 3;
  const base = {
    check: (br + bc) % 2 === 1 ? 95 : 215,
    one: Number(setting('accent')),
    rows: [15, 135, 255][br],
    cols: [15, 135, 255][bc],
  }[setting('boxPattern')] ?? BOX_HUES[b];
  return (base + Number(setting('hueShift'))) % 360;
}

function applyTheme() {
  const dark = isDark();
  const bh = 265;
  const o = (l, ch) => ok(l, ch, bh);
  const acc = Number(setting('accent'));
  const eh = Number(setting('errHue'));
  const user = dark ? ok(0.8, 0.12, acc) : ok(0.5, 0.19, acc);
  const vars = {
    ground: dark ? o(0.2, 0.02) : o(0.975, 0.006),
    surface: dark ? o(0.26, 0.02) : '#fff',
    ink: dark ? o(0.96, 0.01) : o(0.24, 0.03),
    ink2: dark ? o(0.76, 0.02) : o(0.47, 0.02),
    accent: dark ? o(0.94, 0.03) : o(0.27, 0.05),
    'on-accent': dark ? o(0.22, 0.03) : ok(0.98, 0.005, 265),
    groove: dark ? o(0.14, 0.015) : o(0.87, 0.02),
    edge: dark ? o(0.34, 0.02) : o(0.89, 0.01),
    'seg-bg': dark ? o(0.25, 0.02) : o(0.93, 0.01),
    'bar-bg': dark ? o(0.34, 0.02) : o(0.92, 0.01),
    user,
    err: dark ? ok(0.78, 0.15, eh) : ok(0.52, 0.21, eh),
    'err-bg': dark ? ok(0.6, 0.2, eh, 0.4) : ok(0.65, 0.2, eh, 0.28),
    peer: `rgb(255 255 255 / ${dark ? 0.08 : 0.55})`,
    same: dark ? ok(0.8, 0.12, acc, 0.22) : ok(0.5, 0.19, acc, 0.14),
    ring: dark ? ok(0.8, 0.12, acc, 0.8) : ok(0.5, 0.19, acc, 0.7),
  };
  const root = document.documentElement.style;
  for (const [k, v] of Object.entries(vars)) root.setProperty(`--${k}`, v);
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  document.querySelector('meta[name="theme-color"]').content = dark ? '#1b1d24' : '#f6f7fb';

  const tm = Number(setting('tint'));
  boxes.forEach((box, b) => {
    const h = boxHue(b);
    box.style.setProperty('--tile', dark ? ok(0.3, 0.06 * tm, h) : ok(0.945, 0.045 * tm, h));
    box.style.setProperty('--line', dark ? ok(0.23, 0.05 * tm, h) : ok(0.885, 0.06 * tm, h));
  });
}

// ---------- DOM aufbauen ----------

const $ = (id) => document.getElementById(id);
const el = (tag, props = {}, ...children) => {
  const e = Object.assign(document.createElement(tag), props);
  for (const ch of children) e.append(ch);
  return e;
};

for (const id of ['mark', 'menu-mark']) {
  BOX_HUES.forEach((h) => $(id).append(el('span', { style: `background:${ok(0.76, 0.13, h)}` })));
}

const segEl = $('seg');
const segButtons = Object.entries(DIFFICULTIES).map(([key, d]) => {
  const b = el('button', { textContent: d.label, role: 'tab' });
  b.dataset.testid = `diff-${key}`;
  b.addEventListener('click', () => askNewGame(key));
  segEl.append(b);
  return [key, b];
});

const boardEl = $('board');
const boxes = [];
const cells = new Array(81);
for (let b = 0; b < 9; b++) {
  const box = el('div', { className: 'box' });
  boxes.push(box);
  for (let k = 0; k < 9; k++) {
    const r = Math.floor(b / 3) * 3 + Math.floor(k / 3);
    const col = (b % 3) * 3 + (k % 3);
    const i = r * 9 + col;
    const cell = el('button', { className: 'cell', role: 'gridcell' });
    cell.dataset.testid = `cell-${i}`;
    cell.dataset.index = i;
    cell.addEventListener('click', () => act((g) => g.select(i), true));
    cells[i] = cell;
    box.append(cell);
  }
  boardEl.append(box);
}

const TOOLS = [
  ['undo', '↶', 'Zurück', (g) => g.undo()],
  ['erase', '⌫', 'Löschen', (g) => g.erase()],
  ['notes', '✎', 'Notizen', (g) => g.toggleNotes()],
  ['hint', '✦', 'Tipp', (g) => g.hint()],
];
const toolButtons = {};
for (const [id, glyph, label, fn] of TOOLS) {
  const animate = id === 'hint';
  const b = el('button', { className: 'tool' }, el('span', { className: 'g', textContent: glyph }), el('span', { className: 'l', textContent: label }));
  b.dataset.testid = `tool-${id}`;
  b.setAttribute('aria-label', label);
  b.addEventListener('click', () => act(fn, animate));
  toolButtons[id] = b;
}

const padEl = $('pad');
const keys = [];
for (let d = 1; d <= 9; d++) {
  const bar = el('i');
  const k = el('button', { className: 'key' }, el('span', { textContent: String(d) }), el('span', { className: 'bar' }, bar));
  k.dataset.testid = `key-${d}`;
  k.setAttribute('aria-label', `Ziffer ${d}`);
  k.addEventListener('click', () => act((g) => g.onKey(d), true));
  keys.push({ k, bar });
  padEl.append(k);
}

// ---------- Spielzustand und Stoppuhr ----------

let game = null;
let loading = false;
let elapsed = 0;
let startedAt = 0;
let running = false;
let resultShownFor = null;
let menuOpen = false;
let celebrating = false;

function saveGame() {
  if (game) store.set('sudoku-game', { game: game.toJSON(), elapsed: currentElapsed() });
}

function currentElapsed() {
  return running ? elapsed + (performance.now() - startedAt) : elapsed;
}

function pauseTimer() {
  if (running) {
    elapsed += performance.now() - startedAt;
    running = false;
  }
}

function resumeTimer() {
  if (running || loading || !game || game.over) return;
  if (document.hidden && setting('pauseHidden')) return;
  if ($('settings').open && setting('pauseSettings')) return;
  if (menuOpen) return;
  startedAt = performance.now();
  running = true;
}

function formatTime(ms) {
  const total = Math.floor(ms / 1000);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

function newGame(difficulty) {
  hideMenu();
  celebrating = false;
  loading = true;
  pauseTimer();
  elapsed = 0;
  resultShownFor = null;
  render();
  // Kurz warten, damit der Ladekreis sichtbar wird, bevor der Generator rechnet
  setTimeout(() => {
    const clues = difficulty === 'CUSTOM' ? Number(setting('customClues')) : DIFFICULTIES[difficulty].clues;
    game = Game.create(difficulty, clues, setting);
    loading = false;
    saveGame();
    render();
    resumeTimer();
  }, 30);
}

function act(fn, animate = false) {
  if (!game || loading || celebrating) return;
  const before = game.values.slice();
  const wasCompleted = game.completed;
  fn(game);
  if (game.over) pauseTimer(); else resumeTimer();
  if (animate && game.completed && !wasCompleted && motionOK()) celebrating = true;
  saveGame();
  render();
  if (animate) animateChanges(before, wasCompleted);
}

// ---------- Animationen ----------

const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
const motionOK = () => !motionQuery.matches;

const rowOf = (i) => Math.floor(i / 9);
const colOf = (i) => i % 9;
const boxOf = (i) => Math.floor(rowOf(i) / 3) * 3 + Math.floor(colOf(i) / 3);
const UNITS = [
  (i) => [...Array(9)].map((_, k) => rowOf(i) * 9 + k),
  (i) => [...Array(9)].map((_, k) => k * 9 + colOf(i)),
  (i) => [...Array(9)].map((_, k) => (Math.floor(boxOf(i) / 3) * 3 + Math.floor(k / 3)) * 9 + (boxOf(i) % 3) * 3 + (k % 3)),
];
const unitSolved = (values, unit) => unit.every((j) => values[j] === game.solution[j]);
const dist = (a, b) => Math.hypot(rowOf(a) - rowOf(b), colOf(a) - colOf(b));

/** Lichtblitz über ein Feld, der sich ausbreitet: Verzögerung je nach Abstand. */
function flash(i, delay, strength = 0.5, duration = 520) {
  cells[i].animate(
    [{ opacity: 0 }, { opacity: strength, offset: 0.35 }, { opacity: 0 }],
    { duration, delay, easing: 'ease-out', pseudoElement: '::after' },
  );
  cells[i].animate(
    [{ transform: 'scale(1)' }, { transform: 'scale(1.12)', offset: 0.35 }, { transform: 'scale(1)' }],
    { duration, delay, easing: 'ease-out' },
  );
}

function pop(i) {
  cells[i].animate(
    [{ transform: 'scale(0.7)' }, { transform: 'scale(1.12)', offset: 0.6 }, { transform: 'scale(1)' }],
    { duration: 220, easing: 'ease-out' },
  );
}

function shake(i) {
  cells[i].animate(
    [0, -5, 5, -4, 4, -2, 0].map((x) => ({ transform: `translateX(${x}px)` })),
    { duration: 360, easing: 'ease-in-out' },
  );
}

function animateChanges(before, wasCompleted) {
  if (!motionOK()) return;
  const changed = [];
  for (let i = 0; i < 81; i++) if (before[i] !== game.values[i] && game.values[i] !== 0) changed.push(i);

  for (const i of changed) {
    if (game.values[i] !== game.solution[i]) {
      if (game.showErrors && game.isWrong(i, setting('errMode') === 'rules')) shake(i);
      continue;
    }
    pop(i);
    if (game.completed && !wasCompleted) continue; // große Welle übernimmt
    const waved = new Set();
    for (const unitOf of UNITS) {
      const unit = unitOf(i);
      if (unitSolved(game.values, unit) && !unitSolved(before, unit)) {
        for (const j of unit) {
          if (waved.has(j)) continue;
          waved.add(j);
          flash(j, 60 + dist(i, j) * 55);
        }
      }
    }
  }

  if (game.completed && !wasCompleted) {
    const from = changed[changed.length - 1] ?? 40;
    let maxDelay = 0;
    for (let j = 0; j < 81; j++) {
      const d = 120 + dist(from, j) * 70;
      maxDelay = Math.max(maxDelay, d);
      flash(j, d, 0.6, 650);
    }
    // Zweite, sanftere Welle zurück
    for (let j = 0; j < 81; j++) flash(j, maxDelay + 250 + (maxDelay - dist(from, j) * 70) * 0.5, 0.3, 600);
    setTimeout(() => {
      celebrating = false;
      render();
    }, maxDelay * 1.6 + 900);
  }
}

// ---------- Darstellung ----------

function render() {
  applyTheme();
  $('loading').hidden = !loading;
  $('clock').hidden = !setting('showTimer');
  if (!game) return;
  const g = game;

  for (const [key, b] of segButtons) b.setAttribute('aria-selected', String(key === g.difficulty));

  const rules = setting('errMode') === 'rules';
  const hlMode = setting('hlMode');
  const sel = g.selected;
  const selValue = sel >= 0 ? g.values[sel] : 0;
  const sr = Math.floor(sel / 9), sc = sel % 9;

  for (let i = 0; i < 81; i++) {
    const cell = cells[i];
    const v = g.values[i];
    const r = Math.floor(i / 9), col = i % 9;
    const isSel = i === sel;
    let peer = false;
    if (sel >= 0 && !isSel && hlMode !== 'off') {
      peer = r === sr || col === sc || (hlMode === 'all' && isPeer(sel, i) && Math.floor(r / 3) === Math.floor(sr / 3) && Math.floor(col / 3) === Math.floor(sc / 3));
    }
    const same = setting('hlSame') && !isSel && selValue !== 0 && v === selValue;
    const wrong = g.showErrors && g.isWrong(i, rules);
    cell.className = 'cell';
    cell.classList.toggle('given', g.givens[i] !== 0);
    cell.classList.toggle('selected', isSel);
    cell.classList.toggle('peer', peer);
    cell.classList.toggle('same', same);
    cell.classList.toggle('wrong', wrong);
    cell.setAttribute('aria-selected', String(isSel));

    const label = `Zeile ${r + 1}, Spalte ${col + 1}`;
    if (v !== 0) {
      cell.replaceChildren(String(v));
      cell.setAttribute('aria-label', `${label}: ${v}`);
    } else if (g.notes[i] !== 0) {
      const notes = el('span', { className: 'notes' });
      const marked = [];
      for (let d = 1; d <= 9; d++) {
        const on = (g.notes[i] & (1 << (d - 1))) !== 0;
        if (on) marked.push(d);
        notes.append(el('span', { textContent: on ? String(d) : '', className: on && setting('hlNotes') && selValue === d ? 'hit' : '' }));
      }
      cell.replaceChildren(notes);
      cell.setAttribute('aria-label', `${label}: Notizen ${marked.join(' ')}`);
    } else {
      cell.replaceChildren();
      cell.setAttribute('aria-label', `${label}: leer`);
    }
  }

  $('open').textContent = `Noch ${g.open} offen`;
  const max = Number(setting('maxMistakes'));
  const mist = $('mistakes');
  mist.hidden = !setting('showMist');
  mist.textContent = `Fehler: ${g.mistakes}${max > 0 ? ` von ${max}` : ''}`;
  mist.classList.toggle('bad', max > 0 && g.mistakes >= max - 1);
  $('show-errors').checked = g.showErrors;

  // Werkzeuge über oder unter dem Brett
  const target = setting('toolsPos') === 'above' ? $('tools-above') : $('tools');
  const other = target.id === 'tools' ? $('tools-above') : $('tools');
  other.replaceChildren();
  if (target.childElementCount === 0) target.append(...Object.values(toolButtons));
  const labels = setting('toolLabels');
  for (const b of Object.values(toolButtons)) b.querySelector('.l').hidden = !labels;
  toolButtons.notes.setAttribute('aria-pressed', String(g.notesMode));
  const left = g.hintsLeft();
  toolButtons.hint.querySelector('.l').textContent = left != null ? `Tipp (${left})` : 'Tipp';
  toolButtons.hint.disabled = left === 0;

  padEl.classList.toggle('grid', setting('padLayout') === 'grid');
  keys.forEach(({ k, bar }, idx) => {
    const d = idx + 1;
    const n = g.count(d);
    const used = n >= 9;
    k.disabled = used;
    k.style.visibility = used && setting('doneKeys') === 'hide' ? 'hidden' : '';
    k.setAttribute('aria-pressed', String(g.activeDigit === d));
    bar.style.width = `${Math.min(1, n / 9) * 100}%`;
  });

  showResultIfNeeded();
}

function showResultIfNeeded() {
  const dlg = $('result');
  const state = game.completed ? 'done' : game.failed ? 'failed' : null;
  if (!state) {
    if (dlg.open) dlg.close();
    resultShownFor = null;
    return;
  }
  if (celebrating) return;
  if (resultShownFor === state && dlg.open) return;
  resultShownFor = state;
  if (state === 'done') {
    $('result-title').textContent = 'Geschafft.';
    $('result-text').textContent = `Gelöst in ${formatTime(currentElapsed())} mit ${game.mistakes} Fehlern und ${game.hints} Tipps.`;
    $('result-undo').hidden = true;
  } else {
    $('result-title').textContent = 'Zu viele Fehler.';
    $('result-text').textContent = `Das Fehlerlimit von ${setting('maxMistakes')} ist erreicht.`;
    $('result-undo').hidden = false;
  }
  if (!dlg.open) dlg.showModal();
}

setInterval(() => {
  $('clock').textContent = `⏱ ${formatTime(currentElapsed())}`;
}, 250);

// ---------- Dialoge ----------

let pendingDifficulty = null;
function askNewGame(difficulty) {
  if (!game || loading) return;
  pendingDifficulty = difficulty;
  $('confirm').showModal();
}
$('confirm-cancel').addEventListener('click', () => $('confirm').close());
$('confirm-ok').addEventListener('click', () => {
  $('confirm').close();
  newGame(pendingDifficulty);
});
$('result-new').addEventListener('click', () => {
  $('result').close();
  newGame(game.difficulty);
});
$('result-undo').addEventListener('click', () => {
  $('result').close();
  act((g) => g.undo());
});
$('result').addEventListener('cancel', (e) => e.preventDefault());
$('result-menu').addEventListener('click', () => {
  $('result').close();
  showMenu();
});

// ---------- Startmenü ----------

const LEVEL_DOTS = { EASY: 1, MEDIUM: 2, HARD: 3, CUSTOM: 0 };
const menuList = $('menu-list');
for (const [key, d] of Object.entries(DIFFICULTIES)) {
  const dots = el('span', { className: 'mi-dots' });
  for (let n = 0; n < 3; n++) dots.append(el('i', { className: n < LEVEL_DOTS[key] ? 'on' : '' }));
  const sub = el('span', { className: 'mi-sub' });
  const b = el('button', { className: 'menu-item' },
    el('span', {}, el('div', { className: 'mi-title', textContent: d.label }), sub),
    key === 'CUSTOM' ? el('span', { className: 'mi-sub', textContent: 'einstellbar' }) : dots);
  b.dataset.testid = `menu-${key}`;
  b.dataset.key = key;
  b.addEventListener('click', () => newGame(key));
  menuList.append(b);
}

function showMenu() {
  menuOpen = true;
  pauseTimer();
  saveGame();
  for (const b of menuList.children) {
    const key = b.dataset.key;
    const clues = key === 'CUSTOM' ? Number(setting('customClues')) : DIFFICULTIES[key].clues;
    b.querySelector('.mi-sub').textContent = `${clues} Vorgaben`;
  }
  const canContinue = game && !game.over;
  const cont = $('menu-continue');
  cont.hidden = !canContinue;
  if (canContinue) {
    $('menu-continue-sub').textContent = `${DIFFICULTIES[game.difficulty].label} · ${formatTime(elapsed)} · noch ${game.open} offen`;
  }
  $('menu').hidden = false;
  (canContinue ? cont : menuList.firstElementChild).focus();
}

function hideMenu() {
  if (!menuOpen) return;
  menuOpen = false;
  $('menu').hidden = true;
  resumeTimer();
}

$('menu-continue').addEventListener('click', hideMenu);
$('open-menu').addEventListener('click', showMenu);
$('show-errors').addEventListener('change', () => act((g) => { g.showErrors = !g.showErrors; }));

// ---------- Einstellungen ----------

function renderSettings() {
  const body = $('settings-body');
  body.replaceChildren();
  $('settings-sub').textContent = `${ALL_DEFS.length} Einstellungen in ${CATEGORIES.length} Gruppen`;
  for (const cat of CATEGORIES) {
    body.append(el('div', { className: 'caption', textContent: cat.title }));
    const card = el('div', { className: 'card' });
    for (const d of cat.defs) card.append(settingRow(d));
    body.append(card);
  }
  const reset = el('button', { className: 'reset', textContent: 'Alle Einstellungen zurücksetzen' });
  reset.addEventListener('click', () => {
    prefs = { ...DEFAULTS };
    store.set('sudoku-settings', prefs);
    render();
    renderSettings();
  });
  body.append(reset);
}

function labels(d) {
  const box = el('div', {}, el('div', { className: 'label', textContent: d.label }));
  if (d.hint) box.append(el('div', { className: 'hint', textContent: d.hint }));
  return box;
}

function settingRow(d) {
  const value = setting(d.key);
  if (d.kind === 'toggle') {
    const input = el('input', { type: 'checkbox', className: 'switch', checked: Boolean(value) });
    input.dataset.testid = `set-${d.key}`;
    input.setAttribute('aria-label', d.label);
    input.addEventListener('change', () => setPref(d.key, input.checked));
    return el('label', { className: 'row toggle' }, labels(d), input);
  }
  if (d.kind === 'choice') {
    const row = el('div', { className: 'row' }, labels(d));
    const choices = el('div', { className: 'choices' });
    for (const [v, text] of d.options) {
      const b = el('button', { textContent: text });
      b.dataset.testid = `set-${d.key}-${v}`;
      b.setAttribute('aria-pressed', String(v === value));
      b.addEventListener('click', () => {
        setPref(d.key, v);
        for (const other of choices.children) other.setAttribute('aria-pressed', String(other === b));
      });
      choices.append(b);
    }
    row.append(choices);
    return row;
  }
  const val = el('span', { className: 'val', textContent: `${value}${d.suffix}` });
  const range = el('input', { type: 'range', min: d.min, max: d.max, step: d.step, value });
  range.dataset.testid = `set-${d.key}`;
  range.setAttribute('aria-label', d.label);
  range.addEventListener('input', () => {
    val.textContent = `${range.value}${d.suffix}`;
    setPref(d.key, Number(range.value));
  });
  return el('div', { className: 'row' }, el('div', { className: 'slider-head' }, labels(d), val), range);
}

$('open-settings').addEventListener('click', () => {
  renderSettings();
  $('settings').showModal();
  if (setting('pauseSettings')) pauseTimer();
});
$('settings-done').addEventListener('click', () => $('settings').close());
$('settings').addEventListener('close', () => resumeTimer());
$('settings').addEventListener('click', (e) => {
  if (e.target === $('settings')) $('settings').close(); // Klick auf den Hintergrund
});

// ---------- Tastatur ----------

document.addEventListener('keydown', (e) => {
  if (menuOpen && e.key === 'Escape' && game && !game.over) {
    hideMenu();
    return;
  }
  if (!game || menuOpen || document.querySelector('dialog[open]') || e.ctrlKey || e.metaKey || e.altKey) return;
  const k = e.key;
  if (/^[1-9]$/.test(k)) {
    act((g) => g.onKey(Number(k)), true);
  } else if (k === 'Backspace' || k === 'Delete' || k === '0') {
    act((g) => g.erase());
  } else if (k.startsWith('Arrow')) {
    const sel = game.selected < 0 ? 40 : game.selected;
    let r = Math.floor(sel / 9), col = sel % 9;
    if (k === 'ArrowUp') r = (r + 8) % 9;
    if (k === 'ArrowDown') r = (r + 1) % 9;
    if (k === 'ArrowLeft') col = (col + 8) % 9;
    if (k === 'ArrowRight') col = (col + 1) % 9;
    act((g) => { g.selected = r * 9 + col; });
    cells[r * 9 + col].focus();
  } else if (k === 'n' || k === 'N') {
    act((g) => g.toggleNotes());
  } else if (k === 'z' || k === 'Z') {
    act((g) => g.undo());
  } else if (k === 'h' || k === 'H') {
    act((g) => g.hint(), true);
  } else {
    return;
  }
  e.preventDefault();
});

// ---------- Start ----------

document.addEventListener('visibilitychange', () => {
  if (document.hidden && setting('pauseHidden')) pauseTimer(); else resumeTimer();
  saveGame();
});
window.addEventListener('pagehide', saveGame);
darkQuery.addEventListener('change', render);

const saved = store.get('sudoku-game');
try {
  if (saved) {
    game = Game.fromJSON(saved.game, setting);
    elapsed = Number(saved.elapsed) || 0;
  }
} catch {
  game = null;
}
render();
showMenu();

// Für automatische Tests: Zugriff auf den Spielstand (z. B. die Lösung)
window.sudoku = {
  get game() { return game; },
  get loading() { return loading; },
  get menuOpen() { return menuOpen; },
  get celebrating() { return celebrating; },
  newGame,
};
