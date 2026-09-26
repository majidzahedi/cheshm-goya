package ir.cheshmgoya.app.ui

import ir.cheshmgoya.core.keyboard.Key
import ir.cheshmgoya.core.keyboard.KeyboardLayout
import ir.cheshmgoya.core.keyboard.PersianKeyboard
import ir.cheshmgoya.core.phrases.CategoryId
import ir.cheshmgoya.core.phrases.PhraseBank
import ir.cheshmgoya.core.text.PersianText

/**
 * Builds the option grid of each patient screen. Fixed buttons always keep the
 * same place — only the separate suggestion rows change.
 */
object GridBuilder {
    private val home = Cell("خانه", CellAction.GoHome, CellStyle.NAV)

    fun home(suggestions: List<String>): Grid = Grid(
        listOf(
            suggestions.map { Cell(it, CellAction.Say(it), CellStyle.SUGGESTION) },
            listOf(
                Cell(PhraseBank.YES, CellAction.Say(PhraseBank.YES), CellStyle.YES),
                Cell(PhraseBank.NO, CellAction.Say(PhraseBank.NO), CellStyle.NO),
                Cell("کمک فوری", CellAction.Emergency, CellStyle.EMERGENCY),
            ),
            listOf(
                Cell(PhraseBank.DONT_KNOW, CellAction.Say(PhraseBank.DONT_KNOW)),
                Cell(PhraseBank.WAIT, CellAction.Say(PhraseBank.WAIT)),
                Cell("نوشتن", CellAction.Go(Screen.Keyboard), CellStyle.NAV),
            ),
            listOf(
                Cell(CategoryId.NEEDS.titleFa, CellAction.Go(Screen.Category(CategoryId.NEEDS)), CellStyle.NAV),
                Cell(CategoryId.PAIN.titleFa, CellAction.Go(Screen.Category(CategoryId.PAIN)), CellStyle.NAV),
                Cell(CategoryId.FEELINGS.titleFa, CellAction.Go(Screen.Category(CategoryId.FEELINGS)), CellStyle.NAV),
            ),
            listOf(
                Cell(CategoryId.PEOPLE.titleFa, CellAction.Go(Screen.Category(CategoryId.PEOPLE)), CellStyle.NAV),
                Cell(CategoryId.MY_PHRASES.titleFa, CellAction.Go(Screen.Category(CategoryId.MY_PHRASES)), CellStyle.NAV),
                Cell("استراحت", CellAction.Rest, CellStyle.NAV),
            ),
        )
    )

    fun category(id: CategoryId, customPhrases: List<String>): Grid {
        val phrases = if (id == CategoryId.MY_PHRASES) customPhrases else PhraseBank.byCategory(id)
        val top = buildList {
            add(home)
            if (id == CategoryId.PAIN) add(Cell("شدت درد ۰ تا ۱۰", CellAction.Go(Screen.PainScale), CellStyle.NAV))
        }
        val body = phrases.map { Cell(it, CellAction.Say(it)) }.chunked(3)
        val empty = if (phrases.isEmpty()) listOf(listOf(Cell("هنوز عبارتی اضافه نشده", CellAction.None, CellStyle.EMPTY))) else emptyList()
        return Grid(listOf(top) + body + empty)
    }

    fun painScale(): Grid = Grid(
        listOf(
            listOf(home, Cell("برگشت", CellAction.Go(Screen.Category(CategoryId.PAIN)), CellStyle.NAV)),
            (0..5).map { Cell(PersianText.toPersianDigits(it), CellAction.PainLevel(it), CellStyle.LETTER) },
            (6..10).map { Cell(PersianText.toPersianDigits(it), CellAction.PainLevel(it), CellStyle.LETTER) },
        )
    )

    fun yesNo(): Grid = Grid(
        listOf(
            listOf(
                Cell(PhraseBank.YES, CellAction.Say(PhraseBank.YES), CellStyle.YES),
                Cell(PhraseBank.NO, CellAction.Say(PhraseBank.NO), CellStyle.NO),
            ),
            listOf(
                Cell(PhraseBank.DONT_KNOW, CellAction.Say(PhraseBank.DONT_KNOW)),
                Cell(PhraseBank.WAIT, CellAction.Say(PhraseBank.WAIT)),
            ),
            listOf(home),
        )
    )

    /**
     * Keyboard: word suggestions, sentence suggestions and the control row sit
     * above the letters. Suggestion rows have a fixed number of cells so that
     * late-arriving AI suggestions never reset the scan position.
     */
    fun keyboard(layout: KeyboardLayout, words: List<String>, sentences: List<String>): Grid {
        fun pad(list: List<Cell>, n: Int) = list.take(n) + List((n - list.size).coerceAtLeast(0)) { Cell("", CellAction.None, CellStyle.EMPTY) }
        val wordRow = pad(words.map { Cell(it, CellAction.AcceptWord(it), CellStyle.SUGGESTION) }, 4)
        val sentenceRow = pad(sentences.map { Cell(it, CellAction.Say(it), CellStyle.SUGGESTION) }, 3)
        val control = PersianKeyboard.controlRow.map { Cell(it.label, CellAction.Type(it), CellStyle.CONTROL) }
        val letters = PersianKeyboard.letterRows(layout).map { row -> row.map { Cell(it.label, CellAction.Type(it), CellStyle.LETTER) } }
        return Grid(listOf(wordRow, sentenceRow, control) + letters)
    }

    /** Label to read aloud for a highlighted row. */
    fun rowLabel(row: List<Cell>): String = row.firstOrNull { it.style != CellStyle.EMPTY }?.label.orEmpty()

    fun isKeyboardSpeak(a: CellAction) = a is CellAction.Type && a.key == Key.Speak
}
