package ir.cheshmgoya.core

import ir.cheshmgoya.core.blink.BlinkConfig
import ir.cheshmgoya.core.blink.BlinkDetector
import ir.cheshmgoya.core.blink.BlinkEvent
import ir.cheshmgoya.core.blink.EyeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {
    private val frameMs = 33L

    /** Feeds open → closed for [closedMs] → open, returns all events. */
    private fun blink(d: BlinkDetector, closedMs: Long, start: Long = 0, left: Float = 0.9f, right: Float = 0.9f): List<BlinkEvent> {
        val ev = mutableListOf<BlinkEvent>()
        var t = start
        repeat(10) { ev += d.onSample(t, 0.05f, 0.05f); t += frameMs }
        val closeStart = t
        while (t - closeStart < closedMs) { ev += d.onSample(t, left, right); t += frameMs }
        repeat(10) { ev += d.onSample(t, 0.05f, 0.05f); t += frameMs }
        return ev
    }

    private val noSmoothing = BlinkConfig(smoothingAlpha = 1f)

    @Test fun shortNaturalBlinkIsIgnored() {
        val ev = blink(BlinkDetector(noSmoothing), 150)
        assertTrue(ev.any { it is BlinkEvent.Closed })
        assertTrue(ev.any { it is BlinkEvent.Ignored })
        assertTrue(ev.none { it is BlinkEvent.Voluntary || it is BlinkEvent.Ready })
    }

    @Test fun deliberateBlinkSelectsOnOpenAndBeepsReadyFirst() {
        val ev = blink(BlinkDetector(noSmoothing), 800)
        val ready = ev.indexOfFirst { it is BlinkEvent.Ready }
        val vol = ev.indexOfFirst { it is BlinkEvent.Voluntary }
        assertTrue(ready in 0 until vol)
        val v = ev[vol] as BlinkEvent.Voluntary
        assertTrue(v.durationMs in 450..900)
    }

    @Test fun closureLongerThanMaxIsSleepAndSelectsNothing() {
        val ev = blink(BlinkDetector(noSmoothing), 3000)
        assertTrue(ev.any { it is BlinkEvent.SleepStarted })
        assertTrue(ev.any { it is BlinkEvent.SleepEnded })
        assertTrue(ev.none { it is BlinkEvent.Voluntary })
    }

    @Test fun limitsAreConfigurable() {
        val d = BlinkDetector(noSmoothing.copy(minClosedMs = 200, maxClosedMs = 600))
        assertTrue(blink(d, 300).any { it is BlinkEvent.Voluntary })
        assertTrue(blink(d, 900, start = 10_000).any { it is BlinkEvent.SleepStarted })
    }

    @Test fun hysteresisKeepsEyeClosedBetweenThresholds() {
        val d = BlinkDetector(noSmoothing.copy(closeThreshold = 0.5f, openThreshold = 0.3f))
        d.onSample(0, 0.0f, 0.0f)
        d.onSample(33, 0.6f, 0.6f)
        // Hovering at 0.4 (between thresholds) must not reopen.
        val mid = (1..10).flatMap { d.onSample(33 + it * 33L, 0.4f, 0.4f) }
        assertTrue(mid.none { it is BlinkEvent.Ignored || it is BlinkEvent.Voluntary })
        assertTrue(d.isClosed)
    }

    @Test fun smoothingRejectsSingleFrameSpike() {
        val d = BlinkDetector(BlinkConfig(smoothingAlpha = 0.3f))
        d.onSample(0, 0f, 0f)
        val ev = d.onSample(33, 1f, 1f) + d.onSample(66, 0f, 0f)
        assertTrue(ev.none { it is BlinkEvent.Closed })
    }

    @Test fun eyeSelectionUsesOnlyChosenEye() {
        val left = BlinkDetector(noSmoothing.copy(eyes = EyeSelection.LEFT))
        assertTrue(blink(left, 700, left = 0.9f, right = 0.0f).any { it is BlinkEvent.Voluntary })
        val right = BlinkDetector(noSmoothing.copy(eyes = EyeSelection.RIGHT))
        assertTrue(blink(right, 700, left = 0.9f, right = 0.0f).none { it is BlinkEvent.Closed })
    }

    @Test fun faceLossCancelsClosureAndReports() {
        val d = BlinkDetector(noSmoothing)
        d.onSample(0, 0f, 0f)
        d.onSample(33, 0.9f, 0.9f)
        val lost = d.onSample(400, null, null)
        assertEquals(listOf(BlinkEvent.FaceLost(400)), lost)
        val back = d.onSample(500, 0f, 0f) + d.onSample(533, 0f, 0f)
        assertTrue(back.first() is BlinkEvent.FaceFound)
        assertTrue(back.none { it is BlinkEvent.Voluntary || it is BlinkEvent.Ignored })
    }

    @Test fun progressFillsTowardMinimum() {
        val d = BlinkDetector(noSmoothing)
        d.onSample(0, 0f, 0f)
        d.onSample(100, 0.9f, 0.9f)
        d.onSample(325, 0.9f, 0.9f)
        assertEquals(0.5f, d.progress, 0.01f)
        d.onSample(700, 0.9f, 0.9f)
        assertEquals(1f, d.progress, 0.0f)
    }
}
