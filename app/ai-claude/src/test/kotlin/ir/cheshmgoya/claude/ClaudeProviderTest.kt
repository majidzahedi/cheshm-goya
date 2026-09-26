package ir.cheshmgoya.claude

import ir.cheshmgoya.core.ai.AiContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeProviderTest {
    @Test fun requestCarriesContextAndStructuredOutput() {
        val p = ClaudeProvider.buildParams(ClaudeProvider.DEFAULT_MODEL, AiContext("سرم", listOf("آب می‌خوام"), 22))
        assertEquals(ClaudeProvider.DEFAULT_MODEL, p.model().asString())
        val user = p.messages().single().content().string().get()
        assertTrue(user.contains("سرم") && user.contains("آب می‌خوام") && user.contains("22"))
        assertTrue(p.outputConfig().get().format().isPresent)
    }

    @Test fun parsesSentences() {
        val s = ClaudeProvider.parseSentences("""{"sentences":[" سرم درد می‌کنه ","سردمه",""," آب می‌خوام","اضافه"]}""")
        assertEquals(listOf("سرم درد می‌کنه", "سردمه", "آب می‌خوام"), s)
    }
}
