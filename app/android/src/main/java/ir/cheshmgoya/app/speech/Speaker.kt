package ir.cheshmgoya.app.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import ir.cheshmgoya.app.net.ServerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class TtsState { INITIALIZING, READY, PERSIAN_MISSING, UNAVAILABLE }

/**
 * Speaks the patient's messages. Order of preference:
 *  1. The local server's natural (or personal) voice, if enabled and reachable.
 *  2. Android TextToSpeech in Persian (fa-IR).
 *  3. No Persian voice: the UI shows the message in huge letters and we beep.
 */
class Speaker(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state

    private val persian = Locale("fa", "IR")
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var engineReady = false
    private var player: MediaPlayer? = null

    /** Set by the app container according to settings. */
    @Volatile var server: ServerClient? = null
    @Volatile var serverVoice: String = "default"

    /** Called when nothing could speak, so the UI can flash the text and beep. */
    var onCannotSpeak: ((String) -> Unit)? = null

    /** Label of the TTS engine that speaks Persian, for the settings screen. */
    private val _engineLabel = MutableStateFlow<String?>(null)
    val engineLabel: StateFlow<String?> = _engineLabel

    init {
        findPersianEngine()
    }

    /**
     * Google's TTS has no Persian voice, so the system default engine often can't
     * speak Persian. Try the default engine first, then every other installed engine
     * (e.g. a sherpa-onnx / Piper Persian engine) until one supports Persian.
     */
    private fun findPersianEngine() {
        engineReady = false
        _state.value = TtsState.INITIALIZING
        tryEngine(null, remaining = null)
    }

    private fun tryEngine(engine: String?, remaining: ArrayDeque<String>?) {
        var created: TextToSpeech? = null
        // Engines that fail to bind call back synchronously inside the constructor, before
        // `created` is assigned, so handle the result on the next main-loop turn.
        val listener = TextToSpeech.OnInitListener { status -> main.post { onEngineInit(status, created, engine, remaining) } }
        created = if (engine == null) TextToSpeech(context.applicationContext, listener)
        else TextToSpeech(context.applicationContext, listener, engine)
    }

    private fun onEngineInit(status: Int, created: TextToSpeech?, engine: String?, remaining: ArrayDeque<String>?) {
        val t = created ?: return
        val queue = remaining ?: ArrayDeque(
            t.engines.map { it.name }.filter { it != t.defaultEngine && it != engine }
        )
        if (status == TextToSpeech.SUCCESS && selectPersian(t)) {
            tts?.takeIf { it !== t }?.shutdown()
            tts = t
            engineReady = true
            t.setSpeechRate(0.9f)
            _engineLabel.value = t.engines.firstOrNull { it.name == (engine ?: t.defaultEngine) }?.label
            _state.value = TtsState.READY
            return
        }
        if (queue.isNotEmpty()) {
            t.shutdown()
            tryEngine(queue.removeFirst(), queue)
        } else {
            // Keep one engine alive so we can re-check after the family installs a voice.
            tts?.takeIf { it !== t }?.shutdown()
            tts = t
            engineReady = status == TextToSpeech.SUCCESS
            _engineLabel.value = null
            _state.value = if (status == TextToSpeech.SUCCESS) TtsState.PERSIAN_MISSING else TtsState.UNAVAILABLE
        }
    }

    private fun selectPersian(t: TextToSpeech): Boolean = listOf(persian, Locale("fa")).any { loc ->
        val r = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String) {
        val srv = server
        if (srv != null) {
            scope.launch {
                val wav = runCatching { srv.tts(text, serverVoice) }.getOrNull()
                if (wav != null && playWav(wav)) return@launch
                withContext(Dispatchers.Main) { speakLocal(text, flush = true) }
            }
        } else {
            speakLocal(text, flush = true)
        }
    }

    /** Quiet read-out of the highlighted option for patients with low vision. Local TTS only (low latency). */
    fun preview(label: String) {
        if (_state.value == TtsState.READY) tts?.speak(label, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    private fun speakLocal(text: String, flush: Boolean) {
        if (_state.value == TtsState.READY) {
            tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
        } else {
            onCannotSpeak?.invoke(text)
        }
    }

    private suspend fun playWav(bytes: ByteArray): Boolean = withContext(Dispatchers.Main) {
        try {
            val f = File(context.cacheDir, "tts.wav")
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                setDataSource(f.absolutePath)
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("Speaker", "server audio failed", e)
            false
        }
    }

    fun stop() {
        tts?.stop()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun ttsSettingsIntent(): Intent =
        Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Re-check after the family installed a Persian engine (called from onResume). */
    fun recheck() {
        if (_state.value == TtsState.READY || _state.value == TtsState.INITIALIZING) return
        tts?.shutdown()
        tts = null
        findPersianEngine()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
