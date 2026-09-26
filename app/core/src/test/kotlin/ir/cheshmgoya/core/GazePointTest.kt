package ir.cheshmgoya.core

import ir.cheshmgoya.core.gaze.CellRect
import ir.cheshmgoya.core.gaze.EyeGeometry
import ir.cheshmgoya.core.gaze.GazeCalibrationPlan
import ir.cheshmgoya.core.gaze.GazeFeatures
import ir.cheshmgoya.core.gaze.GazeModel
import ir.cheshmgoya.core.gaze.GazePointer
import ir.cheshmgoya.core.gaze.GazeSample
import ir.cheshmgoya.core.gaze.OneEuroFilter
import ir.cheshmgoya.core.gaze.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class GazePointTest {
    /**
     * Synthetic face: the irises shift inside the eyes proportionally to where the
     * patient looks (screen point 0..1), plus pixel noise and small head movement.
     */
    private fun face(target: Pt, rnd: Random, noise: Double = 0.4, headShift: Double = 0.0): EyeGeometry {
        fun n() = (rnd.nextDouble() - 0.5) * 2 * noise
        val hx = 320.0 + headShift
        val hy = 240.0
        // image is unmirrored: looking to the screen's left moves the irises to the image's right
        val dx = (0.5 - target.x) * 12
        val dy = (target.y - 0.5) * 7
        fun p(x: Double, y: Double) = ir.cheshmgoya.core.gaze.Pt(hx + x + n(), hy + y + n())
        return EyeGeometry(
            rightOuter = p(-60.0, 0.0), rightInner = p(-20.0, 0.0), rightUpper = p(-40.0, -8.0 + dy * 0.3), rightLower = p(-40.0, 8.0),
            rightIris = p(-40.0 + dx, dy),
            leftInner = p(20.0, 0.0), leftOuter = p(60.0, 0.0), leftUpper = p(40.0, -8.0 + dy * 0.3), leftLower = p(40.0, 8.0),
            leftIris = p(40.0 + dx, dy),
            noseTip = p(0.0, 45.0), imageWidth = 640, imageHeight = 480,
        )
    }

    private fun calibrate(rnd: Random): GazeModel {
        val samples = GazeCalibrationPlan.calibrationTargets.flatMap { t -> List(25) { GazeSample(GazeFeatures.extract(face(t, rnd)), t) } }
        return GazeModel.fit(samples)
    }

    @Test fun featuresFollowTheIris() {
        val rnd = Random(1)
        val left = GazeFeatures.extract(face(Pt(0.1, 0.5), rnd, noise = 0.0))
        val right = GazeFeatures.extract(face(Pt(0.9, 0.5), rnd, noise = 0.0))
        assertEquals(GazeFeatures.SIZE, left.size)
        assertTrue(left[0] > right[0] && left[2] > right[2])
    }

    @Test fun calibratedModelEstimatesNewPoints() {
        val rnd = Random(7)
        val model = calibrate(rnd)
        for (t in GazeCalibrationPlan.testTargets) {
            val est = List(30) { model.predict(GazeFeatures.extract(face(t, rnd))) }
            val mx = est.sumOf { it.x } / est.size
            val my = est.sumOf { it.y } / est.size
            assertEquals("x for $t", t.x, mx, 0.05)
            assertEquals("y for $t", t.y, my, 0.05)
        }
    }

    @Test fun modelSurvivesJsonRoundTrip() {
        val model = calibrate(Random(3))
        val back = GazeModel.fromJson(model.toJson())!!
        val f = GazeFeatures.extract(face(Pt(0.3, 0.6), Random(4), noise = 0.0))
        assertEquals(model.predict(f), back.predict(f))
        assertNull(GazeModel.fromJson("{}"))
    }

    @Test fun oneEuroSmoothsJitterAtRest() {
        val f = OneEuroFilter()
        val rnd = Random(5)
        val raw = List(90) { Pt(0.5 + (rnd.nextDouble() - 0.5) * 0.1, 0.5) }
        val out = raw.mapIndexed { i, p -> f.filter(i * 33L, p) }.drop(20)
        fun sd(xs: List<Double>): Double { val m = xs.average(); return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size) }
        assertTrue(sd(out.map { it.x }) < sd(raw.drop(20).map { it.x }) / 2)
    }

    private val cells = listOf(
        CellRect(0, 0, 0.0, 0.0, 0.5, 0.5), CellRect(0, 1, 0.5, 0.0, 1.0, 0.5),
        CellRect(1, 0, 0.0, 0.5, 0.5, 1.0), CellRect(1, 1, 0.5, 0.5, 1.0, 1.0),
    )

    @Test fun pointerNeedsDwellBeforeMoving() {
        val p = GazePointer(dwellMs = 300)
        assertFalse(p.update(0, Pt(0.2, 0.2), cells, false))
        assertFalse(p.update(200, Pt(0.2, 0.2), cells, false))
        assertTrue(p.update(300, Pt(0.2, 0.2), cells, false))
        assertEquals(0 to 0, p.current!!.row to p.current!!.col)
        // a glance at another cell shorter than the dwell doesn't move it
        p.update(400, Pt(0.8, 0.8), cells, false)
        p.update(500, Pt(0.2, 0.2), cells, false)
        p.update(900, Pt(0.2, 0.2), cells, false)
        assertEquals(0 to 0, p.current!!.row to p.current!!.col)
    }

    @Test fun pointerHysteresisOnBorders() {
        val p = GazePointer(dwellMs = 0, margin = 0.05)
        p.update(0, Pt(0.4, 0.2), cells, false); p.update(1, Pt(0.4, 0.2), cells, false)
        assertEquals(0, p.current!!.col)
        // just across the border but within the margin: stays
        p.update(2, Pt(0.53, 0.2), cells, false); p.update(3, Pt(0.53, 0.2), cells, false)
        assertEquals(0, p.current!!.col)
        p.update(4, Pt(0.7, 0.2), cells, false); p.update(5, Pt(0.7, 0.2), cells, false)
        assertEquals(1, p.current!!.col)
    }

    @Test fun pointerFreezesWhileEyeClosesAndRemembers() {
        val p = GazePointer(dwellMs = 100)
        p.update(0, Pt(0.2, 0.2), cells, false); p.update(100, Pt(0.2, 0.2), cells, false)
        p.update(1000, Pt(0.8, 0.8), cells, false); p.update(1100, Pt(0.8, 0.8), cells, false)
        assertEquals(1, p.current!!.row)
        assertFalse(p.update(1200, Pt(0.2, 0.9), cells, frozen = true))
        assertEquals(0, p.cellAt(900)!!.row) // what was lit just before
        assertEquals(1, p.cellAt(1150)!!.row)
    }

    @Test fun lookingAwayHighlightsNothing() {
        val p = GazePointer(dwellMs = 0)
        p.update(0, Pt(3.0, 3.0), cells, false); p.update(1, Pt(3.0, 3.0), cells, false)
        assertNull(p.current)
    }

    @Test fun accuracyDecidesUsableTargets() {
        val targets = GazeCalibrationPlan.testTargets
        val good = GazeCalibrationPlan.evaluate(targets, targets.map { t -> List(10) { Pt(t.x + 0.01, t.y) } }, 22.0, 14.0)
        val bad = GazeCalibrationPlan.evaluate(targets, targets.map { t -> List(10) { Pt(t.x + 0.2, t.y + 0.2) } }, 22.0, 14.0)
        assertTrue(good.meanErrorCm < 0.5 && good.maxDirectCells >= 40)
        assertTrue(bad.meanErrorCm > 4 && bad.maxDirectCells < 12)
        assertNotNull(bad)
    }
}
