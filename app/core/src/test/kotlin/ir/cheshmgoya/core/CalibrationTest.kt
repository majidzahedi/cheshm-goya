package ir.cheshmgoya.core

import ir.cheshmgoya.core.blink.Calibration
import ir.cheshmgoya.core.blink.CalibrationOutcome
import ir.cheshmgoya.core.blink.CalibrationSample
import ir.cheshmgoya.core.blink.EyeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    @Test fun computesThresholdsBetweenOpenAndClosed() {
        val open = List(100) { CalibrationSample(0.1f, 0.1f, 0.05f) }
        val closed = List(80) { CalibrationSample(0.8f, 0.8f, 0f) }
        val r = (Calibration.compute(open, closed, EyeSelection.BOTH) as CalibrationOutcome.Success).result
        assertEquals(0.1f, r.openLevel, 1e-4f)
        assertEquals(0.8f, r.closedLevel, 1e-4f)
        assertTrue(r.openThreshold > r.openLevel && r.openThreshold < r.closeThreshold && r.closeThreshold < r.closedLevel)
        assertEquals(0.05f, r.gazeCenter, 1e-4f)
    }

    @Test fun spontaneousBlinksDuringOpenPhaseDoNotSpoilCalibration() {
        val open = List(100) { i -> if (i % 20 == 0) CalibrationSample(0.9f, 0.9f, 0f) else CalibrationSample(0.1f, 0.1f, 0f) }
        val closed = List(80) { CalibrationSample(0.7f, 0.7f, 0f) }
        val r = (Calibration.compute(open, closed, EyeSelection.BOTH) as CalibrationOutcome.Success).result
        assertEquals(0.1f, r.openLevel, 1e-4f)
    }

    @Test fun failsWhenSignalsDoNotSeparate() {
        val open = List(100) { CalibrationSample(0.3f, 0.3f, 0f) }
        val closed = List(80) { CalibrationSample(0.35f, 0.35f, 0f) }
        assertTrue(Calibration.compute(open, closed, EyeSelection.BOTH) is CalibrationOutcome.Failure)
    }

    @Test fun failsWithoutEnoughFrames() {
        assertTrue(Calibration.compute(emptyList(), emptyList(), EyeSelection.BOTH) is CalibrationOutcome.Failure)
    }
}
