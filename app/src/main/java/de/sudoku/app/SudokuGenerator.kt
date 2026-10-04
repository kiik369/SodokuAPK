package de.sudoku.app

import kotlin.random.Random

enum class Difficulty(val label: String, val clues: Int) {
    EASY("Leicht", 40),
    MEDIUM("Mittel", 32),
    HARD("Schwer", 26)
}

class Puzzle(val solution: IntArray, val givens: IntArray)

/**
 * Erzeugt Sudokus: erst ein vollständig gelöstes Raster per Backtracking,
 * dann werden Zahlen entfernt, solange die Lösung eindeutig bleibt.
 * Index im Raster: row * 9 + col, 0 = leer.
 */
object SudokuGenerator {

    fun generate(difficulty: Difficulty, random: Random = Random.Default): Puzzle {
        val solution = IntArray(81)
        fillGrid(solution, random)

        val puzzle = solution.copyOf()
        var clues = 81
        for (pos in (0 until 81).shuffled(random)) {
            if (clues <= difficulty.clues) break
            val backup = puzzle[pos]
            puzzle[pos] = 0
            if (countSolutions(puzzle.copyOf(), 2) != 1) {
                puzzle[pos] = backup
            } else {
                clues--
            }
        }
        return Puzzle(solution, puzzle)
    }

    private fun fillGrid(grid: IntArray, random: Random): Boolean {
        val idx = grid.indexOfFirst { it == 0 }
        if (idx == -1) return true
        for (d in (1..9).shuffled(random)) {
            if (canPlace(grid, idx, d)) {
                grid[idx] = d
                if (fillGrid(grid, random)) return true
                grid[idx] = 0
            }
        }
        return false
    }

    /** Zählt Lösungen, bricht bei [limit] ab. Nimmt immer das Feld mit den wenigsten Kandidaten. */
    private fun countSolutions(grid: IntArray, limit: Int): Int {
        var best = -1
        var bestCount = 10
        for (i in 0 until 81) {
            if (grid[i] != 0) continue
            var c = 0
            for (d in 1..9) if (canPlace(grid, i, d)) c++
            if (c == 0) return 0
            if (c < bestCount) {
                bestCount = c
                best = i
            }
        }
        if (best == -1) return 1

        var total = 0
        for (d in 1..9) {
            if (!canPlace(grid, best, d)) continue
            grid[best] = d
            total += countSolutions(grid, limit - total)
            grid[best] = 0
            if (total >= limit) return total
        }
        return total
    }

    fun canPlace(grid: IntArray, idx: Int, d: Int): Boolean {
        val row = idx / 9
        val col = idx % 9
        for (i in 0 until 9) {
            if (grid[row * 9 + i] == d) return false
            if (grid[i * 9 + col] == d) return false
        }
        val br = row / 3 * 3
        val bc = col / 3 * 3
        for (r in br until br + 3) {
            for (c in bc until bc + 3) {
                if (grid[r * 9 + c] == d) return false
            }
        }
        return true
    }

    fun isPeer(a: Int, b: Int): Boolean {
        val ra = a / 9
        val ca = a % 9
        val rb = b / 9
        val cb = b % 9
        return ra == rb || ca == cb || (ra / 3 == rb / 3 && ca / 3 == cb / 3)
    }
}
