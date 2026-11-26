// Browser-Tests der Web-Version: node web/tests/ui.test.mjs
// Startet einen kleinen Webserver, öffnet die Seite in Chromium und spielt über echte Klicks.
// Screenshots landen in web/tests/screenshots/.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const SHOTS = join(ROOT, 'tests', 'screenshots');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };

let server, browser, url;

before(async () => {
  server = createServer(async (req, res) => {
    const path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
    const file = join(ROOT, path === '/' ? 'index.html' : path);
    try {
      const body = await readFile(file);
      res.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream' });
      res.end(body);
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  url = `http://127.0.0.1:${server.address().port}/`;
  browser = await chromium.launch();
  await mkdir(SHOTS, { recursive: true });
});

after(async () => {
  await browser?.close();
  server?.close();
});

/** Neue Seite mit leerem Speicher; wählt im Startmenü „Leicht“ und wartet, bis das Rätsel da ist. */
async function open(options = {}) {
  const context = await browser.newContext({ viewport: { width: 412, height: 915 }, ...options });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(url);
  await page.getByTestId('menu-EASY').click();
  await page.waitForFunction(() => window.sudoku?.game && !window.sudoku.loading);
  return { page, context, errors };
}

const game = (page) => page.evaluate(() => JSON.parse(JSON.stringify(window.sudoku.game.toJSON())));
const cell = (page, i) => page.getByTestId(`cell-${i}`);
const key = (page, d) => page.getByTestId(`key-${d}`);
const firstEmpty = (g) => g.values.findIndex((v, i) => v === 0 && g.givens[i] === 0);
const wrongDigit = (g, i) => [1, 2, 3, 4, 5, 6, 7, 8, 9].find((d) => d !== g.solution[i] && g.values.filter((v) => v === d).length < 9);

test('Seite lädt ohne Fehler und zeigt ein Rätsel', async () => {
  const { page, context, errors } = await open();
  const g = await game(page);
  assert.equal(g.values.length, 81);
  assert.ok(g.givens.filter(Boolean).length >= 40);
  await assert.doesNotReject(page.getByText(`Noch ${g.values.filter((v) => v === 0).length} offen`).waitFor());
  await page.screenshot({ path: join(SHOTS, '1-start.png') });
  assert.deepEqual(errors, []);
  await context.close();
});

test('Richtige Ziffer, falsche Ziffer, Löschen, Rückgängig', async () => {
  const { page, context } = await open();
  let g = await game(page);
  const i = firstEmpty(g);

  await cell(page, i).click();
  await key(page, g.solution[i]).click();
  assert.equal(await cell(page, i).textContent(), String(g.solution[i]));

  await page.getByTestId('tool-undo').click();
  assert.equal(await cell(page, i).textContent(), '');

  const wrong = wrongDigit(g, i);
  await key(page, wrong).click();
  g = await game(page);
  assert.equal(g.mistakes, 1);
  assert.match(await cell(page, i).getAttribute('class'), /wrong/);
  await page.screenshot({ path: join(SHOTS, '2-fehler.png') });

  await page.getByTestId('tool-erase').click();
  assert.equal((await game(page)).values[i], 0);
  await context.close();
});

test('Notizen und Tipp', async () => {
  const { page, context } = await open();
  const g = await game(page);
  const i = firstEmpty(g);

  await page.getByTestId('tool-notes').click();
  await cell(page, i).click();
  await key(page, 1).click();
  await key(page, 2).click();
  assert.equal((await game(page)).notes[i], 0b11);
  assert.equal(await cell(page, i).locator('.notes span:not(:empty)').count(), 2);

  await page.getByTestId('tool-notes').click();
  await page.getByTestId('tool-hint').click();
  const after = await game(page);
  assert.equal(after.values[i], g.solution[i]);
  assert.equal(after.hints, 1);
  await context.close();
});

test('Tastatursteuerung', async () => {
  const { page, context } = await open();
  const g = await game(page);
  const i = firstEmpty(g);
  await cell(page, i).click();
  await page.keyboard.press(String(g.solution[i]));
  assert.equal((await game(page)).values[i], g.solution[i]);
  await page.keyboard.press('z');
  assert.equal((await game(page)).values[i], 0);
  await page.keyboard.press('ArrowRight');
  assert.equal((await game(page)).selected, i % 9 === 8 ? i - 8 : i + 1);
  await context.close();
});

test('Ganzes Rätsel lösen zeigt „Geschafft.“', async () => {
  const { page, context } = await open();
  const g = await game(page);
  for (let i = 0; i < 81; i++) {
    if (g.givens[i] === 0) {
      await cell(page, i).click();
      await key(page, g.solution[i]).click();
    }
  }
  assert.equal(await page.evaluate(() => window.sudoku.celebrating), true, 'große Welle läuft');
  await page.waitForTimeout(400);
  await page.screenshot({ path: join(SHOTS, '3a-siegeswelle.png') });
  const dialog = page.getByTestId('result-dialog');
  await dialog.getByText('Geschafft.').waitFor();
  await page.screenshot({ path: join(SHOTS, '3-geschafft.png') });
  await dialog.getByRole('button', { name: 'Neues Spiel' }).click();
  await page.waitForFunction(() => !window.sudoku.loading && !window.sudoku.game.completed);
  await context.close();
});

test('Schwierigkeit wechseln mit Rückfrage', async () => {
  const { page, context } = await open();
  await page.getByTestId('diff-HARD').click();
  const confirm = page.getByTestId('confirm-dialog');
  await confirm.getByRole('button', { name: 'Weiterspielen' }).click();
  assert.equal((await game(page)).difficulty, 'EASY');

  await page.getByTestId('diff-HARD').click();
  await confirm.getByRole('button', { name: 'Neues Spiel' }).click();
  await page.waitForFunction(() => window.sudoku.game.difficulty === 'HARD' && !window.sudoku.loading);
  const g = await game(page);
  assert.ok(g.givens.filter(Boolean).length >= 26);
  await context.close();
});

test('Fehlerlimit über die Einstellungen', async () => {
  const { page, context } = await open();
  await page.getByTestId('settings').click();
  const slider = page.getByTestId('set-maxMistakes');
  await slider.fill('1');
  await page.getByRole('button', { name: 'Fertig' }).click();

  const g = await game(page);
  const i = firstEmpty(g);
  await cell(page, i).click();
  await key(page, wrongDigit(g, i)).click();
  const dialog = page.getByTestId('result-dialog');
  await dialog.getByText('Zu viele Fehler.').waitFor();
  await dialog.getByRole('button', { name: 'Zug zurück' }).click();
  assert.equal((await game(page)).failed, false);
  await context.close();
});

test('Einstellungen: Ziffernfeld 3×3 und Dunkelmodus bleiben nach Neuladen', async () => {
  const { page, context } = await open();
  await page.getByTestId('settings').click();
  await page.getByTestId('set-padLayout-grid').click();
  await page.getByTestId('set-theme-dark').click();
  await page.screenshot({ path: join(SHOTS, '4-einstellungen.png') });
  await page.getByRole('button', { name: 'Fertig' }).click();

  assert.match(await page.locator('#pad').getAttribute('class'), /grid/);
  const before = await game(page);
  await page.reload();
  await page.getByTestId('menu-continue').click();
  await page.waitForFunction(() => window.sudoku?.game && !window.sudoku.loading);
  assert.match(await page.locator('#pad').getAttribute('class'), /grid/);
  assert.equal(await page.evaluate(() => document.documentElement.style.colorScheme), 'dark');
  assert.deepEqual((await game(page)).givens, before.givens, 'Spielstand bleibt erhalten');
  await page.screenshot({ path: join(SHOTS, '5-dunkel.png') });
  await context.close();
});

test('Kleines Handy-Display ohne horizontales Scrollen', async () => {
  const { page, context } = await open({ viewport: { width: 320, height: 640 } });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(overflow <= 0, `Seite ist ${overflow}px zu breit`);
  await page.screenshot({ path: join(SHOTS, '6-klein.png'), fullPage: true });
  await context.close();
});

test('Startmenü: erst Schwierigkeit wählen, später weiterspielen', async () => {
  const context = await browser.newContext({ viewport: { width: 412, height: 915 } });
  const page = await context.newPage();
  await page.goto(url);
  const menu = page.getByTestId('menu');
  await menu.waitFor();
  assert.equal(await page.getByTestId('menu-continue').isVisible(), false, 'ohne Spielstand kein Weiterspielen');
  await page.screenshot({ path: join(SHOTS, '7-menu.png') });

  await page.getByTestId('menu-MEDIUM').click();
  await page.waitForFunction(() => window.sudoku.game?.difficulty === 'MEDIUM' && !window.sudoku.loading);
  assert.equal(await menu.isVisible(), false);

  // Ein Zug, dann zurück ins Menü und weiterspielen
  let g = await game(page);
  const i = firstEmpty(g);
  await cell(page, i).click();
  await key(page, g.solution[i]).click();
  await page.getByTestId('menu-button').click();
  await menu.waitFor();
  assert.match(await page.getByTestId('menu-continue').textContent(), /Mittel/);
  await page.screenshot({ path: join(SHOTS, '8-menu-weiter.png') });
  await page.getByTestId('menu-continue').click();
  g = await game(page);
  assert.equal(g.values[i], g.solution[i], 'Spielstand bleibt erhalten');

  // Neues Spiel aus dem Menü ersetzt das laufende
  await page.getByTestId('menu-button').click();
  await page.getByTestId('menu-HARD').click();
  await page.waitForFunction(() => window.sudoku.game?.difficulty === 'HARD' && !window.sudoku.loading);
  await context.close();
});

test('Welle, wenn eine Zeile fertig wird', async () => {
  const { page, context } = await open();
  const g = await game(page);
  // Zeile mit den wenigsten offenen Feldern bis auf eins füllen
  const rows = [...Array(9)].map((_, r) => [...Array(9)].map((_, c) => r * 9 + c).filter((i) => g.values[i] === 0));
  const row = rows.filter((r) => r.length > 0).sort((a, b) => a.length - b.length)[0];
  for (const i of row.slice(0, -1)) {
    await cell(page, i).click();
    await key(page, g.solution[i]).click();
  }
  const last = row[row.length - 1];
  await cell(page, last).click();
  await key(page, g.solution[last]).click();
  // Alle 9 Felder der Zeile laufen gerade eine Animation
  const r = Math.floor(last / 9);
  const animated = await page.evaluate((r) => [...Array(9)].filter((_, c) =>
    document.querySelector(`[data-testid="cell-${r * 9 + c}"]`).getAnimations({ subtree: true }).length > 0).length, r);
  assert.equal(animated, 9);
  await page.waitForTimeout(250);
  await page.screenshot({ path: join(SHOTS, '9-welle.png') });
  await context.close();
});

test('Falsche Ziffer wackelt', async () => {
  const { page, context } = await open();
  const g = await game(page);
  const i = firstEmpty(g);
  await cell(page, i).click();
  await key(page, wrongDigit(g, i)).click();
  const moving = await page.evaluate((i) => document.querySelector(`[data-testid="cell-${i}"]`)
    .getAnimations().some((a) => JSON.stringify(a.effect.getKeyframes()).includes('translateX')), i);
  assert.ok(moving);
  await context.close();
});

test('Ohne Animationen (Bewegung reduzieren) kommt „Geschafft.“ sofort', async () => {
  const { page, context } = await open({ reducedMotion: 'reduce' });
  const g = await game(page);
  for (let i = 0; i < 81; i++) {
    if (g.givens[i] === 0) {
      await cell(page, i).click();
      await key(page, g.solution[i]).click();
    }
  }
  assert.equal(await page.evaluate(() => window.sudoku.celebrating), false);
  await page.getByTestId('result-dialog').getByText('Geschafft.').waitFor({ timeout: 1000 });
  await context.close();
});
