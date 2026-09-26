package ir.cheshmgoya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheshmgoya.app.ui.Cell
import ir.cheshmgoya.app.ui.CellStyle
import ir.cheshmgoya.app.ui.Grid
import ir.cheshmgoya.app.ui.LocalAacColors
import ir.cheshmgoya.app.ui.LocalFontScale
import ir.cheshmgoya.core.scan.Highlight

/**
 * Renders a screen's options. The highlighted option gets a thick yellow frame
 * and fill; while the eye is closed a bar fills up across it.
 *
 * @param rowWeights optional relative heights per row (e.g. a slimmer suggestion row).
 */
@Composable
fun OptionGrid(
    grid: Grid,
    highlight: Highlight?,
    progress: Float,
    onTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
    rowWeights: (Int) -> Float = { 1f },
    emptyRowPlaceholder: (Int) -> String? = { null },
) {
    Column(modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.rows.forEachIndexed { r, row ->
            val weight = rowWeights(r)
            if (row.isEmpty()) {
                val placeholder = emptyRowPlaceholder(r) ?: return@forEachIndexed
                Box(Modifier.fillMaxWidth().weight(weight), contentAlignment = Alignment.Center) {
                    Text(placeholder, color = LocalAacColors.current.onBackground.copy(alpha = 0.35f), fontSize = 18.sp)
                }
                return@forEachIndexed
            }
            Row(Modifier.fillMaxWidth().weight(weight), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { col, cell ->
                    val rowLit = highlight != null && highlight.isRow && highlight.row == r
                    val itemLit = highlight != null && !highlight.isRow && highlight.row == r && highlight.col == col
                    OptionCell(
                        cell = cell,
                        rowHighlighted = rowLit,
                        highlighted = itemLit,
                        progress = if (itemLit || (rowLit && col == 0)) progress else 0f,
                        onClick = { onTap(r, col) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
fun OptionCell(
    cell: Cell,
    rowHighlighted: Boolean,
    highlighted: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAacColors.current
    val scale = LocalFontScale.current
    val base = when (cell.style) {
        CellStyle.YES -> colors.yes
        CellStyle.NO -> colors.no
        CellStyle.EMERGENCY -> colors.emergency
        CellStyle.NAV -> colors.nav
        CellStyle.CONTROL -> colors.control
        CellStyle.SUGGESTION -> colors.suggestion
        CellStyle.LETTER, CellStyle.NORMAL -> colors.cell
        CellStyle.EMPTY -> colors.background
    }
    val coloured = cell.style !in setOf(CellStyle.NORMAL, CellStyle.LETTER, CellStyle.EMPTY)
    val bg = if (highlighted) colors.highlight else base
    val fg = when {
        highlighted -> colors.onHighlight
        coloured -> Color.White
        else -> colors.onCell
    }
    val borderColor = if (highlighted || rowHighlighted) colors.highlight else colors.outline
    val borderWidth = when {
        highlighted -> 8.dp
        rowHighlighted -> 6.dp
        cell.style == CellStyle.EMPTY -> 0.dp
        else -> 2.dp
    }
    val len = cell.label.length
    val size = when {
        cell.style == CellStyle.LETTER -> 36
        len <= 8 -> 32
        len <= 16 -> 26
        len <= 28 -> 22
        else -> 18
    } * scale
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = cell.style != CellStyle.EMPTY, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.label,
            color = fg,
            fontSize = size.sp,
            lineHeight = (size * 1.25f).sp,
            fontWeight = if (highlighted) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(14.dp)
                    .background(if (highlighted) Color.Black else colors.highlight)
            )
        }
    }
}
