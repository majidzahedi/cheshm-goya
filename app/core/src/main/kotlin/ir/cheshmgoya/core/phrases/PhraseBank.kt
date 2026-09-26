package ir.cheshmgoya.core.phrases

import kotlinx.serialization.Serializable

enum class CategoryId(val titleFa: String) {
    NEEDS("نیازها"),
    PAIN("درد و بدن"),
    FEELINGS("احساسات"),
    PEOPLE("افراد و درخواست‌ها"),
    MY_PHRASES("عبارت‌های من"),
}

@Serializable
data class Phrase(val text: String, val category: String? = null)

/**
 * Built-in first-person, colloquial phrases. The order is fixed so patients can
 * learn where each phrase sits; never reorder at runtime.
 */
object PhraseBank {
    const val YES = "بله"
    const val NO = "نه"
    const val DONT_KNOW = "نمی‌دونم"
    const val WAIT = "صبر کنید"
    const val EMERGENCY = "کمک فوری! لطفاً سریع بیاید!"

    val needs = listOf(
        "آب می‌خوام",
        "تشنمه",
        "گرسنمه",
        "جابه‌جام کنید",
        "ساکشن لازم دارم",
        "دستشویی دارم",
        "پوشکم رو عوض کنید",
        "سردمه، پتو بدید",
        "گرممه، پتو رو بردارید",
        "بالشم رو درست کنید",
        "سرم رو بالا بیارید",
        "تخت رو پایین بیارید",
        "دهنم خشکه",
        "چشمم رو پاک کنید",
        "صورتم رو بشورید",
        "می‌خوام بخوابم",
        "چراغ رو خاموش کنید",
        "چراغ رو روشن کنید",
        "تلویزیون رو روشن کنید",
        "پنجره رو باز کنید",
        "عینکم رو بدید",
        "دارو می‌خوام",
    )

    val pain = listOf(
        "درد دارم",
        "سرم درد می‌کنه",
        "سینه‌ام درد می‌کنه",
        "شکمم درد می‌کنه",
        "کمرم درد می‌کنه",
        "گردنم درد می‌کنه",
        "دستم درد می‌کنه",
        "پام درد می‌کنه",
        "گلوم درد می‌کنه",
        "نفسم تنگه",
        "حالت تهوع دارم",
        "سرم گیج می‌ره",
        "بدنم می‌خاره",
        "پوستم می‌سوزه",
        "لوله اذیتم می‌کنه",
        "ماسک اذیتم می‌کنه",
        "دستم خواب رفته",
        "مسکن لازم دارم",
    )

    val feelings = listOf(
        "می‌ترسم",
        "نگرانم",
        "ناراحتم",
        "خوشحالم",
        "حوصله‌ام سر رفته",
        "خسته‌ام",
        "تنهام",
        "دلم تنگ شده",
        "عصبانی‌ام",
        "حالم خوبه",
        "حالم خوب نیست",
        "دوستتون دارم",
        "ممنونم",
        "ببخشید",
    )

    val people = listOf(
        "پرستار رو صدا کنید",
        "دکتر رو صدا کنید",
        "خانواده‌ام رو صدا کنید",
        "پیشم بمونید",
        "تنهام بذارید",
        "آروم‌تر حرف بزنید",
        "دوباره بگید",
        "یه چیزی بخونید",
        "آهنگ بذارید",
        "به خانواده‌ام زنگ بزنید",
        "دستم رو بگیرید",
        "در رو ببندید",
        "ساعت چنده؟",
        "کی میای؟",
        "چی شده؟",
        "حالم چطوره؟",
    )

    /** Body-part phrases used by the pain screen next to the 0–10 scale. */
    fun byCategory(id: CategoryId): List<String> = when (id) {
        CategoryId.NEEDS -> needs
        CategoryId.PAIN -> pain
        CategoryId.FEELINGS -> feelings
        CategoryId.PEOPLE -> people
        CategoryId.MY_PHRASES -> emptyList()
    }

    val all: List<String> by lazy { listOf(YES, NO, DONT_KNOW, WAIT) + needs + pain + feelings + people }

    /** Sentence spoken after choosing a level on the 0–10 pain scale. */
    fun painLevelSentence(level: Int): String {
        require(level in 0..10)
        val n = ir.cheshmgoya.core.text.PersianText.toPersianDigits(level)
        return when (level) {
            0 -> "درد ندارم"
            else -> "شدت دردم $n از ۱۰ هست"
        }
    }
}
