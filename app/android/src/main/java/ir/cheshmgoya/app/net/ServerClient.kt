package ir.cheshmgoya.app.net

import ir.cheshmgoya.core.ai.HttpException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Audio endpoints of our local server (the text endpoints live in core's LocalServerProvider). */
class ServerClient(baseUrl: String, private val token: String, private val http: OkHttpTransport) {
    private val base = baseUrl.trimEnd('/')
    private val headers get() = mapOf("Authorization" to "Bearer $token")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class TtsRequest(val text: String, val voice: String)
    @Serializable private data class SttResponse(val text: String)
    @Serializable data class Voice(val id: String, val name: String, val personal: Boolean = false)
    @Serializable private data class Voices(val voices: List<Voice>)

    /** Speech → text with Whisper on the server. */
    suspend fun stt(wav: ByteArray): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", "question.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("language", "fa")
            .build()
        val (code, bytes) = http.postBody("$base/stt", headers, body, 15_000)
        if (code != 200) throw HttpException(code, "stt $code")
        return json.decodeFromString<SttResponse>(bytes.decodeToString()).text
    }

    /** Text → WAV audio in the chosen server voice. */
    suspend fun tts(text: String, voice: String): ByteArray {
        val body = json.encodeToString(TtsRequest(text, voice)).toRequestBody("application/json".toMediaType())
        val (code, bytes) = http.postBody("$base/tts", headers, body, 3000)
        if (code != 200) throw HttpException(code, "tts $code")
        return bytes
    }

    suspend fun voices(): List<Voice> {
        val r = http.get("$base/voices", headers, 3000)
        if (r.code != 200) throw HttpException(r.code, "voices ${r.code}")
        return json.decodeFromString<Voices>(r.body).voices
    }
}
