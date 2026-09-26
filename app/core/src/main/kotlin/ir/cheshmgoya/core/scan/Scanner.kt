package ir.cheshmgoya.core.scan

enum class ScanMode {
    /** Row-column for large grids, linear for short lists. */
    AUTO,
    LINEAR,
    ROW_COLUMN,
}

data class ScanConfig(
    /** Time each option stays highlighted. */
    val stepMs: Long = 1800,
    /** The first option of every cycle stays highlighted this many times longer. */
    val firstItemFactor: Float = 1.6f,
    /** If the highlight moved less than this before the eye closed, the previous option is meant. */
    val reactionGraceMs: Long = 300,
    /** In row-column scanning, return to row level after this many empty passes through a row. */
    val rowFallbackRounds: Int = 2,
    val mode: ScanMode = ScanMode.AUTO,
    /** In [ScanMode.AUTO], grids with at least this many options use row-column scanning. */
    val rowColumnMinItems: Int = 9,
)

enum class ScanLevel { ROWS, ITEMS }

/** What is highlighted. When [level] is ROWS the whole [row] is highlighted and [col] is -1. */
data class Highlight(val level: ScanLevel, val row: Int, val col: Int) {
    val isRow get() = level == ScanLevel.ROWS
}

sealed interface ScanSelection {
    data class Item(val row: Int, val col: Int) : ScanSelection
    /** A row was chosen; scanning now continues inside it. */
    data class EnteredRow(val row: Int) : ScanSelection
    data object None : ScanSelection
}

/**
 * Switch-access scanning state machine: automatic stepping, manual stepping
 * (gaze / keys), freezing while the eye is closed and the reaction-time grace.
 *
 * Layouts are given as the number of selectable cells in each row; empty rows
 * are skipped.
 */
class Scanner(config: ScanConfig = ScanConfig()) {

    var config: ScanConfig = config
        private set

    private var rows: List<Int> = emptyList()
    private var rowColumn = false

    private var level = ScanLevel.ITEMS
    private var row = 0
    private var col = 0
    private var prev: Triple<ScanLevel, Int, Int>? = null
    private var lastMoveAt = Long.MIN_VALUE / 2
    private var lastMoveAuto = false
    private var stepStart = 0L
    private var passesInRow = 0

    var running = false
        private set
    var frozen = false
        private set

    val isRowColumn: Boolean get() = rowColumn
    val isEmpty: Boolean get() = rows.all { it == 0 }

    fun updateConfig(newConfig: ScanConfig, nowMs: Long) {
        config = newConfig
        setLayout(rows, nowMs)
    }

    fun setLayout(rowSizes: List<Int>, nowMs: Long) {
        rows = rowSizes
        val total = rowSizes.sum()
        val nonEmptyRows = rowSizes.count { it > 0 }
        rowColumn = when (config.mode) {
            ScanMode.LINEAR -> false
            ScanMode.ROW_COLUMN -> nonEmptyRows > 1
            ScanMode.AUTO -> nonEmptyRows > 1 && total >= config.rowColumnMinItems
        }
        restart(nowMs)
    }

    /** Back to the first option (or first row) of the current layout. */
    fun restart(nowMs: Long) {
        level = if (rowColumn) ScanLevel.ROWS else ScanLevel.ITEMS
        row = firstRow()
        col = 0
        prev = null
        passesInRow = 0
        frozen = false
        stepStart = nowMs
        lastMoveAt = Long.MIN_VALUE / 2
    }

    fun start(nowMs: Long) {
        running = true
        frozen = false
        stepStart = nowMs
    }

    fun stop() {
        running = false
        frozen = false
    }

    fun highlight(): Highlight? {
        if (isEmpty) return null
        return if (level == ScanLevel.ROWS) Highlight(ScanLevel.ROWS, row, -1) else Highlight(ScanLevel.ITEMS, row, col)
    }

