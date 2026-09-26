package ir.cheshmgoya.core

import ir.cheshmgoya.core.blink.RestWakeDetector
import ir.cheshmgoya.core.gaze.GazeConfig
import ir.cheshmgoya.core.gaze.GazeDetector
import ir.cheshmgoya.core.gaze.GazeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeAndRestTest {
    private fun hold(g: GazeDetector, value: Float, fromMs: Long, toMs: Long): List<GazeDirection> {
        val out = mutableListOf<GazeDirection>()
        var t = fromMs
        while (t <= toMs) { g.onSample(t, value, false)?.let { out += it }; t += 33 }
        return out
    }

    @Test fun heldLookMovesOnceAfterHoldTime() {
        val g = GazeDetector(GazeConfig(holdMs = 650, repeatMs = 0))
        assertEquals(emptyList<GazeDirection>(), hold(g, 0.5f, 0, 600))
        assertEquals(listOf(GazeDirection.LEFT), hold(g, 0.5f, 633, 3000))
    }

    @Test fun glanceShorterThanHoldIsIgnored() {
        val g = GazeDetector()
        assertTrue(hold(g, -0.5f, 0, 400).isEmpty())
        assertTrue(hold(g, 0f, 433, 2000).isEmpty())
    }

    @Test fun repeatsWhileHeld() {
        val g = GazeDetector(GazeConfig(holdMs = 650, repeatMs = 1000))
        assertEquals(3, hold(g, -0.5f, 0, 2800).size)
    }

    @Test fun invertSwapsDirection() {
        val g = GazeDetector(GazeConfig(inverted = true, repeatMs = 0))
        assertEquals(listOf(GazeDirection.RIGHT), hold(g, 0.5f, 0, 1000))
    }

    @Test fun calibratedCentreIsSubtracted() {
        val g = GazeDetector(GazeConfig(center = 0.3f))
        assertTrue(hold(g, 0.4f, 0, 2000).isEmpty())
    }

    @Test fun closingEyeCancelsPendingLook() {
        val g = GazeDetector(GazeConfig(repeatMs = 0))
        hold(g, 0.5f, 0, 500)
        assertNull(g.onSample(533, 0.5f, eyesClosing = true))
        assertTrue(hold(g, 0.5f, 566, 1100).isEmpty()) // timer restarted
    }

    @Test fun horizontalFromBlendshapes() {
        val g = GazeDetector()
        assertTrue(g.horizontal(lookOutLeft = 0.8f, lookInLeft = 0f, lookInRight = 0.8f, lookOutRight = 0f) > 0.5f)
        assertTrue(g.horizontal(0f, 0.8f, 0f, 0.8f) < -0.5f)
    }

    @Test fun restWakeNeedsThreeBlinksInFiveSeconds() {
        val r = RestWakeDetector()
        assertFalse(r.onVoluntaryBlink(0))
        assertFalse(r.onVoluntaryBlink(2000))
        assertTrue(r.onVoluntaryBlink(4500))
        assertFalse(r.onVoluntaryBlink(10_000))
        assertFalse(r.onVoluntaryBlink(12_000))
        assertFalse(r.onVoluntaryBlink(17_500)) // both earlier ones expired
        assertFalse(r.onVoluntaryBlink(18_000))
        assertTrue(r.onVoluntaryBlink(19_000))
    }
}
