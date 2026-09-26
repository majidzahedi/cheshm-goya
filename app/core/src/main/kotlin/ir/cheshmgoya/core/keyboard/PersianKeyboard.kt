package ir.cheshmgoya.core.keyboard

import ir.cheshmgoya.core.text.PersianText

enum class KeyboardLayout(val titleFa: String) { FREQUENCY("پرکاربرد اول"), ALPHABETIC("الفبایی") }

sealed interface Key {
    val label: String

    data class Char(val value: String, override val label: String = value) : Key
    data object Speak : Key { override val label = "بگو" }
    data object Space : Key { override val label = "فاصله" }
    data object Backspace : Key { override val label = "حذف" }
    data object DeleteWord : Key { override val label = "حذف کلمه" }
    data object Clear : Key { override val label = "پاک همه" }
    data object Home : Key { override val label = "خانه" }
}

/**
 * The full Persian keyboard: 32 letters + آ ئ ء, half-space, Persian digits and ؟ ! ، .
 * Rows are short so row-column scanning stays fast.
 */
object PersianKeyboard {
    val letters32 = listOf(
        "ا", "ب", "پ", "ت", "ث", "ج", "چ", "ح", "خ", "د", "ذ", "ر", "ز", "ژ", "س", "ش",
        "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م", "ن", "و", "ه", "ی",
    )
    val extras = listOf("آ", "ئ", "ء")
    val punctuation = listOf("؟", "!", "،", ".")
    val digits = (0..9).map { PersianText.toPersianDigits(it) }

    /** Approximate letter frequency in everyday Persian. */
    val frequencyOrder = listOf(
        "ا", "ی", "ن", "م", "ر", "ه", "و", "د", "ت", "ب", "س", "ش", "ک", "ل", "ز", "گ",
        "خ", "ف", "ج", "آ", "ق", "پ", "چ", "ع", "ح", "ص", "ط", "غ", "ض", "ث", "ذ", "ظ", "ژ", "ئ", "ء",
    )

    val controlRow: List<Key> = listOf(Key.Speak, Key.Space, Key.Backspace, Key.DeleteWord, Key.Clear, Key.Home)

    fun letterRows(layout: KeyboardLayout, perRow: Int = 7): List<List<Key>> {
        val letters = when (layout) {
            KeyboardLayout.FREQUENCY -> frequencyOrder
            KeyboardLayout.ALPHABETIC -> listOf("آ") + letters32 + listOf("ئ", "ء")
        }
        val half = Key.Char(PersianText.ZWNJ.toString(), "نیم‌فاصله")
        val symbols = (letters.map { Key.Char(it) } + half + punctuation.map { Key.Char(it) })
        return symbols.chunked(perRow) + digits.map { Key.Char(it) }.chunked(perRow.coerceAtLeast(5))
    }

    /** Applies a key to the text being composed. Speak/Home are handled by the caller. */
    fun apply(text: String, key: Key): String = when (key) {
        is Key.Char -> {
            val t = if (key.value in punctuation && text.endsWith(" ")) text.trimEnd() else text
            t + key.value + if (key.value in punctuation) " " else ""
        }
        Key.Space -> if (text.isEmpty() || text.endsWith(" ")) text else "$text "
        Key.Backspace -> if (text.isEmpty()) text else text.dropLast(1)
        Key.DeleteWord -> {
            val t = text.trimEnd()
            val cut = t.lastIndexOf(' ') // the half-space is part of a word (می‌خوام), so it is not a boundary
            if (cut < 0) "" else t.substring(0, cut + 1)
        }
        Key.Clear -> ""
        Key.Speak, Key.Home -> text
    }

    /** Replace the word being typed with a chosen suggestion and add a space. */
    fun acceptWord(text: String, word: String): String {
        if (text.isEmpty() || text.endsWith(" ")) return "$text$word "
        val cut = text.lastIndexOf(' ')
        return text.substring(0, cut + 1) + word + " "
    }
}
