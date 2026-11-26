// Spiellogik der Web-Version. Entspricht SudokuGenerator.kt und GameViewModel.kt der App.
// Index im Raster: row * 9 + col, 0 = leer.

// maxMistakes: erlaubte Fehler, danach ist das Spiel verloren (null = Einstellung „maxMistakes“)
export const DIFFICULTIES = {
  EASY: { label: 'Leicht', clues: 40, maxMistakes: 5 },
  MEDIUM: { label: 'Mittel', clues: 32, maxMistakes: 3 },
  HARD: { label: 'Schwer', clues: 26, maxMistakes: 1 },
  CUSTOM: { label: 'Eigene', clues: 34, maxMistakes: null },
};

/** Kleiner, reproduzierbarer Zufallsgenerator (für Tests mit festem Startwert). */
export function seededRandom(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffled(arr, random) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export function canPlace(grid, idx, d) {
  const row = Math.floor(idx / 9);
  const col = idx % 9;
  for (let i = 0; i < 9; i++) {
    if (grid[row * 9 + i] === d) return false;
    if (grid[i * 9 + col] === d) return false;
  }
  const br = row - (row % 3);
  const bc = col - (col % 3);
  for (let r = br; r < br + 3; r++) {
    for (let c = bc; c < bc + 3; c++) {
      if (grid[r * 9 + c] === d) return false;
    }
  }
  return true;
}

export function isPeer(a, b) {
  const ra = Math.floor(a / 9), ca = a % 9;
  const rb = Math.floor(b / 9), cb = b % 9;
  return ra === rb || ca === cb || (Math.floor(ra / 3) === Math.floor(rb / 3) && Math.floor(ca / 3) === Math.floor(cb / 3));
}

function fillGrid(grid, random) {
  const idx = grid.indexOf(0);
  if (idx === -1) return true;
  for (const d of shuffled([1, 2, 3, 4, 5, 6, 7, 8, 9], random)) {
    if (canPlace(grid, idx, d)) {
      grid[idx] = d;
      if (fillGrid(grid, random)) return true;
      grid[idx] = 0;
    }
  }
  return false;
}

/** Zählt Lösungen, bricht bei `limit` ab. Nimmt immer das Feld mit den wenigsten Kandidaten. */
export function countSolutions(grid, limit = 2) {
  let best = -1;
  let bestCount = 10;
  for (let i = 0; i < 81; i++) {
    if (grid[i] !== 0) continue;
    let c = 0;
    for (let d = 1; d <= 9; d++) if (canPlace(grid, i, d)) c++;
    if (c === 0) return 0;
    if (c < bestCount) {
      bestCount = c;
      best = i;
    }
  }
  if (best === -1) return 1;
  let total = 0;
  for (let d = 1; d <= 9; d++) {
    if (!canPlace(grid, best, d)) continue;
    grid[best] = d;
    total += countSolutions(grid, limit - total);
    grid[best] = 0;
    if (total >= limit) return total;
  }
  return total;
}

/** Erzeugt ein Rätsel mit eindeutiger Lösung und mindestens `clueTarget` Vorgaben. */
export function generate(clueTarget, random = Math.random) {
  const solution = new Array(81).fill(0);
  fillGrid(solution, random);
  const puzzle = solution.slice();
  let clues = 81;
  for (const pos of shuffled([...Array(81).keys()], random)) {
    if (clues <= clueTarget) break;
    const backup = puzzle[pos];
    puzzle[pos] = 0;
    if (countSolutions(puzzle.slice(), 2) !== 1) {
      puzzle[pos] = backup;
    } else {
      clues--;
    }
  }
  return { solution, givens: puzzle };
}

/** Standardwerte der Einstellungen, die die Spiellogik braucht. */
export const GAME_DEFAULTS = {
  inputMode: 'cell', // 'cell' | 'digit'
  autoAdvance: 'off', // 'off' | 'next' | 'correct'
  autoNotes: true,
  maxMistakes: 0, // nur bei „Eigene“; 0 = unbegrenzt
  hintLimit: 0, // 0 = unbegrenzt
  hintMode: 'fill', // 'fill' | 'show'
};

/**
 * Spielzustand und Eingaben. `settings` ist eine Funktion key => Wert,
 * damit geänderte Einstellungen sofort gelten.
 */
export class Game {
  constructor(data, settings = (k) => GAME_DEFAULTS[k]) {
    this.settings = settings;
    Object.assign(this, {
      difficulty: 'EASY',
      selected: -1,
      notesMode: false,
      showErrors: true,
      completed: false,
      failed: false,
      hints: 0,
      mistakes: 0,
      activeDigit: 0,
      history: [],
    }, data);
  }

  static create(difficulty, clues, settings, random = Math.random) {
    const { solution, givens } = generate(clues, random);
    return new Game({
      difficulty,
      solution,
      givens,
      values: givens.slice(),
      notes: new Array(81).fill(0),
    }, settings);
  }

  static fromJSON(json, settings) {
    const g = new Game(json, settings);
    if (!Array.isArray(g.values) || g.values.length !== 81) throw new Error('Ungültiger Spielstand');
    return g;
  }

  toJSON() {
    const { settings, ...rest } = this;
    return { ...rest, history: this.history.slice(-50) };
  }

  get open() {
    return this.values.filter((v) => v === 0).length;
  }

  count(d) {
    return this.values.filter((v) => v === d).length;
  }

  /** Erlaubte Fehler für dieses Spiel (0 = unbegrenzt). */
  mistakeLimit() {
    const fixed = DIFFICULTIES[this.difficulty]?.maxMistakes;
    return fixed ?? (Number(this.settings('maxMistakes')) || 0);
  }

  get over() {
    return this.completed || this.failed;
  }

  isWrong(i, rules = false) {
    const v = this.values[i];
    if (v === 0 || this.givens[i] !== 0) return false;
    if (rules) {
      for (let j = 0; j < 81; j++) {
        if (j !== i && isPeer(i, j) && this.values[j] === v) return true;
      }
      return false;
    }
    return v !== this.solution[i];
  }

  select(i) {
    this.selected = i;
    if (this.settings('inputMode') === 'digit' && this.activeDigit !== 0 && this.givens[i] === 0) {
      this.inputDigit(this.activeDigit);
    }
  }

  toggleNotes() {
    this.notesMode = !this.notesMode;
  }

  /** Ziffernfeld-Taste: je nach Eingabemodus Ziffer eintragen oder aktive Ziffer wählen. */
  onKey(d) {
    if (this.settings('inputMode') === 'digit') {
      this.activeDigit = this.activeDigit === d ? 0 : d;
    } else {
      this.inputDigit(d);
    }
  }

  inputDigit(d) {
    if (this.over) return;
    const sel = this.selected;
    if (sel < 0 || this.givens[sel] !== 0) return;

    if (this.notesMode) {
      if (this.values[sel] !== 0) return;
      this.push();
      this.notes[sel] ^= 1 << (d - 1);
      return;
    }

    const wasSame = this.values[sel] === d;
    this.push();
    this.values[sel] = d;
    this.notes[sel] = 0;
    if (this.settings('autoNotes')) this.clearPeerNotes(sel, d);

    const right = d === this.solution[sel];
    if (!right && !wasSame) this.mistakes++;
    const max = this.mistakeLimit();
    this.failed = max > 0 && this.mistakes >= max;
    this.completed = this.values.every((v, i) => v === this.solution[i]);

    if (!this.completed && !this.failed) {
      const adv = this.settings('autoAdvance');
      if (adv === 'next' || (adv === 'correct' && right)) this.selected = this.nextEmpty(sel);
    }
  }

  erase() {
    const sel = this.selected;
    if (this.over || sel < 0 || this.givens[sel] !== 0) return;
    if (this.values[sel] === 0 && this.notes[sel] === 0) return;
    this.push();
    this.values[sel] = 0;
    this.notes[sel] = 0;
  }

  undo() {
    if (this.failed) return; // verloren: nur Neustart
    const last = this.history.pop();
    if (!last) return;
    this.values = last.values;
    this.notes = last.notes;
    this.completed = false;
    this.failed = false;
  }

  hintsLeft() {
    const limit = Number(this.settings('hintLimit')) || 0;
    return limit > 0 ? Math.max(0, limit - this.hints) : null;
  }

  /** Füllt das markierte Feld mit der richtigen Ziffer (oder zeigt es nur); ohne Markierung das erste falsche Feld. */
  hint() {
    if (this.over || this.hintsLeft() === 0) return;
    const s = this.selected;
    let target;
    if (s >= 0 && this.givens[s] === 0 && this.values[s] !== this.solution[s]) {
      target = s;
    } else {
      target = this.values.findIndex((v, i) => v !== this.solution[i]);
      if (target === -1) return;
    }
    if (this.settings('hintMode') === 'show') {
      this.selected = target;
      this.hints++;
      return;
    }
    this.push();
    const d = this.solution[target];
    this.values[target] = d;
    this.notes[target] = 0;
    this.clearPeerNotes(target, d);
    this.selected = target;
    this.hints++;
    this.completed = this.values.every((v, i) => v === this.solution[i]);
  }

  clearPeerNotes(from, d) {
    for (let i = 0; i < 81; i++) {
      if (isPeer(from, i)) this.notes[i] &= ~(1 << (d - 1));
    }
  }

  nextEmpty(from) {
    for (let k = 1; k <= 81; k++) {
      const n = (from + k) % 81;
      if (this.values[n] === 0) return n;
    }
    return from;
  }

  push() {
    this.history.push({ values: this.values.slice(), notes: this.notes.slice() });
    if (this.history.length > 200) this.history.shift();
  }
}
