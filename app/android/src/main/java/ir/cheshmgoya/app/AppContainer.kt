package ir.cheshmgoya.app

import android.content.Context
import ir.cheshmgoya.app.data.AppDatabase
import ir.cheshmgoya.app.data.AppSettings
import ir.cheshmgoya.app.data.SettingsRepository
import ir.cheshmgoya.app.net.OkHttpTransport
import ir.cheshmgoya.app.net.ServerClient
import ir.cheshmgoya.app.net.ServerDiscovery
import ir.cheshmgoya.app.speech.Sounds
import ir.cheshmgoya.app.speech.Speaker
import ir.cheshmgoya.app.speech.VoiceInput
import ir.cheshmgoya.app.tracking.EyeTracker
import ir.cheshmgoya.claude.ClaudeProvider
import ir.cheshmgoya.core.ai.AiCoordinator
import ir.cheshmgoya.core.ai.AiProvider
import ir.cheshmgoya.core.ai.JevProvider
import ir.cheshmgoya.core.ai.LocalServerProvider
import ir.cheshmgoya.core.ai.OfflineProvider
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.phrases.PhraseBank
import ir.cheshmgoya.core.predict.LanguageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container, created once per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(appContext)
    val db = AppDatabase.create(appContext)
    val http = OkHttpTransport()

    val languageModel = LanguageModel().apply {
        seedDefaultLexicon()
        PhraseBank.all.forEach { seedSentence(it, 0.5) }
    }
    val offline = OfflineProvider(languageModel)
    val ai = AiCoordinator(offline)

    val eyeTracker = EyeTracker(appContext)
    val sounds = Sounds(appContext)
    val speaker = Speaker(appContext, scope)
    val voiceInput = VoiceInput(appContext)
    val discovery = ServerDiscovery(appContext)

    /** Audio client for the local server, or null when it isn't the selected provider. */
    @Volatile var serverClient: ServerClient? = null
        private set

    /** Rebuild AI providers after settings change. Everything stays offline-safe. */
    private var providerKey: String? = null

    fun applyAiSettings(s: AppSettings) {
        val key = listOf(s.aiProvider, s.serverUrl, s.serverToken, s.jevApiKey, s.claudeApiKey, s.claudeModel).joinToString("|")
        if (key != providerKey) {
            providerKey = key
            ai.provider = buildProvider(s)
        }
        ai.rankConfidenceThreshold = s.rankThreshold.toDouble()

        serverClient = if (s.aiProvider == ProviderId.LOCAL_SERVER && s.serverUrl.isNotBlank()) ServerClient(s.serverUrl, s.serverToken, http) else null
        speaker.server = if (s.serverTts) serverClient else null
        speaker.serverVoice = s.serverVoice
    }

    private fun buildProvider(s: AppSettings): AiProvider? {
        return when (s.aiProvider) {
            ProviderId.OFF -> null
            ProviderId.OFFLINE -> offline
            ProviderId.LOCAL_SERVER -> s.serverUrl.takeIf { it.isNotBlank() }?.let { LocalServerProvider(it, s.serverToken, http) }
            ProviderId.JEV -> s.jevApiKey.takeIf { it.isNotBlank() }?.let { JevProvider(it, http) }
            ProviderId.CLAUDE -> s.claudeApiKey.takeIf { it.isNotBlank() }?.let { ClaudeProvider(it, s.claudeModel) }
        }
    }
}
