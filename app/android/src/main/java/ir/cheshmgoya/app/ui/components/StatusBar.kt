package ir.cheshmgoya.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheshmgoya.app.ui.LocalAacColors
import ir.cheshmgoya.core.ai.ConnectionStatus
import ir.cheshmgoya.core.ai.ProviderId

/**
 * Thin top bar: connection indicator, and the companion's buttons (mic,
 * history, settings). Settings needs a long press so it isn't opened by accident.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusBar(
    provider: ProviderId,
    connection: ConnectionStatus,
    listening: Boolean,
    onMic: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalAacColors.current
    val (dot, label) = when {
        provider == ProviderId.OFF -> Color.Gray to "آفلاین"
        provider == ProviderId.OFFLINE -> Color.Gray to "پیش‌بینی آفلاین"
        connection == ConnectionStatus.CONNECTED -> Color(0xFF00C853) to (if (provider == ProviderId.LOCAL_SERVER) "سرور وصل است" else "هوش مصنوعی وصل است")
        else -> Color(0xFFFFAB00) to "آفلاین (سرویس در دسترس نیست)"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Text(label, color = colors.onBackground.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        BarButton(if (listening) "⏹ توقف" else "🎤 سؤال", highlighted = listening, onClick = onMic)
        BarButton("تاریخچه", onClick = onHistory)
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(colors.control)
                .combinedClickable(onClick = {}, onLongClick = onSettings)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) { Text("⚙ تنظیمات (نگه دارید)", color = Color.White, fontSize = 14.sp) }
    }
}

@Composable
private fun BarButton(text: String, highlighted: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAacColors.current
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (highlighted) colors.emergency else colors.control)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = Color.White, fontSize = 14.sp) }
}

/** Coloured strip for warnings (face not visible, sleeping, missing voice…). */
@Composable
fun Banner(text: String, color: Color, big: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(color)
            .padding(horizontal = 14.dp, vertical = if (big) 14.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, color = Color.White, fontSize = if (big) 30.sp else 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            BarButton(actionLabel, onClick = onAction)
        }
    }
}
