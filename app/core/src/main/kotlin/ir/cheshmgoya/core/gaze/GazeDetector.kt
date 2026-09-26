package ir.cheshmgoya.core.gaze

enum class GazeDirection { LEFT, RIGHT }

data class GazeConfig(
    /** Gaze must be held this long off-centre before the highlight moves. */
    val holdMs: Long = 650,
    /** Deflection from the calibrated centre needed to count as looking sideways. */
    val threshold: Float = 0.25f,
    /** Deflection must fall below this before a new look counts (hysteresis). */
    val releaseThreshold: Float = 0.12f,
    /** While still held, repeat the move every this many ms. 0 disables repeat. */
    val repeatMs: Long = 1000,
    /** Calibrated resting value. */
    val center: Float = 0f,
    /** Swap left and right (e.g. mirrored camera or patient preference). */
    val inverted: Boolean = false,
)

/**
 * Detects a held sideways look from MediaPipe `eyeLook*` blendshapes.
 */
class GazeDetector(var config: GazeConfig = GazeConfig()) {
    private var current: GazeDirection? = null
    private var since = 0L
    private var lastFired = 0L
    private var firedOnce = false

    /** Latest deflection (positive = patient looks to their left), for the debug view. */
    var lastDeflection = 0f
        private set

    fun reset() {
        current = null
        firedOnce = false
    }

    /**
     * Horizontal gaze from blendshapes; positive = the patient looks to their own left
     * (which is also the left of the screen they are facing).
     */
    fun horizontal(lookOutLeft: Float, lookInLeft: Float, lookInRight: Float, lookOutRight: Float): Float =
        ((lookOutLeft + lookInRight) - (lookInLeft + lookOutRight)) / 2f

    /**
     * @param eyesClosing true while the blink detector sees the eye closing; gaze
     * readings are meaningless then and a pending look is cancelled.
     * @return the direction to move, at most once per hold (plus repeats).
     */
    fun onSample(timeMs: Long, horizontal: Float?, eyesClosing: Boolean): GazeDirection? {
        if (horizontal == null || eyesClosing) {
            reset()
            return null
        }
        val d = horizontal - config.center
        lastDeflection = d
        val dir = when {
            d >= config.threshold -> GazeDirection.LEFT
            d <= -config.threshold -> GazeDirection.RIGHT
            current != null && kotlin.math.abs(d) > config.releaseThreshold -> current
            else -> null
        }
        if (dir != current) {
            current = dir
            since = timeMs
            firedOnce = false
            return null
        }
        if (dir == null) return null
        val out = if (config.inverted) (if (dir == GazeDirection.LEFT) GazeDirection.RIGHT else GazeDirection.LEFT) else dir
        if (!firedOnce && timeMs - since >= config.holdMs) {
            firedOnce = true
            lastFired = timeMs
            return out
        }
        if (firedOnce && config.repeatMs > 0 && timeMs - lastFired >= config.repeatMs) {
            lastFired = timeMs
            return out
        }
        return null
    }
}
