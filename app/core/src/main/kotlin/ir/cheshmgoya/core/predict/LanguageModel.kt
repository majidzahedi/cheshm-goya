package ir.cheshmgoya.core.predict

import ir.cheshmgoya.core.text.PersianText
import kotlin.math.exp
import kotlin.math.ln

/** Persisted learned statistics (seed data is rebuilt on start and never stored). */
data class WordRow(val key: String, val display: String, val count: Double, val lastUsedMs: Long)
data class NgramRow(val context: String, val next: String, val count: Double, val lastUsedMs: Long)
data class SentenceRow(val key: String, val text: String, val count: Double, val lastUsedMs: Long)

/** Rows changed by one [LanguageModel.learn] call, to upsert into storage. */
data class LearnDelta(val words: List<WordRow>, val ngrams: List<NgramRow>, val sentences: List<SentenceRow>)

/**
 * Offline word and sentence prediction: prefix completion, bigram/trigram
 * next-word prediction and whole-sentence suggestions, ranked by frequency and
 * recency. Always available — it is the fallback for every AI provider.
 */
class LanguageModel(private val clock: () -> Long = System::currentTimeMillis) {

    private class Stat(var display: String, var seed: Double = 0.0, var learned: Double = 0.0, var lastUsed: Long = 0)

    private val words = HashMap<String, Stat>()
    private val ngrams = HashMap<String, HashMap<String, Stat>>() // context ("k1" or "k1 k2") -> next -> stat
    private val sentences = HashMap<String, Stat>()

    var recencyHalfLifeMs: Long = 3L * 24 * 3600 * 1000

    // ---------- seeding & persistence ----------

    fun seedWord(word: String, weight: Double) {
        val k = PersianText.key(word)
        if (k.isEmpty()) return
        words.getOrPut(k) { Stat(PersianText.normalize(word)) }.seed += weight
    }

    /** Seed a phrase-bank sentence: counted weakly for n-grams and offered as a sentence suggestion. */
    fun seedSentence(text: String, weight: Double = 1.0) {
        val norm = PersianText.normalize(text)
        val k = PersianText.key(norm)
        if (k.isEmpty()) return
        sentences.getOrPut(k) { Stat(norm) }.seed += weight
        val toks = PersianText.words(norm)
        for ((i, t) in toks.withIndex()) {
            val tk = PersianText.key(t)
            words.getOrPut(tk) { Stat(t) }.seed += weight
            if (i >= 1) ngram(PersianText.key(toks[i - 1]), tk, t).seed += weight
            if (i >= 2) ngram(PersianText.key(toks[i - 2]) + " " + PersianText.key(toks[i - 1]), tk, t).seed += weight
        }
    }

