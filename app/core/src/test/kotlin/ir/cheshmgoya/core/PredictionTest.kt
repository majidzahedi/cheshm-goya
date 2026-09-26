package ir.cheshmgoya.core

import ir.cheshmgoya.core.keyboard.Key
import ir.cheshmgoya.core.keyboard.KeyboardLayout
import ir.cheshmgoya.core.keyboard.PersianKeyboard
import ir.cheshmgoya.core.phrases.PhraseBank
import ir.cheshmgoya.core.predict.LanguageModel
import ir.cheshmgoya.core.text.PersianText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionTest {
    private var now = 1_000_000_000L
    private fun model() = LanguageModel { now }.apply {
        seedDefaultLexicon()
        PhraseBank.all.forEach { seedSentence(it, 0.5) }
    }

    @Test fun normalizationUnifiesArabicLettersAndHalfSpace() {
        assertEquals("یک کتاب", PersianText.normalize("يك  كتاب"))
        assertEquals(PersianText.key("می‌خوام"), PersianText.key("میخوام"))
        assertEquals("۱۲", PersianText.normalize("١٢"))
    }

    @Test fun lexiconHasSeveralHundredWords() {
        val lines = LanguageModel::class.java.getResourceAsStream("/lexicon_fa.txt")!!.bufferedReader().readLines()
        assertTrue(lines.count { !it.startsWith("#") } >= 400)
        val letters = PersianKeyboard.letters32.toSet() + PersianKeyboard.extras + PersianText.ZWNJ.toString() + "\u064B" + "أ"
        for (l in lines.filterNot { it.startsWith("#") }) {
            val w = l.split('\t')[0]
            assertTrue("bad word '$w'", w.all { it.toString() in letters })
        }
    }

    @Test fun prefixSuggestsFourWords() {
        val s = model().suggestWords("سر")
        assertEquals(4, s.size)
        assertTrue(s.all { PersianText.key(it).startsWith("سر") })
    }

    @Test fun prefixMatchesIgnoringHalfSpaceAndArabicYeh() {
        val m = model()
        assertTrue(m.suggestWords("ميخو").any { PersianText.key(it) == PersianText.key("می‌خوام") })
    }

    @Test fun nextWordFromBigramWhenCurrentWordEmpty() {
        val m = model()
        repeat(3) { m.learn("سرم درد می‌کنه") }
        assertEquals("درد", m.suggestWords("سرم ").first())
    }

    @Test fun trigramBeatsBigram() {
        val m = LanguageModel { now }
        repeat(5) { m.learn("آب سرد بده") }
        repeat(2) { m.learn("من سرد نیست") }
        repeat(3) { m.learn("خیلی سرد هست") }
        assertEquals("بده", m.suggestWords("آب سرد ").first())
        assertEquals("هست", m.suggestWords("خیلی سرد ").first())
    }

    @Test fun learnedWordsOutrankSeeds() {
        val m = model()
        repeat(4) { m.learn("سرنگ رو بیارید") }
        assertEquals("سرنگ", m.suggestWords("سر").first())
    }

    @Test fun recencyBreaksFrequencyTies() {
        val m = LanguageModel { now }
        m.learn("تخت")
        now += 30L * 24 * 3600 * 1000
        m.learn("تختخواب")
        assertEquals("تختخواب", m.suggestWords("تخ").first())
    }

    @Test fun sentenceSuggestionsFromBankAndHistory() {
        val m = model()
        val s = m.suggestSentences("سرم")
        assertTrue(s.contains("سرم درد می‌کنه"))
        assertTrue(s.size <= 3)
        m.learn("سرمو بخارونید لطفاً")
        m.learn("سرمو بخارونید لطفاً")
        assertEquals("سرمو بخارونید لطفاً", m.suggestSentences("سرمو").first())
    }

    @Test fun sentenceMatchesWordsAnywhere() {
        val s = model().suggestSentences("درد سین")
        assertTrue(s.contains("سینه‌ام درد می‌کنه"))
    }

    @Test fun learnReturnsRowsToPersistAndRestoreRebuildsState() {
        val m = LanguageModel { now }
        val d = m.learn("آب می‌خوام")
        assertEquals(2, d.words.size)
        assertEquals(1, d.ngrams.size)
        assertEquals(1, d.sentences.size)
        val m2 = LanguageModel { now }
        m2.restore(d.words, d.ngrams, d.sentences)
        assertEquals(listOf("آب می‌خوام"), m2.recentSentences(5))
        assertEquals("می‌خوام", m2.suggestWords("آب ").first())
    }

    @Test fun keyboardEditing() {
        var t = ""
        for (c in listOf("س", "ل", "ا", "م")) t = PersianKeyboard.apply(t, Key.Char(c))
        t = PersianKeyboard.apply(t, Key.Space)
        t = PersianKeyboard.apply(t, Key.Space)
        assertEquals("سلام ", t)
        t = PersianKeyboard.acceptWord(t + "خو", "خوبی")
        assertEquals("سلام خوبی ", t)
        t = PersianKeyboard.apply(t, Key.Char("؟"))
        assertEquals("سلام خوبی؟ ", t)
        assertEquals("سلام ", PersianKeyboard.apply("سلام می‌خوام", Key.DeleteWord))
        assertEquals("", PersianKeyboard.apply(t, Key.Clear))
        assertEquals("سلا", PersianKeyboard.apply("سلام", Key.Backspace))
    }

    @Test fun keyboardHasAllCharacters() {
        for (layout in KeyboardLayout.entries) {
            val chars = PersianKeyboard.letterRows(layout).flatten().filterIsInstance<Key.Char>().map { it.value }.toSet()
            assertTrue(chars.containsAll(PersianKeyboard.letters32))
            assertTrue(chars.containsAll(PersianKeyboard.extras + PersianKeyboard.punctuation + PersianKeyboard.digits))
            assertTrue(chars.contains(PersianText.ZWNJ.toString()))
            assertEquals(32, PersianKeyboard.letters32.toSet().size)
        }
        assertFalse(PersianKeyboard.controlRow.isEmpty())
    }

    @Test fun painScaleSentence() {
        assertEquals("شدت دردم ۷ از ۱۰ هست", PhraseBank.painLevelSentence(7))
        assertEquals("درد ندارم", PhraseBank.painLevelSentence(0))
    }
}
