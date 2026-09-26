package ir.cheshmgoya.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.blink.EyeSelection
import ir.cheshmgoya.core.keyboard.KeyboardLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class InputMode(val titleFa: String, val usesCamera: Boolean) {
    SCAN_BLINK("اسکن خودکار + پلک", true),
    GAZE_BLINK("نگاه به چپ و راست + پلک", true),
    SWITCH_SCAN("اسکن خودکار + کلید", false),
    SWITCH_MANUAL("فقط کلید (حرکت و انتخاب)", false),
}

data class AppSettings(
    val inputMode: InputMode = InputMode.SCAN_BLINK,
    val scanStepMs: Long = 1800,
    val blinkMinMs: Long = 450,
    val blinkMaxMs: Long = 2000,
    val eyes: EyeSelection = EyeSelection.BOTH,
    /** Swap MediaPipe's left/right labels if they don't match the patient's eyes. */
    val swapEyes: Boolean = false,
    val smoothing: Float = 0.6f,
    val closeThreshold: Float = 0.5f,
    val openThreshold: Float = 0.35f,
    val gazeCenter: Float = 0f,
    val calibrated: Boolean = false,
    val gazeHoldMs: Long = 650,
    val gazeInverted: Boolean = false,
    val readAloud: Boolean = false,
    val goHomeAfterSpeak: Boolean = true,
    val keyboardLayout: KeyboardLayout = KeyboardLayout.FREQUENCY,
    val fontScale: Float = 1f,
    val highContrast: Boolean = true,
    val darkTheme: Boolean = true,
    val emergencyPhone: String = "",
    val emergencySms: Boolean = false,
    val lockTask: Boolean = false,
    val aiProvider: ProviderId = ProviderId.OFF,
    val serverUrl: String = "",
    val serverToken: String = "",
    val serverId: String = "",
    val serverTts: Boolean = false,
    val serverVoice: String = "default",
    val syncSentences: Boolean = false,
    val jevApiKey: String = "",
    val claudeApiKey: String = "",
    val claudeModel: String = "claude-opus-5",
    val rankThreshold: Float = 0.35f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val inputMode = stringPreferencesKey("inputMode")
        val scanStepMs = longPreferencesKey("scanStepMs")
        val blinkMinMs = longPreferencesKey("blinkMinMs")
        val blinkMaxMs = longPreferencesKey("blinkMaxMs")
        val eyes = stringPreferencesKey("eyes")
        val swapEyes = booleanPreferencesKey("swapEyes")
        val smoothing = floatPreferencesKey("smoothing")
        val closeThreshold = floatPreferencesKey("closeThreshold")
        val openThreshold = floatPreferencesKey("openThreshold")
        val gazeCenter = floatPreferencesKey("gazeCenter")
        val calibrated = booleanPreferencesKey("calibrated")
        val gazeHoldMs = longPreferencesKey("gazeHoldMs")
        val gazeInverted = booleanPreferencesKey("gazeInverted")
        val readAloud = booleanPreferencesKey("readAloud")
        val goHome = booleanPreferencesKey("goHomeAfterSpeak")
        val keyboardLayout = stringPreferencesKey("keyboardLayout")
        val fontScale = floatPreferencesKey("fontScale")
        val highContrast = booleanPreferencesKey("highContrast")
        val darkTheme = booleanPreferencesKey("darkTheme")
        val emergencyPhone = stringPreferencesKey("emergencyPhone")
        val emergencySms = booleanPreferencesKey("emergencySms")
        val lockTask = booleanPreferencesKey("lockTask")
        val aiProvider = stringPreferencesKey("aiProvider")
        val serverUrl = stringPreferencesKey("serverUrl")
        val serverToken = stringPreferencesKey("serverToken")
        val serverId = stringPreferencesKey("serverId")
        val serverTts = booleanPreferencesKey("serverTts")
        val serverVoice = stringPreferencesKey("serverVoice")
        val syncSentences = booleanPreferencesKey("syncSentences")
        val jevApiKey = stringPreferencesKey("jevApiKey")
        val claudeApiKey = stringPreferencesKey("claudeApiKey")
        val claudeModel = stringPreferencesKey("claudeModel")
        val rankThreshold = floatPreferencesKey("rankThreshold")
    }

    private inline fun <reified E : Enum<E>> enumOf(v: String?, default: E): E =
        v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    val settings: Flow<AppSettings> = context.dataStore.data.map { currentFrom(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = currentFrom(p)
            val s = transform(cur)
            p[K.inputMode] = s.inputMode.name
            p[K.scanStepMs] = s.scanStepMs
            p[K.blinkMinMs] = s.blinkMinMs
            p[K.blinkMaxMs] = s.blinkMaxMs
            p[K.eyes] = s.eyes.name
            p[K.swapEyes] = s.swapEyes
            p[K.smoothing] = s.smoothing
            p[K.closeThreshold] = s.closeThreshold
            p[K.openThreshold] = s.openThreshold
            p[K.gazeCenter] = s.gazeCenter
            p[K.calibrated] = s.calibrated
            p[K.gazeHoldMs] = s.gazeHoldMs
            p[K.gazeInverted] = s.gazeInverted
            p[K.readAloud] = s.readAloud
            p[K.goHome] = s.goHomeAfterSpeak
            p[K.keyboardLayout] = s.keyboardLayout.name
            p[K.fontScale] = s.fontScale
            p[K.highContrast] = s.highContrast
            p[K.darkTheme] = s.darkTheme
            p[K.emergencyPhone] = s.emergencyPhone
            p[K.emergencySms] = s.emergencySms
            p[K.lockTask] = s.lockTask
            p[K.aiProvider] = s.aiProvider.name
            p[K.serverUrl] = s.serverUrl
            p[K.serverToken] = s.serverToken
            p[K.serverId] = s.serverId
            p[K.serverTts] = s.serverTts
            p[K.serverVoice] = s.serverVoice
            p[K.syncSentences] = s.syncSentences
            p[K.jevApiKey] = s.jevApiKey
            p[K.claudeApiKey] = s.claudeApiKey
            p[K.claudeModel] = s.claudeModel
            p[K.rankThreshold] = s.rankThreshold
        }
    }

    private fun currentFrom(p: Preferences): AppSettings {
        val d = AppSettings()
        return d.copy(
            inputMode = enumOf(p[K.inputMode], d.inputMode),
            scanStepMs = p[K.scanStepMs] ?: d.scanStepMs,
            blinkMinMs = p[K.blinkMinMs] ?: d.blinkMinMs,
            blinkMaxMs = p[K.blinkMaxMs] ?: d.blinkMaxMs,
            eyes = enumOf(p[K.eyes], d.eyes),
            swapEyes = p[K.swapEyes] ?: d.swapEyes,
            smoothing = p[K.smoothing] ?: d.smoothing,
            closeThreshold = p[K.closeThreshold] ?: d.closeThreshold,
            openThreshold = p[K.openThreshold] ?: d.openThreshold,
            gazeCenter = p[K.gazeCenter] ?: d.gazeCenter,
            calibrated = p[K.calibrated] ?: d.calibrated,
            gazeHoldMs = p[K.gazeHoldMs] ?: d.gazeHoldMs,
            gazeInverted = p[K.gazeInverted] ?: d.gazeInverted,
            readAloud = p[K.readAloud] ?: d.readAloud,
            goHomeAfterSpeak = p[K.goHome] ?: d.goHomeAfterSpeak,
            keyboardLayout = enumOf(p[K.keyboardLayout], d.keyboardLayout),
            fontScale = p[K.fontScale] ?: d.fontScale,
            highContrast = p[K.highContrast] ?: d.highContrast,
            darkTheme = p[K.darkTheme] ?: d.darkTheme,
            emergencyPhone = p[K.emergencyPhone] ?: d.emergencyPhone,
            emergencySms = p[K.emergencySms] ?: d.emergencySms,
            lockTask = p[K.lockTask] ?: d.lockTask,
            aiProvider = enumOf(p[K.aiProvider], d.aiProvider),
            serverUrl = p[K.serverUrl] ?: d.serverUrl,
            serverToken = p[K.serverToken] ?: d.serverToken,
            serverId = p[K.serverId] ?: d.serverId,
            serverTts = p[K.serverTts] ?: d.serverTts,
            serverVoice = p[K.serverVoice] ?: d.serverVoice,
            syncSentences = p[K.syncSentences] ?: d.syncSentences,
            jevApiKey = p[K.jevApiKey] ?: d.jevApiKey,
            claudeApiKey = p[K.claudeApiKey] ?: d.claudeApiKey,
            claudeModel = p[K.claudeModel] ?: d.claudeModel,
            rankThreshold = p[K.rankThreshold] ?: d.rankThreshold,
        )
    }
}
