package ir.cheshmgoya.core

import ir.cheshmgoya.core.ai.AiContext
import ir.cheshmgoya.core.ai.AiCoordinator
import ir.cheshmgoya.core.ai.AiProvider
import ir.cheshmgoya.core.ai.ConnectionStatus
import ir.cheshmgoya.core.ai.HttpTransport
import ir.cheshmgoya.core.ai.JevProvider
import ir.cheshmgoya.core.ai.LocalServerProvider
import ir.cheshmgoya.core.ai.OfflineProvider
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.ai.QuestionClass
import ir.cheshmgoya.core.ai.QuestionClassifier
import ir.cheshmgoya.core.ai.RankResult
import ir.cheshmgoya.core.ai.RankedPhrase
import ir.cheshmgoya.core.phrases.PhraseBank
import ir.cheshmgoya.core.predict.LanguageModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLayerTest {
    private val ctx = AiContext("سرم", listOf("آب می‌خوام"), 14)
    private val offline = OfflineProvider(LanguageModel().apply { PhraseBank.all.forEach { seedSentence(it) } })

    private open class Fake(val delayMs: Long = 0, val fail: Boolean = false) : AiProvider {
        override val id = ProviderId.LOCAL_SERVER
        override suspend fun complete(context: AiContext): List<String>? { delay(delayMs); if (fail) error("boom"); return listOf("الف", "ب", "ج", "د") }
        override suspend fun rank(context: AiContext, phrases: List<String>): RankResult? {
            delay(delayMs); if (fail) error("boom")
            return RankResult(phrases.reversed().mapIndexed { i, p -> RankedPhrase(p, 0.6 / (i + 1)) }, 0.6)
        }
        override suspend fun classify(question: String) = QuestionClass("yes_no", null, 0.9)
        override suspend fun ping() = !fail
    }

    @Test fun usesProviderWhenHealthy() = runTest {
        val c = AiCoordinator(offline).apply { provider = Fake() }
        assertEquals(listOf("الف", "ب", "ج"), c.complete(ctx))
        assertEquals(ConnectionStatus.CONNECTED, c.status.value)
    }

    @Test fun timeoutFallsBackSilentlyToOffline() = runTest {
        val c = AiCoordinator(offline).apply { provider = Fake(delayMs = 5000) }
        val r = c.complete(ctx)
        assertTrue(r.contains("سرم درد می‌کنه"))
        assertEquals(ConnectionStatus.FALLBACK, c.status.value)
    }

    @Test fun rankTimeoutIsOneSecond() = runTest {
        val c = AiCoordinator(offline).apply { provider = Fake(delayMs = 1500) }
        c.suggestionsFor(ctx, listOf("الف", "ب"))
        assertEquals(ConnectionStatus.FALLBACK, c.status.value)
    }

    @Test fun errorFallsBack() = runTest {
        val c = AiCoordinator(offline).apply { provider = Fake(fail = true) }
        assertTrue(c.complete(ctx).isNotEmpty())
        assertEquals("yes_no", c.classify("آب می‌خوای؟").type)
        assertEquals(ConnectionStatus.FALLBACK, c.ping())
    }

    @Test fun suggestionRowOnlyWhenConfident() = runTest {
        val c = AiCoordinator(offline, rankConfidenceThreshold = 0.5).apply { provider = Fake() }
        assertEquals(listOf("ب", "الف"), c.suggestionsFor(ctx, listOf("الف", "ب")))
        c.rankConfidenceThreshold = 0.9
        assertTrue(c.suggestionsFor(ctx, listOf("الف", "ب")).isEmpty())
    }

    @Test fun aiOffMeansNoSuggestionRow() = runTest {
        val c = AiCoordinator(offline)
        assertTrue(c.suggestionsFor(ctx, PhraseBank.needs).isEmpty())
        assertEquals(ConnectionStatus.OFFLINE_MODE, c.status.value)
    }

    @Test fun offlineQuestionClassifier() {
        val q = QuestionClassifier()
        assertEquals("yes_no", q.classify("آب می‌خوای؟").type)
        assertEquals("needs", q.classify("آب می‌خوای؟").topic)
        assertEquals("pain", q.classify("کجات درد می‌کنه؟").topic)
        assertEquals("choice", q.classify("کجات درد می‌کنه؟").type)
        assertEquals("yes_no", q.classify("درد داری؟").type)
        assertEquals("pain", q.classify("درد داری؟").topic)
        assertEquals("feelings", q.classify("حالت چطوره؟").topic)
        assertEquals("choice", q.classify("حالت چطوره؟").type)
        assertEquals("open", q.classify("امروز چی شد؟").type)
    }

    private class RecordingTransport(val code: Int, val response: String) : HttpTransport {
        var lastUrl = ""; var lastBody = ""; var lastHeaders = emptyMap<String, String>()
        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpTransport.Response {
            lastUrl = url; lastBody = body; lastHeaders = headers; return HttpTransport.Response(code, response)
        }
        override suspend fun get(url: String, headers: Map<String, String>, timeoutMs: Long) = HttpTransport.Response(code, response)
    }

    @Test fun localServerWireFormat() = runTest {
        val t = RecordingTransport(200, """{"suggestions":["سرم درد می‌کنه","سردمه"],"model":"x"}""")
        val p = LocalServerProvider("http://192.168.1.5:8765/", "tok", t)
        assertEquals(listOf("سرم درد می‌کنه", "سردمه"), p.complete(ctx))
        assertEquals("http://192.168.1.5:8765/complete", t.lastUrl)
        assertEquals("Bearer tok", t.lastHeaders["Authorization"])
        assertTrue(t.lastBody.contains("\"hour\":14"))
    }

    @Test fun localServerHttpErrorThrows() = runTest {
        val p = LocalServerProvider("http://x", "bad", RecordingTransport(401, "{}"))
        val c = AiCoordinator(offline).apply { provider = p }
        assertTrue(c.complete(ctx).isNotEmpty()) // falls back
        assertEquals(ConnectionStatus.FALLBACK, c.status.value)
    }

    @Test fun jevRankRequestAndResponse() = runTest {
        val resp = """{"model":"jev-1.13.0","answers":{"next_phrase":{"type":"choice","choice":"p1","confidence":0.8,"probabilities":{"p0":0.2,"p1":0.8}}}}"""
        val t = RecordingTransport(200, resp)
        val r = JevProvider("k", t).rank(ctx, listOf("آب می‌خوام", "درد دارم"))!!
        assertEquals("درد دارم", r.ranked.first().text)
        assertEquals(0.8, r.confidence, 1e-9)
        assertTrue(t.lastBody.contains("\"model\":\"jev-1.13.0\""))
        assertTrue(t.lastBody.contains("\"type\":\"choice\""))
        assertEquals(JevProvider.ENDPOINT, t.lastUrl)
    }

    @Test fun jevClassify() {
        val body = """{"answers":{"is_yes_no":{"type":"noul","probability":0.93},"topic":{"type":"choice","choice":"pain","confidence":0.7,"probabilities":{}}}}"""
        val q = JevProvider.parseClassify(body)!!
        assertEquals("yes_no", q.type)
        assertEquals("pain", q.topic)
    }
}
