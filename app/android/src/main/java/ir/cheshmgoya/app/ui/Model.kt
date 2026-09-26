package ir.cheshmgoya.app.ui

import ir.cheshmgoya.app.data.AppSettings
import ir.cheshmgoya.app.speech.TtsState
import ir.cheshmgoya.core.ai.ConnectionStatus
import ir.cheshmgoya.core.keyboard.Key
import ir.cheshmgoya.core.phrases.CategoryId
import ir.cheshmgoya.core.scan.Highlight

sealed interface Screen {
    /** Patient screens are scanned; companion screens are operated by touch. */
    val forPatient: Boolean get() = true

    data object Home : Screen
    data class Category(val id: CategoryId) : Screen
    data object PainScale : Screen
    data object YesNo : Screen
    data object Keyboard : Screen
    data object Rest : Screen
    data object Emergency : Screen

    data object Settings : Screen { override val forPatient = false }
    data object History : Screen { override val forPatient = false }
    data object MyPhrasesEditor : Screen { override val forPatient = false }
    data object Calibration : Screen { override val forPatient = false }
    data object Debug : Screen { override val forPatient = false }
    data object Pairing : Screen { override val forPatient = false }
    data object VoiceHelp : Screen { override val forPatient = false }
}

enum class CellStyle { NORMAL, YES, NO, EMERGENCY, NAV, CONTROL, SUGGESTION, LETTER, EMPTY }

sealed interface CellAction {
    data class Say(val text: String) : CellAction
    data class Go(val screen: Screen) : CellAction
    data object GoHome : CellAction
    data object Emergency : CellAction
    data object Rest : CellAction
    data class Type(val key: Key) : CellAction
    data class AcceptWord(val word: String) : CellAction
    data class PainLevel(val level: Int) : CellAction
    data object None : CellAction
}

data class Cell(val label: String, val action: CellAction, val style: CellStyle = CellStyle.NORMAL)

/** A screen's selectable options, row by row. Rows may be empty (skipped by scanning). */
data class Grid(val rows: List<List<Cell>>) {
    val rowSizes: List<Int> get() = rows.map { it.size }

    companion object { val EMPTY = Grid(emptyList()) }
}

enum class CalibrationPhase { IDLE, PREPARE_OPEN, OPEN, PREPARE_CLOSED, CLOSED, DONE, FAILED }

data class CalibrationState(val phase: CalibrationPhase = CalibrationPhase.IDLE, val message: String = "", val secondsLeft: Int = 0)

data class DebugData(
    val signal: List<Float> = emptyList(),
    val gaze: List<Float> = emptyList(),
    val left: Float = 0f,
    val right: Float = 0f,
    val closeThreshold: Float = 0.5f,
    val openThreshold: Float = 0.35f,
    val face: Boolean = false,
    val fps: Float = 0f,
)

data class UiState(
    val screen: Screen = Screen.Home,
    val grid: Grid = Grid.EMPTY,
    val highlight: Highlight? = null,
    /** 0..1 fill on the highlighted option while the eye is closed. */
    val blinkProgress: Float = 0f,
    val composing: String = "",
    /** Last message the patient said, shown large for a few seconds. */
    val lastSpoken: String? = null,
    /** Shown in huge letters when no voice is available. */
    val bigMessage: String? = null,
    /** The companion's question, shown on top while the patient answers. */
    val question: String? = null,
    val faceVisible: Boolean = true,
    val sleeping: Boolean = false,
    val cameraError: String? = null,
    val connection: ConnectionStatus = ConnectionStatus.OFFLINE_MODE,
    val tts: TtsState = TtsState.INITIALIZING,
    val restBlinks: Int = 0,
    val listening: Boolean = false,
    val notice: String? = null,
    val calibration: CalibrationState = CalibrationState(),
    val settings: AppSettings = AppSettings(),
)
