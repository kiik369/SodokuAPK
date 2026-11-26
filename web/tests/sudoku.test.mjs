// Unit-Tests der Spiellogik: node --test web/tests/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DIFFICULTIES, Game, canPlace, countSolutions, generate, isPeer, seededRandom } from '../sudoku.js';

function assertValidSolution(grid) {
  const all = [1, 2, 3, 4, 5, 6, 7, 8, 9].join();
  for (let i = 0; i < 9; i++) {
    const row = [], col = [], box = [];
    for (let k = 0; k < 9; k++) {
      row.push(grid[i * 9 + k]);
      col.push(grid[k * 9 + i]);
      box.push(grid[(Math.floor(i / 3) * 3 + Math.floor(k / 3)) * 9 + (i % 3) * 3 + (k % 3)]);
    }
    for (const unit of [row, col, box]) assert.equal(unit.sort().join(), all);
  }
}

test('Rätsel sind gültig und haben genau eine Lösung', () => {
  for (const key of ['EASY', 'MEDIUM', 'HARD']) {
    for (let seed = 1; seed <= 3; seed++) {
      const { solution, givens } = generate(DIFFICULTIES[key].clues, seededRandom(seed * 7 + key.length));
      assertValidSolution(solution);
      givens.forEach((g, i) => { if (g) assert.equal(g, solution[i]); });
      assert.ok(givens.filter(Boolean).length >= DIFFICULTIES[key].clues);
      assert.equal(countSolutions(givens.slice()), 1);
    }
  }
});

test('canPlace und isPeer prüfen Zeile, Spalte und Block', () => {
  const grid = new Array(81).fill(0);
  grid[0] = 5;
  assert.equal(canPlace(grid, 8, 5), false);
  assert.equal(canPlace(grid, 72, 5), false);
  assert.equal(canPlace(grid, 20, 5), false);
  assert.equal(canPlace(grid, 40, 5), true);
  assert.equal(isPeer(0, 20), true);
  assert.equal(isPeer(0, 40), false);
});

function newGame(settings = {}) {
  const get = (k) => ({ inputMode: 'cell', autoAdvance: 'off', autoNotes: true, maxMistakes: 0, hintLimit: 0, hintMode: 'fill', ...settings })[k];
  const g = Game.create('EASY', 40, get, seededRandom(42));
  const empty = g.values.findIndex((v) => v === 0);
  return { g, empty };
}

test('Eingabe, Fehler, Löschen und Rückgängig', () => {
  const { g, empty } = newGame();
  const right = g.solution[empty];
  const wrong = right === 9 ? 1 : right + 1;

  g.select(empty);
  g.inputDigit(wrong);
  assert.equal(g.values[empty], wrong);
  assert.equal(g.mistakes, 1);
  assert.ok(g.isWrong(empty));

  g.erase();
  assert.equal(g.values[empty], 0);

  g.inputDigit(right);
  assert.equal(g.values[empty], right);
  g.undo();
  assert.equal(g.values[empty], 0);
});

test('Vorgaben lassen sich nicht ändern', () => {
  const { g } = newGame();
  const given = g.givens.findIndex(Boolean);
  g.select(given);
  g.inputDigit(given === 1 ? 2 : 1);
  g.erase();
  assert.equal(g.values[given], g.givens[given]);
});

test('Notizen und automatisches Aufräumen', () => {
  const { g, empty } = newGame();
  const d = g.solution[empty];
  const peer = g.values.findIndex((v, i) => v === 0 && i !== empty && isPeer(i, empty) && g.solution[i] !== d);
  g.toggleNotes();
  g.select(peer);
  g.inputDigit(d);
  assert.equal(g.notes[peer], 1 << (d - 1));
  g.toggleNotes();
  g.select(empty);
  g.inputDigit(d);
  assert.equal(g.notes[peer], 0, 'Notiz im Nachbarfeld wird entfernt');
});

test('Tipp füllt das gewählte Feld und respektiert das Limit', () => {
  const { g, empty } = newGame({ hintLimit: 1 });
  g.select(empty);
  g.hint();
  assert.equal(g.values[empty], g.solution[empty]);
  assert.equal(g.hintsLeft(), 0);
  const next = g.values.findIndex((v) => v === 0);
  g.select(next);
  g.hint();
  assert.equal(g.values[next], 0);
});

test('Fehlerlimit beendet das Spiel, Rückgängig setzt es fort', () => {
  const { g, empty } = newGame({ maxMistakes: 1 });
  g.select(empty);
  g.inputDigit(g.solution[empty] === 9 ? 1 : g.solution[empty] + 1);
  assert.ok(g.failed);
  g.inputDigit(g.solution[empty]);
  assert.notEqual(g.values[empty], g.solution[empty], 'nach Spielende keine Eingabe');
  g.undo();
  assert.equal(g.failed, false);
});

test('Ziffer-zuerst-Modus und Weiterspringen', () => {
  const { g, empty } = newGame({ inputMode: 'digit', autoAdvance: 'next' });
  const d = g.solution[empty];
  g.onKey(d);
  assert.equal(g.activeDigit, d);
  g.select(empty);
  assert.equal(g.values[empty], d);
  assert.notEqual(g.selected, empty, 'springt zum nächsten leeren Feld');
  assert.equal(g.values[g.selected], 0);
});

test('Komplett lösen und Spielstand speichern/laden', () => {
  const { g } = newGame();
  for (let i = 0; i < 81; i++) {
    if (g.givens[i] === 0) {
      g.select(i);
      g.inputDigit(g.solution[i]);
    }
  }
  assert.ok(g.completed);
  const copy = Game.fromJSON(JSON.parse(JSON.stringify(g.toJSON())), g.settings);
  assert.deepEqual(copy.values, g.values);
  assert.ok(copy.completed);
});
