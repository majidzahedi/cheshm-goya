package ir.cheshmgoya.core.gaze

import kotlin.math.floor
import kotlin.math.sqrt

data class AccuracyReport(
    /** Mean distance between where the patient looked and where we estimated, in cm. */
    val meanErrorCm: Double,
    /** Frame-to-frame spread of the estimate, in cm. */
    val jitterCm: Double,
    /** Largest number of options that can be chosen directly by gaze on this screen. */
    val maxDirectCells: Int,
)

/** Where the calibration dots appear, and how accuracy turns into usable target sizes. */
object GazeCalibrationPlan {
    /** 9 calibration points (fractions of the screen area). */
    val calibrationTargets: List<Pt> = listOf(0.1, 0.5, 0.9).flatMap { y -> listOf(0.1, 0.5, 0.9).map { x -> Pt(x, y) } }

    /** 5 different points for the accuracy test. */
    val testTargets: List<Pt> = listOf(Pt(0.3, 0.3), Pt(0.7, 0.3), Pt(0.5, 0.5), Pt(0.3, 0.7), Pt(0.7, 0.7))

    const val SETTLE_MS = 1000L
    const val COLLECT_MS = 1300L

    /**
     * @param estimates per test target, the gaze estimates collected (normalised 0..1)
     */
    fun evaluate(targets: List<Pt>, estimates: List<List<Pt>>, screenWidthCm: Double, screenHeightCm: Double): AccuracyReport {
        var errSum = 0.0
        var jitSum = 0.0
        var n = 0
        for ((t, est) in targets.zip(estimates)) {
            if (est.isEmpty()) continue
            val mx = est.sumOf { it.x } / est.size
            val my = est.sumOf { it.y } / est.size
            errSum += cm(Pt(mx, my) - t, screenWidthCm, screenHeightCm)
            jitSum += sqrt(est.sumOf { cm(it - Pt(mx, my), screenWidthCm, screenHeightCm).let { d -> d * d } } / est.size)
            n++
        }
        if (n == 0) return AccuracyReport(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0)
        val err = errSum / n
        val jit = jitSum / n
        return AccuracyReport(err, jit, maxCells(err + jit, screenWidthCm, screenHeightCm))
    }

    /** An option needs to be about 2.5× the typical error wide and tall to be hit reliably. */
    fun maxCells(errorCm: Double, screenWidthCm: Double, screenHeightCm: Double): Int {
        val size = (2.5 * errorCm).coerceAtLeast(1.0)
        val cols = floor(screenWidthCm / size).toInt()
        val rows = floor(screenHeightCm * 0.8 / size).toInt() // leave room for bars and text
        return (cols * rows).coerceIn(0, 60)
    }

    private fun cm(d: Pt, w: Double, h: Double) = kotlin.math.hypot(d.x * w, d.y * h)
}
