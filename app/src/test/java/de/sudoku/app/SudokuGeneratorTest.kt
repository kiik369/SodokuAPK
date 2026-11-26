package de.sudoku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuGeneratorTest {

    private fun assertValidSolution(grid: IntArray) {
        assertEquals(81, grid.size)
        for (i in 0 until 9) {
            val row = (0 until 9).map { grid[i * 9 + it] }.toSet()
            val col = (0 until 9).map { grid[it * 9 + i] }.toSet()
            val br = i / 3 * 3
            val bc = i % 3 * 3
            val box = (0 until 9).map { grid[(br + it / 3) * 9 + bc + it % 3] }.toSet()
            assertEquals("Zeile $i", (1..9).toSet(), row)
            assertEquals("Spalte $i", (1..9).toSet(), col)
            assertEquals("Block $i", (1..9).toSet(), box)
        }
    }

    /** Unabhängiger Löser, um die Eindeutigkeit zu prüfen. */
    private fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val idx = grid.indexOfFirst { it == 0 }
        if (idx == -1) return 1
        var total = 0
        for (d in 1..9) {
            if (SudokuGenerator.canPlace(grid, idx, d)) {
                grid[idx] = d
                total += countSolutions(grid, limit - total)
                grid[idx] = 0
                if (total >= limit) break
            }
        }
        return total
    }

    @Test
    fun generatedPuzzlesAreValidAndUnique() {
        for (difficulty in listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            repeat(3) { seed ->
                val p = SudokuGenerator.generate(difficulty, Random(seed * 31 + difficulty.ordinal))
                assertValidSolution(p.solution)

                for (i in 0 until 81) {
                    if (p.givens[i] != 0) assertEquals("Vorgabe $i passt nicht zur Lösung", p.solution[i], p.givens[i])
                }

                val clues = p.givens.count { it != 0 }
                assertTrue("$difficulty: $clues Vorgaben, erwartet mindestens ${difficulty.clues}", clues >= difficulty.clues)
                assertEquals("$difficulty: Rätsel muss genau eine Lösung haben", 1, countSolutions(p.givens.copyOf()))
            }
        }
    }

    @Test
    fun customClueTargetIsRespected() {
        for (target in listOf(22, 34, 50)) {
            val p = SudokuGenerator.generate(target, Random(target))
            assertTrue(p.givens.count { it != 0 } >= target)
            assertEquals(1, countSolutions(p.givens.copyOf()))
        }
    }

    @Test
    fun canPlaceChecksRowColumnAndBox() {
        val grid = IntArray(81)
        grid[0] = 5 // Zeile 0, Spalte 0, Block 0
        assertFalse(SudokuGenerator.canPlace(grid, 8, 5))  // gleiche Zeile
        assertFalse(SudokuGenerator.canPlace(grid, 72, 5)) // gleiche Spalte
        assertFalse(SudokuGenerator.canPlace(grid, 20, 5)) // gleicher Block
        assertTrue(SudokuGenerator.canPlace(grid, 40, 5))  // Mitte, kein Konflikt
        assertTrue(SudokuGenerator.canPlace(grid, 8, 4))   // andere Ziffer
    }

    @Test
    fun isPeerMatchesRowColumnAndBox() {
        assertTrue(SudokuGenerator.isPeer(0, 8))
        assertTrue(SudokuGenerator.isPeer(0, 72))
        assertTrue(SudokuGenerator.isPeer(0, 20))
        assertFalse(SudokuGenerator.isPeer(0, 40))
        assertFalse(SudokuGenerator.isPeer(0, 30))
    }
}
