package ir.cheshmgoya.core.ai

import ir.cheshmgoya.core.text.PersianText

/**
 * Offline, rule-based understanding of a companion's spoken question.
 * Used directly when no server is available and as a fallback otherwise.
 */
class QuestionClassifier {

    private val topics: List<Pair<String, List<String>>> = listOf(
        "pain" to listOf("درد", "میسوز", "می سوز", "اذیت", "ناراحتی جسمی", "کجات", "سرت", "دستت", "پات", "شکمت", "سینه", "نفس", "حالت تهوع", "میخار", "خارش", "گیج"),
        "needs" to listOf("غذا", "گرسن", "تشنه", "آب", "بخوری", "بنوشی", "دستشویی", "جابجا", "جابه جا", "پتو", "سرد", "گرم", "بخوابی", "خواب", "ساکشن", "تلویزیون", "چراغ", "بالش", "لباس", "حموم"),
        "feelings" to listOf("حالت", "احساس", "ناراحت", "خوشحال", "میترسی", "می ترسی", "نگران", "حوصله", "عصبانی", "دلت", "خسته"),
        "people" to listOf("کی رو", "کسی", "صدا کنم", "زنگ", "خانواده", "پرستار", "دکتر", "ملاقات", "بیاد", "پیشت"),
    )

    private val yesNoStarts = listOf("آیا", "ایا")
    private val yesNoVerbs = listOf("میخوای", "می خوای", "میخواین", "داری", "دارین", "هست", "هستی", "خوبی", "میتونی", "می تونی", "موافقی", "باشه", "بیارم", "بدم", "کنم", "ببرم", "بذارم")
    private val openWords = listOf("چی", "چه", "چرا", "کی")

    /** Question words that also appear inflected (چطوره، کجاست، چندتا …). */
    private val openPrefixes = listOf("چطور", "چجور", "کجا", "کدوم", "چند")

    fun classify(question: String): QuestionClass {
        // Keys drop the half-space, so match with spaces removed as well.
        val k = PersianText.key(question)
        val compact = k.replace(" ", "")
        fun has(word: String) = k.contains(word) || compact.contains(word.replace(" ", ""))

        val topic = topics.firstOrNull { (_, words) -> words.any { has(it) } }?.first
        val words = k.split(' ').map { it.trim('؟', '?', '!', '.', '،', ',') }
        val isOpen = words.any { w -> w in openWords || openPrefixes.any { w.startsWith(it) } }
        val isYesNo = !isOpen && (yesNoStarts.any { k.startsWith(it) } || yesNoVerbs.any { has(it) } || k.endsWith("؟") || k.endsWith("?"))
        val type = when {
            isOpen -> if (topic != null) "choice" else "open"
            isYesNo -> "yes_no"
            else -> if (topic != null) "choice" else "open"
        }
        val confidence = when {
            type == "yes_no" && !isOpen -> 0.7
            topic != null -> 0.6
            else -> 0.3
        }
        return QuestionClass(type, topic, confidence)
    }
}
