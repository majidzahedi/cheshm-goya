package ir.cheshmgoya.core

import ir.cheshmgoya.core.scan.Highlight
import ir.cheshmgoya.core.scan.ScanConfig
import ir.cheshmgoya.core.scan.ScanLevel
import ir.cheshmgoya.core.scan.ScanMode
import ir.cheshmgoya.core.scan.ScanSelection
import ir.cheshmgoya.core.scan.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerTest {
    private val cfg = ScanConfig(stepMs = 1000, firstItemFactor = 1.6f, reactionGraceMs = 300)

    private fun linear(n: Int) = Scanner(cfg.copy(mode = ScanMode.LINEAR)).apply { setLayout(listOf(n), 0); start(0) }

    @Test fun firstItemDwellsLonger() {
        val s = linear(4)
        assertFalse(s.tick(1000))
        assertFalse(s.tick(1599))
        assertTrue(s.tick(1600))
        assertEquals(Highlight(ScanLevel.ITEMS, 0, 1), s.highlight())
        assertFalse(s.tick(2599))
        assertTrue(s.tick(2600))
        assertEquals(2, s.highlight()!!.col)
    }

    @Test fun linearWrapsAround() {
        val s = linear(2)
        s.tick(1600); s.tick(2600)
        assertEquals(0, s.highlight()!!.col)
    }

    @Test fun freezeStopsMovementAndUnfreezeResumesWithFullDwell() {
        val s = linear(4)
        s.tick(1600) // -> item 1
        s.freeze(2000)
        assertFalse(s.tick(5000))
        s.unfreeze(5000)
        assertFalse(s.tick(5999))
        assertTrue(s.tick(6000))
        assertEquals(2, s.highlight()!!.col)
    }

    @Test fun reactionGraceSelectsPreviousOptionIfHighlightJustMoved() {
        val s = linear(4)
        s.tick(1600) // moved to 1 at t=1600
        s.freeze(1800) // closed 200 ms after the move → patient meant item 0
        assertEquals(0, s.highlight()!!.col)
        assertEquals(ScanSelection.Item(0, 0), s.select(2400))
    }

    @Test fun noGraceAfterReactionWindow() {
        val s = linear(4)
        s.tick(1600)
        s.freeze(1950)
        assertEquals(ScanSelection.Item(0, 1), s.select(2500))
    }

    @Test fun noGraceForManualMoves() {
        val s = linear(4)
        s.next(1000)
        s.freeze(1100)
        assertEquals(ScanSelection.Item(0, 1), s.select(1600))
    }

    @Test fun rowColumnEntersRowThenSelectsItem() {
        val s = Scanner(cfg.copy(mode = ScanMode.ROW_COLUMN)).apply { setLayout(listOf(3, 3, 3), 0); start(0) }
        assertTrue(s.highlight()!!.isRow)
        s.tick(1600) // row 1
        assertEquals(ScanSelection.EnteredRow(1), s.select(2000))
        assertEquals(Highlight(ScanLevel.ITEMS, 1, 0), s.highlight())
        s.tick(3600) // item 1 (first item dwells 1.6x from t=2000)
        assertEquals(ScanSelection.Item(1, 1), s.select(4000))
        assertTrue(s.highlight()!!.isRow) // back to start after a choice
    }

    @Test fun rowColumnFallsBackToRowsAfterTwoEmptyPasses() {
        val s = Scanner(cfg.copy(mode = ScanMode.ROW_COLUMN)).apply { setLayout(listOf(2, 2), 0); start(0) }
        s.select(0) // enter row 0
        var t = 0L
        // pass 1: 0 (1.6s) → 1 (1s) → wrap; pass 2: 0 → 1 → wrap → back to rows
        val dwell = listOf(1600L, 1000L, 1600L, 1000L)
        for (d in dwell) { t += d; s.tick(t) }
        assertTrue(s.highlight()!!.isRow)
        assertEquals(0, s.highlight()!!.row)
    }

    @Test fun autoModeUsesLinearForShortLists() {
        val s = Scanner(cfg).apply { setLayout(listOf(2, 2), 0) }
        assertFalse(s.isRowColumn)
        s.setLayout(listOf(3, 3, 3, 3), 0)
        assertTrue(s.isRowColumn)
    }

    @Test fun emptyRowsAreSkipped() {
        val s = Scanner(cfg.copy(mode = ScanMode.ROW_COLUMN)).apply { setLayout(listOf(0, 3, 3), 0); start(0) }
        assertEquals(1, s.highlight()!!.row)
        s.tick(1600)
        assertEquals(2, s.highlight()!!.row)
        s.tick(2600)
        assertEquals(1, s.highlight()!!.row)
    }

    @Test fun singleItemRowSelectsDirectly() {
        val s = Scanner(cfg.copy(mode = ScanMode.ROW_COLUMN)).apply { setLayout(listOf(1, 3), 0); start(0) }
        assertEquals(ScanSelection.Item(0, 0), s.select(100))
    }

    @Test fun stoppedScannerDoesNotMove() {
        val s = linear(3)
        s.stop()
        assertFalse(s.tick(10_000))
    }

    @Test fun manualPreviousWraps() {
        val s = linear(3)
        s.previous(10)
        assertEquals(2, s.highlight()!!.col)
    }
}
