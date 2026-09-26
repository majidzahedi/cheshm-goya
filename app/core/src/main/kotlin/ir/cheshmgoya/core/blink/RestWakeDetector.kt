package ir.cheshmgoya.core.blink

/** Leaves rest mode after [count] deliberate blinks within [windowMs]. */
class RestWakeDetector(private val count: Int = 3, private val windowMs: Long = 5000) {
    private val times = ArrayDeque<Long>()

    /** Record a deliberate blink; returns true when the wake gesture is complete. */
    fun onVoluntaryBlink(atMs: Long): Boolean {
        times.addLast(atMs)
        while (times.isNotEmpty() && atMs - times.first() > windowMs) times.removeFirst()
        if (times.size >= count) {
            times.clear()
            return true
        }
        return false
    }

    /** Blinks counted so far in the current window (for on-screen feedback). */
    fun pending(nowMs: Long): Int = times.count { nowMs - it <= windowMs }

    fun reset() = times.clear()
}