    /** Current dwell time: the first option of each cycle is held longer. */
    fun currentDwellMs(): Long {
        val first = if (level == ScanLevel.ROWS) row == firstRow() else if (rowColumn) col == 0 else (row == firstRow() && col == 0)
        return if (first) (config.stepMs * config.firstItemFactor).toLong() else config.stepMs
    }

    /** Advance automatically when the dwell time is over. Returns true if the highlight changed. */
    fun tick(nowMs: Long): Boolean {
        if (!running || frozen || isEmpty) return false
        if (nowMs - stepStart < currentDwellMs()) return false
        step(forward = true, nowMs = nowMs, auto = true)
        return true
    }

    /** Manual move (gaze, arrow keys, volume keys). */
    fun next(nowMs: Long) = step(forward = true, nowMs = nowMs, auto = false)
    fun previous(nowMs: Long) = step(forward = false, nowMs = nowMs, auto = false)

    /**
     * Eye closed: stop moving. If the highlight moved automatically within the
     * reaction grace period, it goes back to the option the patient was reacting to.
     */
    fun freeze(nowMs: Long) {
        if (isEmpty) return
        applyGrace(nowMs)
        frozen = true
    }

    /** The closure was not a deliberate blink: continue scanning with a full dwell. */
    fun unfreeze(nowMs: Long) {
        frozen = false
        stepStart = nowMs
    }

    /**
     * Choose the highlighted option. Call on eye-open after a voluntary blink, or on a switch press.
     */
    fun select(nowMs: Long): ScanSelection {
        if (isEmpty) return ScanSelection.None
        if (!frozen) applyGrace(nowMs)
        frozen = false
        stepStart = nowMs
        prev = null
        lastMoveAt = Long.MIN_VALUE / 2
        return if (level == ScanLevel.ROWS) {
            if (rows[row] == 1) {
                val chosen = ScanSelection.Item(row, 0)
                restart(nowMs)
                chosen
            } else {
                level = ScanLevel.ITEMS
                col = 0
                passesInRow = 0
                ScanSelection.EnteredRow(row)
            }
        } else {
            val chosen = ScanSelection.Item(row, col)
            restart(nowMs)
            chosen
        }
    }

    /** Jump straight to a cell (e.g. touch). */
    fun moveTo(r: Int, c: Int, nowMs: Long) {
        if (r !in rows.indices || c !in 0 until rows[r]) return
        level = ScanLevel.ITEMS
        row = r
        col = c
        stepStart = nowMs
    }

    private fun applyGrace(nowMs: Long) {
        val p = prev
        if (p != null && lastMoveAuto && nowMs - lastMoveAt < config.reactionGraceMs) {
            level = p.first
            row = p.second
            col = p.third
            prev = null
        }
    }

    private fun firstRow(): Int = rows.indexOfFirst { it > 0 }.coerceAtLeast(0)

    private fun step(forward: Boolean, nowMs: Long, auto: Boolean) {
        if (isEmpty) return
        prev = Triple(level, row, col)
        when {
            level == ScanLevel.ROWS -> row = nextRow(row, forward)
            rowColumn -> { // items inside one row
                val n = rows[row]
                col = if (forward) col + 1 else col - 1
                if (col >= n) {
                    col = 0
                    passesInRow++
                    if (auto && passesInRow >= config.rowFallbackRounds) {
                        level = ScanLevel.ROWS
                        row = firstRow()
                        passesInRow = 0
                    }
                } else if (col < 0) {
                    col = n - 1
                }
            }
            else -> { // linear through every cell
                if (forward) {
                    col++
                    if (col >= rows[row]) {
                        row = nextRow(row, true)
                        col = 0
                    }
                } else {
                    col--
                    if (col < 0) {
                        row = nextRow(row, false)
                        col = rows[row] - 1
                    }
                }
            }
        }
        lastMoveAt = nowMs
        lastMoveAuto = auto
        stepStart = nowMs
    }

    private fun nextRow(from: Int, forward: Boolean): Int {
        var r = from
        repeat(rows.size) {
            r = if (forward) (r + 1) % rows.size else (r - 1 + rows.size) % rows.size
            if (rows[r] > 0) return r
        }
        return from
    }
}
