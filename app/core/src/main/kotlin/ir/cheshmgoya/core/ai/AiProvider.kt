package ir.cheshmgoya.core.ai

import kotlinx.serialization.Serializable

enum class ProviderId(val titleFa: String) {
    OFF("خاموش"),
    OFFLINE("پیش‌بینی آفلاین"),
    LOCAL_SERVER("سرور محلی (کامپیوتر خودمان)"),
    JEV("Jev (اینترنتی، فقط رتبه‌بندی)"),
    CLAUDE("Claude (اینترنتی)"),
}

/** Context sent with every AI request. */
@Serializable
data class AiContext(
    /** What the patient has typed so far (may be empty). */
    val typed: String,
    /** Up to five most recent messages the patient said, oldest first. */
    val recent: List<String>,
    /** Local hour of day 0–23. */
    val hour: Int,
)

@Serializable
data class RankedPhrase(val text: String, val probability: Double)

@Serializable
data class RankResult(val ranked: List<RankedPhrase>, val confidence: Double)

/** Kind of question the companion asked, so the app can open the right screen. */
@Serializable
data class QuestionClass(
    /** "yes_no", "choice" or "open". */
    val type: String,
    /** Category screen to open: needs, pain, feelings, people — or null. */
    val topic: String? = null,
    val confidence: Double = 0.0,
)

/**
 * One source of "smart" suggestions. Every method may return null to mean
 * "I can't do this" — the coordinator then falls back to the offline model.
 * Implementations may throw; the coordinator treats that as null too.
 */
interface AiProvider {
    val id: ProviderId

    /** Three short, colloquial, first-person sentence completions. */
    suspend fun complete(context: AiContext): List<String>?

    /** Rank [phrases] by how likely the patient wants to say them now. */
    suspend fun rank(context: AiContext, phrases: List<String>): RankResult?

    /** Understand a transcribed question from the companion. */
    suspend fun classify(question: String): QuestionClass?

    /** Cheap reachability check for the status indicator. */
    suspend fun ping(): Boolean
}

/** Minimal HTTP abstraction so providers stay pure Kotlin and testable. */
interface HttpTransport {
    data class Response(val code: Int, val body: String)

    suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): Response
    suspend fun get(url: String, headers: Map<String, String>, timeoutMs: Long): Response
}

class HttpException(val code: Int, message: String) : Exception(message)
