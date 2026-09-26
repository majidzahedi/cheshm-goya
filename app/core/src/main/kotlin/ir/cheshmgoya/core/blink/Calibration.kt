package ir.cheshmgoya.core.blink

/** One camera frame's worth of the signals used for calibration. */
data class CalibrationSample(val left: Float, val right: Float, val gazeHorizontal: Float)

data class CalibrationResult(
    val openLevel: Float,
    val closedLevel: Float,
    val closeThreshold: Float,
    val openThreshold: Float,
    /** Resting horizontal gaze value when looking at the middle of the screen. */
    val gazeCenter: Float,
)

sealed interface CalibrationOutcome {
    data class Success(val result: CalibrationResult) : CalibrationOutcome
    data class Failure(val reasonFa: String) : CalibrationOutcome
}

/**
 * Turns the guided calibration recordings (≈4 s eyes open looking at the centre,
 * then ≈3 s eyes closed) into thresholds.
 */
object Calibration {
    const val OPEN_PHASE_MS = 4000L
    const val CLOSED_PHASE_MS = 3000L
    private const val MIN_SAMPLES = 10
    private const val MIN_SEPARATION = 0.15f

    fun compute(open: List<CalibrationSample>, closed: List<CalibrationSample>, eyes: EyeSelection): CalibrationOutcome {
        if (open.size < MIN_SAMPLES || closed.size < MIN_SAMPLES) {
            return CalibrationOutcome.Failure("چهره به‌اندازه‌ی کافی دیده نشد. تبلت را روبه‌روی صورت بگذارید و دوباره امتحان کنید.")
        }
        val detector = BlinkDetector(BlinkConfig(eyes = eyes))
        // Robust statistics: ignore the transition frames at the edges of each phase.
        val openVals = open.map { detector.combine(it.left, it.right) }.sorted()
        val closedVals = closed.map { detector.combine(it.left, it.right) }.sorted()
        val openLevel = percentile(openVals, 0.75f) // spontaneous blinks during the open phase push the top up; 75th is safe
        val closedLevel = percentile(closedVals, 0.25f)
        val sep = closedLevel - openLevel
        if (sep < MIN_SEPARATION) {
            return CalibrationOutcome.Failure("تفاوت چشم باز و بسته خیلی کم بود. نور صورت را بیشتر کنید یا چشم دیگر را انتخاب کنید.")
        }
        val gazeCenter = median(open.map { it.gazeHorizontal })
        return CalibrationOutcome.Success(
            CalibrationResult(
                openLevel = openLevel,
                closedLevel = closedLevel,
                closeThreshold = openLevel + sep * 0.55f,
                openThreshold = openLevel + sep * 0.35f,
                gazeCenter = gazeCenter,
            )
        )
    }

    private fun percentile(sorted: List<Float>, p: Float): Float =
        sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)]

    private fun median(values: List<Float>): Float = percentile(values.sorted(), 0.5f)
}
