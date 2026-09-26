package ir.cheshmgoya.app.data

import ir.cheshmgoya.core.text.PersianText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Export/import format for «عبارت‌های من», so families can back up or share a list. */
@Serializable
data class PhrasesFile(val app: String = "cheshm-goya", val version: Int = 1, val phrases: List<String>)

object PhrasesJson {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun export(phrases: List<String>): String = json.encodeToString(PhrasesFile(phrases = phrases))

    /** Accepts our own format or a plain JSON array of strings. */
    fun import(text: String): List<String> {
        val trimmed = text.trim()
        val list = if (trimmed.startsWith("[")) json.decodeFromString<List<String>>(trimmed) else json.decodeFromString<PhrasesFile>(trimmed).phrases
        return list.map { PersianText.normalize(it) }.filter { it.isNotBlank() }.distinct()
    }
}
