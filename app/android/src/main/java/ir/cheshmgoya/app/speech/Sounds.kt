package ir.cheshmgoya.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/** Short feedback beeps and the emergency siren. */
class Sounds(private val context: Context) {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
    private var alarm: AudioTrack? = null
    private var savedAlarmVolume: Int? = null

    /** "Ready": the eye has been closed long enough — opening it now selects. */
    fun ready() { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }

    /** Selection confirmed. */
    fun selected() { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }

    /** Attention beep used when speech isn't available. */
    fun attention() { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600) }

    /**
     * Loud two-tone siren on the ALARM stream so it is heard even when the
     * tablet is on silent. Raises the alarm volume to maximum while it plays.
     */
    fun startAlarm() {
        if (alarm != null) return
        val am = context.getSystemService(AudioManager::class.java)
        try {
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot raise alarm volume (Do Not Disturb?)", e)
        }
        val rate = 22050
        val seconds = 1.2
        val n = (rate * seconds).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val f = if (t < seconds / 2) 880.0 else 660.0
            pcm[i] = (sin(2 * PI * f * t) * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        track.write(pcm, 0, n)
        track.setLoopPoints(0, n, -1)
        track.play()
        alarm = track
    }

    fun stopAlarm() {
        alarm?.let {
            runCatching { it.stop() }
            it.release()
        }
        alarm = null
        savedAlarmVolume?.let { v ->
            runCatching { context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
        }
        savedAlarmVolume = null
    }

    fun release() {
        stopAlarm()
        tone?.release()
    }

    private companion object { const val TAG = "Sounds" }
}
