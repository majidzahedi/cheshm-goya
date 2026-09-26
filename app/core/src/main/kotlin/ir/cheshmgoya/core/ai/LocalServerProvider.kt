package ir.cheshmgoya.core.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Wire format shared with server/app/schemas.py. */
object ServerApi {
    @Serializable data class CompleteRequest(val typed: String, val recent: List<String>, val hour: Int, val n: Int = 3)
    @Serializable data class CompleteResponse(val suggestions: List<String>)
    @Serializable data class RankRequest(val context: AiContext, val phrases: List<String>)
    @Serializable data class ClassifyRequest(val text: String)
    @Serializable data class SentenceItem(val text: String, val ts: Long)
    @Serializable data class SyncRequest(val sentences: List<SentenceItem>)
    @Serializable data class SyncResponse(val stored: Int)
    @Serializable data class Health(val status: String, val version: String = "", val models: Map<String, Boolean> = emptyMap())

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

/**
 * Our own GPU server on the local network (see server/). Data only goes to
 * that computer. Authenticated with the shared token from pairing.
 */
class LocalServerProvider(
    baseUrl: String,
    private val token: String,
    private val http: HttpTransport,
) : AiProvider {
    override val id = ProviderId.LOCAL_SERVER
    private val base = baseUrl.trimEnd('/')
    private val json = ServerApi.json
    private val headers get() = mapOf("Authorization" to "Bearer $token")

    private suspend fun post(path: String, body: String, timeoutMs: Long): String {
        val r = http.postJson("$base$path", headers, body, timeoutMs)
        if (r.code !in 200..299) throw HttpException(r.code, "server $path → ${r.code}")
        return r.body
    }

    override suspend fun complete(context: AiContext): List<String> {
        val body = json.encodeToString(ServerApi.CompleteRequest(context.typed, context.recent, context.hour))
        return json.decodeFromString<ServerApi.CompleteResponse>(post("/complete", body, 3000)).suggestions
    }

    override suspend fun rank(context: AiContext, phrases: List<String>): RankResult {
        val body = json.encodeToString(ServerApi.RankRequest(context, phrases))
        return json.decodeFromString(post("/rank", body, 1000))
    }

    override suspend fun classify(question: String): QuestionClass =
        json.decodeFromString(post("/classify", json.encodeToString(ServerApi.ClassifyRequest(question)), 3000))

    override suspend fun ping(): Boolean {
        val r = http.get("$base/health", headers, 1500)
        return r.code == 200 && json.decodeFromString<ServerApi.Health>(r.body).status == "ok"
    }

    /** Send spoken sentences for personalisation (only when the family allowed it). */
    suspend fun syncSentences(items: List<ServerApi.SentenceItem>): Int {
        if (items.isEmpty()) return 0
        val body = json.encodeToString(ServerApi.SyncRequest(items))
        return json.decodeFromString<ServerApi.SyncResponse>(post("/personalize/sentences", body, 5000)).stored
    }
}
