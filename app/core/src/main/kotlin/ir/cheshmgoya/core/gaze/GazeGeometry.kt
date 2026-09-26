package ir.cheshmgoya.core.gaze

import kotlin.math.hypot

/** A point in image pixels (or normalised screen units, depending on context). */
data class Pt(val x: Double, val y: Double) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    fun dot(o: Pt) = x * o.x + y * o.y
    fun length() = hypot(x, y)
    fun distanceTo(o: Pt) = (this - o).length()
}

/**
 * The few MediaPipe Face Landmarker points (in image pixels) needed to estimate
 * where on the screen the patient is looking.
 *
 * "Right"/"left" are the patient's own eyes. In the (unmirrored) front-camera
 * image the patient's right eye is on the image's left.
 */
data class EyeGeometry(
    val rightOuter: Pt, // 33
    val rightInner: Pt, // 133
    val rightUpper: Pt, // 159
    val rightLower: Pt, // 145
    val rightIris: Pt,  // 468
    val leftInner: Pt,  // 362
    val leftOuter: Pt,  // 263
    val leftUpper: Pt,  // 386
    val leftLower: Pt,  // 374
    val leftIris: Pt,   // 473
    val noseTip: Pt,    // 1
    val imageWidth: Int,
    val imageHeight: Int,
) {
    companion object {
        /** Landmark indices, in constructor order (without the image size). */
        val INDICES = intArrayOf(33, 133, 159, 145, 468, 362, 263, 386, 374, 473, 1)

        fun fromLandmarks(xs: FloatArray, ys: FloatArray, width: Int, height: Int): EyeGeometry {
            fun p(i: Int) = Pt(xs[i] * width.toDouble(), ys[i] * height.toDouble())
            return EyeGeometry(p(33), p(133), p(159), p(145), p(468), p(362), p(263), p(386), p(374), p(473), p(1), width, height)
        }
    }
}

/**
 * Turns eye geometry into a feature vector for the gaze regression:
 * iris position inside each eye (horizontal along the corner axis, vertical
 * relative to the lids) plus head-pose proxies, so small head movements are
 * partly compensated.
 */
object GazeFeatures {
    const val SIZE = 11

    fun extract(g: EyeGeometry): DoubleArray {
        // Both corner axes run from image-left to image-right.
        val r = eye(g.rightOuter, g.rightInner, g.rightUpper, g.rightLower, g.rightIris)
        val l = eye(g.leftInner, g.leftOuter, g.leftUpper, g.leftLower, g.leftIris)
        val eyesMid = (g.rightOuter + g.leftOuter) * 0.5
        val eyeDist = g.rightOuter.distanceTo(g.leftOuter).coerceAtLeast(1.0)
        val yaw = (g.noseTip.x - eyesMid.x) / eyeDist
        val pitch = (g.noseTip.y - eyesMid.y) / eyeDist
        val headX = eyesMid.x / g.imageWidth
        val headY = eyesMid.y / g.imageHeight
        val scale = eyeDist / g.imageWidth
        val u = (r.u + l.u) / 2
        // The upper lid follows vertical gaze, so eye opening helps the vertical estimate.
        val open = (r.open + l.open) / 2
        return doubleArrayOf(r.u, r.v, l.u, l.v, open, yaw, pitch, headX, headY, scale, u * u)
    }

    private class EyeFeat(val u: Double, val v: Double, val open: Double)

    /** Iris offset from the eye centre along/perpendicular to the corner axis, in eye-widths. */
    private fun eye(a: Pt, b: Pt, upper: Pt, lower: Pt, iris: Pt): EyeFeat {
        val axis = b - a
        val width = axis.length().coerceAtLeast(1e-6)
        val ex = axis * (1 / width)
        val ey = Pt(-ex.y, ex.x)
        val d = iris - (a + b) * 0.5
        return EyeFeat(d.dot(ex) / width, d.dot(ey) / width, upper.distanceTo(lower) / width)
    }
}
