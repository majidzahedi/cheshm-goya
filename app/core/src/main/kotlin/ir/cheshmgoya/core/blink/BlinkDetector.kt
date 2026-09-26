package ir.cheshmgoya.core.blink

/** Events produced by [BlinkDetector]. Timestamps are monotonic milliseconds. */
sealed interface BlinkEvent {
    val atMs: Long

    /** Eye just closed. Scanning should freeze on the current option. */
    data class Closed(override val atMs: Long) : BlinkEvent

    /** Closure reached [BlinkConfig.minClosedMs]: play the "ready" beep. */
    data class Ready(override val atMs: Long) : BlinkEvent

    /** Deliberate blink finished (eye reopened within the min..max window): select. */
    data class Voluntary(override val atMs: Long, val durationMs: Long) : BlinkEvent

    /** Eye reopened too soon: a natural blink. Scanning resumes, nothing is selected. */
    data class Ignored(override val atMs: Long, val durationMs: Long) : BlinkEvent

    /** Closed longer than max: treated as sleep. Scanning stops, nothing is selected. */
    data class SleepStarted(override val atMs: Long) : BlinkEvent

    /** Eye opened after a sleep-length closure. */
    data class SleepEnded(override val atMs: Long, val durationMs: Long) : BlinkEvent

    data class FaceLost(override val atMs: Long) : BlinkEvent
    data class FaceFound(override val atMs: Long) : BlinkEvent
}

/**
 * Deliberate-blink state machine. Feed it one sample per camera frame.
 *
 * Pure Kotlin and clock-free so it can be unit-tested with synthetic signals.
 */
class BlinkDetector(config: BlinkConfig = BlinkConfig()) {

    var config: BlinkConfig = config
        set(value) {
            field = value
            reset()
        }

    private enum class State { OPEN, CLOSED, SLEEPING }

    private var state = State.OPEN
    private var closedAt = 0L
    private var readyFired = false
    private var smoothed: Float? = null
    private var faceVisible = true

    /** Latest smoothed signal (0 open .. 1 closed), for the debug graph. */
    var lastSignal: Float = 0f
        private set

    /** Milliseconds the eye has been closed as of the last sample, 0 if open. */
    var closedDurationMs: Long = 0
        private set

    /** Progress toward a valid blink, 0..1, for the fill bar on the highlighted option. */
    val progress: Float
        get() = if (state == State.CLOSED) (closedDurationMs.toFloat() / config.minClosedMs).coerceIn(0f, 1f) else 0f

    val isClosed: Boolean get() = state != State.OPEN
    val isSleeping: Boolean get() = state == State.SLEEPING

    fun reset() {
        state = State.OPEN
        readyFired = false
        smoothed = null
        closedDurationMs = 0
    }

    /** Raw per-eye value selected according to [BlinkConfig.eyes]. */
    fun combine(left: Float, right: Float): Float = when (config.eyes) {
        EyeSelection.BOTH -> (left + right) / 2f
        EyeSelection.LEFT -> left
        EyeSelection.RIGHT -> right
    }

    /**
     * @param left MediaPipe eyeBlinkLeft, or null when no face is detected.
     * @param right MediaPipe eyeBlinkRight, or null when no face is detected.
     */
    fun onSample(timeMs: Long, left: Float?, right: Float?): List<BlinkEvent> {
        val events = ArrayList<BlinkEvent>(2)
        if (left == null || right == null) {
            if (faceVisible) {
                faceVisible = false
                events += BlinkEvent.FaceLost(timeMs)
            }
            // A closure in progress cannot be trusted any more: drop it without selecting.
            reset()
            return events
        }
        if (!faceVisible) {
            faceVisible = true
            events += BlinkEvent.FaceFound(timeMs)
        }

        val raw = combine(left, right).coerceIn(0f, 1f)
        val s = smoothed?.let { it + config.smoothingAlpha * (raw - it) } ?: raw
        smoothed = s
        lastSignal = s

        when (state) {
            State.OPEN -> if (s >= config.closeThreshold) {
                state = State.CLOSED
                closedAt = timeMs
                closedDurationMs = 0
                readyFired = false
                events += BlinkEvent.Closed(timeMs)
            }

            State.CLOSED -> {
                closedDurationMs = timeMs - closedAt
                if (s <= config.openThreshold) {
                    val d = closedDurationMs
                    state = State.OPEN
                    closedDurationMs = 0
                    events += if (d >= config.minClosedMs) BlinkEvent.Voluntary(timeMs, d) else BlinkEvent.Ignored(timeMs, d)
                } else if (closedDurationMs > config.maxClosedMs) {
                    state = State.SLEEPING
                    events += BlinkEvent.SleepStarted(timeMs)
                } else if (!readyFired && closedDurationMs >= config.minClosedMs) {
                    readyFired = true
                    events += BlinkEvent.Ready(timeMs)
                }
            }

            State.SLEEPING -> {
                closedDurationMs = timeMs - closedAt
                if (s <= config.openThreshold) {
                    state = State.OPEN
                    val d = closedDurationMs
                    closedDurationMs = 0
                    events += BlinkEvent.SleepEnded(timeMs, d)
                }
            }
        }
        return events
    }
}
