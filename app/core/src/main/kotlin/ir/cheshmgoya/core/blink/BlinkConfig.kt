package ir.cheshmgoya.core.blink

enum class EyeSelection { BOTH, LEFT, RIGHT }

/**
 * Tunables for voluntary-blink detection. Thresholds are on the MediaPipe
 * `eyeBlink*` blendshape scale (0 = open, 1 = closed) after calibration.
 */
data class BlinkConfig(
    /** Eye must stay closed at least this long to count as a deliberate blink. */
    val minClosedMs: Long = 450,
    /** Closed longer than this = sleep / rest: never selects anything. */
    val maxClosedMs: Long = 2000,
    /** Smoothed signal above this = eye closed. */
    val closeThreshold: Float = 0.5f,
    /** Smoothed signal must fall below this to count as open again (hysteresis). */
    val openThreshold: Float = 0.35f,
    /** Exponential moving average factor for new samples (1 = no smoothing). */
    val smoothingAlpha: Float = 0.6f,
    val eyes: EyeSelection = EyeSelection.BOTH,
) {
    init {
        require(minClosedMs in 50..maxClosedMs) { "minClosedMs must be positive and ≤ maxClosedMs" }
        require(openThreshold < closeThreshold) { "openThreshold must be below closeThreshold" }
        require(smoothingAlpha in 0.05f..1f)
    }
}
