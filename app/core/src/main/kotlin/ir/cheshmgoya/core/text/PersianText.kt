package ir.cheshmgoya.core.text

/** Persian text helpers shared by prediction, phrase search and the keyboard. */
object PersianText {
    const val ZWNJ = '\u200C'

    private val diacritics = Regex("[\u064B-\u065F\u0670\u0640]") // harakat, superscript alef, tatweel
    private val spaces = Regex("\\s+")

    /**
     * Canonical display form: Arabic ي/ى → ی, ك → ک, collapse whitespace.
     * The half-space and tanvin (لطفاً) are kept because they are part of correct spelling.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    in '\u0660'..'\u0669' -> '\u06F0' + (c - '\u0660') // Arabic-Indic → Persian digits
                    else -> c
                }
            )
        }
        return sb.toString().replace('\u0640'.toString(), "").replace(spaces, " ").trim() // drop tatweel
    }

    /**
     * Comparison key: [normalize] plus diacritics and the half-space removed, ۀ/ة → ه, أ/إ → ا,
     * so that «می‌خوام», «میخوام» and «مي خوام»-style typing variants match.
     */
    fun key(text: String): String {
        val n = normalize(text).replace(diacritics, "")
        val sb = StringBuilder(n.length)
        for (c in n) {
            when (c) {
                ZWNJ -> Unit
                'ۀ', 'ة' -> sb.append('ه')
                'أ', 'إ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                else -> sb.append(c)
            }
        }
        return sb.toString().lowercase()
    }

    /** Splits into words on whitespace and punctuation; keeps ZWNJ inside words. */
    fun words(text: String): List<String> =
        normalize(text).split(' ', '،', '.', '؟', '?', '!', ',', '؛', ';', ':', '«', '»', '"')
            .filter { it.isNotBlank() }

    private val faDigits = "۰۱۲۳۴۵۶۷۸۹"

    fun toPersianDigits(n: Int): String = n.toString().map { if (it.isDigit()) faDigits[it - '0'] else it }.joinToString("")
}
