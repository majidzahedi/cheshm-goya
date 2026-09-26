package ir.cheshmgoya.core.gaze

/** A selectable option's rectangle, normalised 0..1 over the app window. */
data class CellRect(val row: Int, val col: Int, val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun contains(p: Pt, margin: Double = 0.0) = p.x >= left - margin && p.x <= right + margin && p.y >= top - margin && p.y <= bottom + margin
    val center get() = Pt((left + right) / 2, (top + bottom) / 2)
}

/**
 * Decides which option the patient is looking at. The highlight only moves
 * after the gaze has rested on a new option for [dwellMs], and it stays on the
 * current option while the gaze is within [margin] of it (hysteresis), so
 * jitter on a border doesn't make it flicker. While the eye is closing the
 * pointer freezes; [cellAt] gives what was highlighted just before.
 */
class GazePointer(
    var dwellMs: Long = 300,
    var margin: Double = 0.03,
    /** Gaze farther than this from every option (e.g. off-screen) highlights nothing. */
    var maxDistance: Double = 0.12,
) {
    var current: CellRect? = null
        private set
    private var pending: CellRect? = null
    private var pendingSince = 0L
    private val history = ArrayDeque<Pair<Long, CellRect?>>()

    fun reset() {
        current = null
        pending = null
        history.clear()
    }

    /** @return true if the highlighted option changed. */
    fun update(tMs: Long, point: Pt?, cells: List<CellRect>, frozen: Boolean): Boolean {
        if (frozen || point == null || cells.isEmpty()) return false
        val cur = current
        val candidate = when {
            cur != null && cur.contains(point, margin) -> cur
            else -> cells.firstOrNull { it.contains(point) }
                ?: cells.minByOrNull { it.center.distanceTo(point) }?.takeIf { nearestEdgeDistance(it, point) <= maxDistance }
        }
        if (sameCell(candidate, cur)) {
            pending = null
            return false
        }
        if (!sameCell(candidate, pending)) {
            pending = candidate
            pendingSince = tMs
            return false
        }
        if (tMs - pendingSince < dwellMs) return false
        current = candidate
        pending = null
        history.addLast(tMs to candidate)
        while (history.size > 20) history.removeFirst()
        return true
    }

    /** The option that was highlighted at time [tMs] (for "the eye started closing here"). */
    fun cellAt(tMs: Long): CellRect? {
        var result: CellRect? = null
        var found = false
        for ((t, c) in history) {
            if (t <= tMs) { result = c; found = true } else break
        }
        return if (found) result else current.takeIf { history.isEmpty() }
    }

    private fun sameCell(a: CellRect?, b: CellRect?) = a?.row == b?.row && a?.col == b?.col

    private fun nearestEdgeDistance(c: CellRect, p: Pt): Double {
        val dx = maxOf(c.left - p.x, 0.0, p.x - c.right)
        val dy = maxOf(c.top - p.y, 0.0, p.y - c.bottom)
        return kotlin.math.hypot(dx, dy)
    }
}