    /** Load the lexicon resource: lines of `word<TAB>weight`, `#` comments. */
    fun seedLexicon(lines: Sequence<String>) {
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split('\t')
            seedWord(parts[0], parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0)
        }
    }

    fun seedDefaultLexicon() {
        val stream = LanguageModel::class.java.getResourceAsStream("/lexicon_fa.txt") ?: return
        stream.bufferedReader(Charsets.UTF_8).useLines { seedLexicon(it) }
    }

    fun restore(wordRows: List<WordRow>, ngramRows: List<NgramRow>, sentenceRows: List<SentenceRow>) {
        for (r in wordRows) words.getOrPut(r.key) { Stat(r.display) }.apply { learned = r.count; lastUsed = r.lastUsedMs }
        for (r in ngramRows) ngrams.getOrPut(r.context) { HashMap() }.getOrPut(r.next) { Stat(r.next) }
            .apply { learned = r.count; lastUsed = r.lastUsedMs; display = words[r.next]?.display ?: display }
        for (r in sentenceRows) sentences.getOrPut(r.key) { Stat(r.text) }.apply { learned = r.count; lastUsed = r.lastUsedMs }
    }

    // ---------- learning ----------

    /** Learn from a sentence the patient actually said. */
    fun learn(text: String): LearnDelta {
        val now = clock()
        val norm = PersianText.normalize(text)
        val toks = PersianText.words(norm)
        if (toks.isEmpty()) return LearnDelta(emptyList(), emptyList(), emptyList())
        val w = ArrayList<WordRow>()
        val n = ArrayList<NgramRow>()
        for ((i, t) in toks.withIndex()) {
            val tk = PersianText.key(t)
            val s = words.getOrPut(tk) { Stat(t) }
            s.learned += 1; s.lastUsed = now; s.display = t
            w += WordRow(tk, t, s.learned, now)
            if (i >= 1) {
                val ctx = PersianText.key(toks[i - 1])
                val g = ngram(ctx, tk, t); g.learned += 1; g.lastUsed = now
                n += NgramRow(ctx, tk, g.learned, now)
            }
            if (i >= 2) {
                val ctx = PersianText.key(toks[i - 2]) + " " + PersianText.key(toks[i - 1])
                val g = ngram(ctx, tk, t); g.learned += 1; g.lastUsed = now
                n += NgramRow(ctx, tk, g.learned, now)
            }
        }
        val sk = PersianText.key(norm)
        val s = sentences.getOrPut(sk) { Stat(norm) }
        s.learned += 1; s.lastUsed = now; s.display = norm
        return LearnDelta(w, n, listOf(SentenceRow(sk, norm, s.learned, now)))
    }

    private fun ngram(ctx: String, nextKey: String, display: String) =
        ngrams.getOrPut(ctx) { HashMap() }.getOrPut(nextKey) { Stat(display) }

    // ---------- scoring ----------

    private fun score(s: Stat, now: Long): Double {
        val count = s.seed + s.learned * 3 // what the patient says outweighs the seed lexicon
        var sc = ln(1.0 + count)
        if (s.lastUsed > 0) sc += 2.0 * exp(-(now - s.lastUsed).coerceAtLeast(0) / recencyHalfLifeMs.toDouble() * ln(2.0))
        return sc
    }

    private fun contextProbabilities(ctx: List<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        fun addFrom(key: String, weight: Double) {
            val m = ngrams[key] ?: return
            val total = m.values.sumOf { it.seed + it.learned * 3 }
            if (total <= 0) return
            for ((k, s) in m) out.merge(k, weight * (s.seed + s.learned * 3) / total, Double::plus)
        }
        if (ctx.size >= 2) addFrom(ctx[ctx.size - 2] + " " + ctx.last(), 2.0)
        if (ctx.isNotEmpty()) addFrom(ctx.last(), 1.0)
        return out
    }

    // ---------- suggestions ----------

    /**
     * Up to [limit] word suggestions for [input] (the whole text typed so far).
     * If the current word is empty, predicts the next word from n-grams.
     */
    fun suggestWords(input: String, limit: Int = 4): List<String> {
        val now = clock()
        val trailingSpace = input.isEmpty() || input.last().isWhitespace()
        val toks = PersianText.words(input)
        val partial = if (trailingSpace) "" else toks.lastOrNull().orEmpty()
        val ctx = (if (trailingSpace) toks else toks.dropLast(1)).map { PersianText.key(it) }
        val ctxP = contextProbabilities(ctx)

        if (partial.isEmpty()) {
            val ranked = ctxP.entries.sortedByDescending { it.value + 0.05 * (words[it.key]?.let { s -> score(s, now) } ?: 0.0) }
                .mapNotNull { words[it.key]?.display }
            val fill = if (ranked.size < limit) topWords(now, limit * 2) else emptyList()
            return (ranked + fill).distinct().take(limit)
        }
        val pk = PersianText.key(partial)
        return words.entries.asSequence()
            .filter { it.key.startsWith(pk) && it.key != pk }
            .map { it to score(it.value, now) + 4.0 * (ctxP[it.key] ?: 0.0) }
            .sortedWith(compareByDescending<Pair<Map.Entry<String, Stat>, Double>> { it.second }.thenBy { it.first.key.length })
            .take(limit)
            .map { it.first.value.display }
            .toList()
    }

    private fun topWords(now: Long, n: Int) = words.values.sortedByDescending { score(it, now) }.take(n).map { it.display }

    /** Up to [limit] whole-sentence suggestions matching what has been typed. */
    fun suggestSentences(input: String, limit: Int = 3): List<String> {
        val now = clock()
        val k = PersianText.key(input)
        if (k.isBlank()) {
            return sentences.values.sortedByDescending { score(it, now) + if (it.learned > 0) 1.0 else 0.0 }
                .take(limit).map { it.display }
        }
        val typed = k.split(' ').filter { it.isNotEmpty() }
        return sentences.entries.asSequence()
            .filter { it.key != k }
            .mapNotNull { (sk, s) ->
                val quality = when {
                    sk.startsWith(k) -> 3.0
                    matchesAllWords(sk, typed) -> 1.5
                    else -> return@mapNotNull null
                }
                s.display to quality + score(s, now)
            }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }.toList()
    }

    private fun matchesAllWords(sentenceKey: String, typed: List<String>): Boolean {
        val sw = sentenceKey.split(' ')
        return typed.all { t -> sw.any { it.startsWith(t) } }
    }

    /** Recent sentences the patient said, most recent first. */
    fun recentSentences(limit: Int): List<String> =
        sentences.values.filter { it.lastUsed > 0 }.sortedByDescending { it.lastUsed }.take(limit).map { it.display }

    fun knownSentenceCount(): Int = sentences.size
}
