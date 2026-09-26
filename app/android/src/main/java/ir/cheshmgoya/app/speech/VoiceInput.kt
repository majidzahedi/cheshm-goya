package ir.cheshmgoya.app.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ir.cheshmgoya.app.net.ServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Captures the companion's spoken question. Uses the local server's Whisper when
 * available (audio goes only to our own computer), otherwise Android's
 * SpeechRecognizer. Returns null if nothing was understood.
 */
class VoiceInput(private val context: Context) {

    @Volatile private var stopRequested = false

    /** Stop the current server recording early (tap the mic again). */
    fun stop() { stopRequested = true }

    suspend fun listen(server: ServerClient?, maxMs: Long = 8000): String? {
        if (server != null) {
            val wav = recordWav(maxMs)
            val text = wav?.let { runCatching { server.stt(it) }.getOrNull() }
            if (!text.isNullOrBlank()) return text
        }
        return recognizeOnDevice()
    }

    @SuppressLint("MissingPermission") // checked by the caller before listening
    private suspend fun recordWav(maxMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext null
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4)
        } catch (e: Exception) {
            return@withContext null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext null }
        val pcm = ByteArrayOutputStream()
        val buf = ByteArray(minBuf)
        stopRequested = false
        val start = System.currentTimeMillis()
        rec.startRecording()
        try {
            while (isActive && !stopRequested && System.currentTimeMillis() - start < maxMs) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) pcm.write(buf, 0, n)
            }
        } finally {
            rec.stop()
            rec.release()
        }
        wav(pcm.toByteArray(), rate)
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    private suspend fun recognizeOnDevice(): String? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return@withContext null
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            fun finish(v: String?) {
                sr.destroy()
                if (cont.isActive) cont.resume(v)
            }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) =
                    finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                override fun onError(error: Int) = finish(null)
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            sr.startListening(intent)
            cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { sr.destroy() } }
        }
    }
}
