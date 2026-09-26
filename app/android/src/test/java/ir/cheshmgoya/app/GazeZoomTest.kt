package ir.cheshmgoya.app

import ir.cheshmgoya.app.ui.CellAction
import ir.cheshmgoya.app.ui.GridBuilder
import ir.cheshmgoya.core.keyboard.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeZoomTest {
    private val keyboard = GridBuilder.keyboard(KeyboardLayout.FREQUENCY, listOf("سلام", "سرم"), emptyList())

    @Test fun overviewTurnsRowsIntoBigGroups() {
        val o = GridBuilder.zoomOverview(keyboard)
        val cells = o.rows.flatten()
        // the all-empty sentence row is skipped; every other row is one target
        assertEquals(keyboard.rows.count { r -> r.any { it.action != CellAction.None } }, cells.size)
        assertTrue(o.rows.all { it.size <= 3 })
        assertTrue(cells.first().action is CellAction.ZoomRow)
        assertTrue(GridBuilder.cellCount(o) < GridBuilder.cellCount(keyboard) / 3)
    }

    @Test fun zoomInShowsTheRowPlusBack() {
        val lettersRow = 3 // first row of letters (after words, sentences, controls)
        val z = GridBuilder.zoomIn(keyboard, lettersRow)
        val cells = z.rows.flatten()
        assertEquals(keyboard.rows[lettersRow].size + 1, cells.size)
        assertEquals(CellAction.ZoomOut, cells.last().action)
        assertTrue(cells.dropLast(1).all { it.action is CellAction.Type })
    }

    @Test fun singleOptionRowsActDirectly() {
        val cat = GridBuilder.painScale()
        val o = GridBuilder.zoomOverview(GridBuilder.category(ir.cheshmgoya.core.phrases.CategoryId.MY_PHRASES, listOf("عینکم رو بدید")))
        assertEquals(CellAction.GoHome, o.rows[0][0].action)
        assertEquals(CellAction.Say("عینکم رو بدید"), o.rows[0][1].action)
        assertTrue(GridBuilder.cellCount(cat) > 0)
    }
}
