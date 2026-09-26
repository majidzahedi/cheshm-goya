package ir.cheshmgoya.core.gaze

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter (Casiez et al. 2012): strong smoothing while the gaze rests,
 * little lag when it moves quickly. Filters a 2-D point.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.3,
    private val dCutoff: Double = 1.0,
) {
    private var last: Pt? = null
    private var lastDeriv = Pt(0.0, 0.0)
    private var lastT = 0L

    fun reset() { last = null }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    fun filter(tMs: Long, p: Pt): Pt {
        val prev = last
        if (prev == null) {
            last = p; lastT = tMs; lastDeriv = Pt(0.0, 0.0)
            return p
        }
        val dt = ((tMs - lastT).coerceAtLeast(1)) / 1000.0
        val deriv = (p - prev) * (1 / dt)
        val ad = alpha(dCutoff, dt)
        val d = lastDeriv + (deriv - lastDeriv) * ad
        val speed = abs(d.x) + abs(d.y)
        val a = alpha(minCutoff + beta * speed, dt)
        val out = prev + (p - prev) * a
        last = out; lastDeriv = d; lastT = tMs
        return out
    }
}
