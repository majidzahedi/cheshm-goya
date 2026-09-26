package ir.cheshmgoya.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigDisabled
import ir.cheshmgoya.core.ai.AiContext
import ir.cheshmgoya.core.ai.AiProvider
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.ai.QuestionClass
import ir.cheshmgoya.core.ai.RankResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

/**
 * Claude API, used to generate candidate sentences. Internet service:
 * messages leave the device. Ranking and classification are left to the
 * other providers (return null → offline fallback).
 */
class ClaudeProvider(
    private val client: AnthropicClient,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    constructor(apiKey: String, model: String = DEFAULT_MODEL) : this(
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(3))
            .maxRetries(0) // the coordinator's 3 s budget has no room for retries; offline fallback instead
            .build(),
        model,
    )

    override val id = ProviderId.CLAUDE

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"

        val SYSTEM_PROMPT = """
            You help a patient who cannot speak or move, and communicates in Persian by blinking at a tablet.
            Every word costs the patient a lot of effort, so suggest what they most likely want to say.
            Write exactly three short Persian sentences (at most eight words each), colloquial (محاوره‌ای),
            in the first person, as the patient speaking to family or nurses. If the patient has typed
            something, each sentence must start with or naturally complete it. Use proper Persian
            half-spaces (می‌خوام). No explanations.
        """.trimIndent()

        /** JSON schema for `{"sentences": [string, string, string]}`. */
        private val schema: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(mapOf("sentences" to mapOf("type" to "array", "items" to mapOf("type" to "string")))),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("sentences")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()

        fun userPrompt(context: AiContext): String = buildString {
            append("ساعت: ${context.hour}\n")
            if (context.recent.isNotEmpty()) append("پیام‌های اخیر بیمار (قدیمی به جدید):\n" + context.recent.joinToString("\n") { "- $it" } + "\n")
            append("متن تایپ‌شده: «${context.typed}»")
        }

        fun buildParams(model: String, context: AiContext): MessageCreateParams = MessageCreateParams.builder()
            .model(model)
            .maxTokens(400L)
            .system(SYSTEM_PROMPT)
            // Latency matters more than depth here: a 3-second budget per request.
            .thinking(ThinkingConfigDisabled.builder().build())
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(schema).build())
                    .build()
            )
            .addUserMessage(userPrompt(context))
            .build()

        fun parseSentences(text: String): List<String> =
            Json.parseToJsonElement(text).jsonObject["sentences"]!!.jsonArray
                .map { it.jsonPrimitive.content.trim() }
                .filter { it.isNotEmpty() }
                .take(3)
    }

    override suspend fun complete(context: AiContext): List<String>? = withContext(Dispatchers.IO) {
        val response = client.messages().create(buildParams(model, context))
        if (response.stopReason().map { it.toString() }.orElse("") == "refusal") return@withContext null
        val text = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
        parseSentences(text).takeIf { it.isNotEmpty() }
    }

    override suspend fun rank(context: AiContext, phrases: List<String>): RankResult? = null

    override suspend fun classify(question: String): QuestionClass? = null

    override suspend fun ping(): Boolean = true
}
