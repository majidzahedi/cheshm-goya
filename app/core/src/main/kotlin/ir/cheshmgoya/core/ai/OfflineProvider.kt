package ir.cheshmgoya.core.ai

import ir.cheshmgoya.core.predict.LanguageModel

/** Always-available provider backed by the on-device [LanguageModel]. */
class OfflineProvider(
    private val model: LanguageModel,
    private val classifier: QuestionClassifier = QuestionClassifier(),
) : AiProvider {
    override val id = ProviderId.OFFLINE

    override suspend fun complete(context: AiContext): List<String> = model.suggestSentences(context.typed, 3)

    /**
     * Offline ranking: phrases the patient has said recently and often score higher.
     * Confidence is deliberately modest so offline ranking only fills the
     * suggestion row when there is a clear favourite.
     */
    override suspend fun rank(context: AiContext, phrases: List<String>): RankResult {
        val preferred = model.suggestSentences(context.typed, phrases.size.coerceAtLeast(3))
        val scores = phrases.map { p ->
            val idx = preferred.indexOf(p)
            if (idx >= 0) 1.0 / (idx + 2) else 0.05
        }
        val total = scores.sum().takeIf { it > 0 } ?: 1.0
        val ranked = phrases.zip(scores).map { RankedPhrase(it.first, it.second / total) }.sortedByDescending { it.probability }
        return RankResult(ranked, ranked.firstOrNull()?.probability ?: 0.0)
    }

    override suspend fun classify(question: String): QuestionClass = classifier.classify(question)

    override suspend fun ping() = true
}
