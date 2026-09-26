package ir.cheshmgoya.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * TypeSafe AI's Jev decision model, used only for ranking and question
 * classification (it returns calibrated probabilities, not text).
 * Internet service: messages leave the device.
 */
class JevProvider(
    private val apiKey: String,
    private val http: HttpTransport,
    /** Pinned so behaviour does not change under us; `jev-latest` is only an alias. */
    private val model: String = PINNED_MODEL,
    private val endpoint: String = ENDPOINT,
) : AiProvider {
    override val id = ProviderId.JEV

    companion object {
        const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"
        const val PINNED_MODEL = "jev-1.13.0"
        private val json = Json { ignoreUnknownKeys = true }

        fun describeState(context: AiContext): String = buildString {
            append("A patient who cannot speak communicates with an eye-blink AAC tablet in Persian. ")
            append("Local hour: ${context.hour}. ")
            if (context.recent.isNotEmpty()) append("Recent messages from the patient (oldest first): ${context.recent.joinToString(" | ")}. ")
            if (context.typed.isNotBlank()) append("Currently typed text: ${context.typed}")
        }

        fun rankBody(model: String, context: AiContext, phrases: List<String>): String = buildJsonObject {
            put("model", model)
            put("state", describeState(context))
            putJsonObject("questions") {
                putJsonObject("next_phrase") {
                    put("type", "choice")
                    put("instructions", "Which phrase is the patient most likely to want to say right now?")
                    putJsonObject("criteria") { phrases.forEachIndexed { i, p -> put("p$i", p) } }
                }
            }
        }.toString()

        fun parseRank(body: String, phrases: List<String>): RankResult? {
            val ans = answers(body)?.get("next_phrase")?.jsonObject ?: return null
            val probs = ans["probabilities"]?.jsonObject ?: return null
            val ranked = phrases.mapIndexed { i, p -> RankedPhrase(p, probs["p$i"]?.jsonPrimitive?.doubleOrNull ?: 0.0) }
                .sortedByDescending { it.probability }
            val conf = ans["confidence"]?.jsonPrimitive?.doubleOrNull ?: ranked.firstOrNull()?.probability ?: 0.0
            return RankResult(ranked, conf)
        }

        val topics = linkedMapOf(
            "needs" to "food, water, toilet, position, temperature, sleep or other physical needs",
            "pain" to "pain or a body symptom",
            "feelings" to "emotions or mood",
            "people" to "people, visitors, calling someone or requests to others",
            "none" to "none of these",
        )

        fun classifyBody(model: String, question: String): String = buildJsonObject {
            put("model", model)
            put("state", "A caregiver asked a patient (who can only answer by blinking) this question in Persian: «$question»")
            putJsonObject("questions") {
                putJsonObject("is_yes_no") {
                    put("type", "noul")
                    put("instructions", "The question can be answered with yes or no.")
                }
                putJsonObject("topic") {
                    put("type", "choice")
                    put("instructions", "What is the question about?")
                    putJsonObject("criteria") { topics.forEach { (k, v) -> put(k, v) } }
                }
            }
        }.toString()

        fun parseClassify(body: String): QuestionClass? {
            val a = answers(body) ?: return null
            val yn = a["is_yes_no"]?.jsonObject?.let { noulProbability(it) } ?: return null
            val topicObj = a["topic"]?.jsonObject
            val topic = topicObj?.get("choice")?.jsonPrimitive?.content?.takeIf { it != "none" }
            val topicConf = topicObj?.get("confidence")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val type = if (yn >= 0.5) "yes_no" else if (topic != null) "choice" else "open"
            return QuestionClass(type, topic, if (yn >= 0.5) yn else maxOf(1 - yn, topicConf))
        }

        /** Noul answers carry P(yes); accept the documented field and a couple of likely spellings. */
        private fun noulProbability(o: JsonObject): Double? =
            listOf("probability", "p_yes", "yes", "value").firstNotNullOfOrNull { o[it]?.jsonPrimitive?.doubleOrNull }

        private fun answers(body: String): JsonObject? =
            runCatching { json.parseToJsonElement(body).jsonObject["answers"]?.jsonObject }.getOrNull()
    }

    private val headers get() = mapOf("Authorization" to "Bearer $apiKey")

    override suspend fun complete(context: AiContext): List<String>? = null // Jev does not generate text.

    override suspend fun rank(context: AiContext, phrases: List<String>): RankResult? {
        val r = http.postJson(endpoint, headers, rankBody(model, context, phrases), 1000)
        if (r.code !in 200..299) throw HttpException(r.code, "jev ${r.code}")
        return parseRank(r.body, phrases)
    }

    override suspend fun classify(question: String): QuestionClass? {
        val r = http.postJson(endpoint, headers, classifyBody(model, question), 3000)
        if (r.code !in 200..299) throw HttpException(r.code, "jev ${r.code}")
        return parseClassify(r.body)
    }

    override suspend fun ping(): Boolean = apiKey.isNotBlank()
}
