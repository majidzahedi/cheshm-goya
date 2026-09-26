package ir.cheshmgoya.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheshmgoya.app.data.InputMode
import ir.cheshmgoya.app.speech.TtsState
import ir.cheshmgoya.app.ui.LocalAacColors
import ir.cheshmgoya.app.ui.LocalFontScale
import ir.cheshmgoya.app.ui.Screen
import ir.cheshmgoya.app.ui.UiState
import ir.cheshmgoya.app.ui.components.Banner
import ir.cheshmgoya.app.ui.components.OptionGrid
import ir.cheshmgoya.app.ui.components.StatusBar

/** Home, category, pain scale, yes/no and keyboard screens: all scanned grids. */
@Composable
fun PatientScreen(
    s: UiState,
    onTap: (Int, Int) -> Unit,
    onMic: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onInstallVoice: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val colors = LocalAacColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        StatusBar(s.settings.aiProvider, s.connection, s.listening, onMic, onHistory, onSettings)

        val cameraMode = s.settings.inputMode.usesCamera
        if (cameraMode && s.cameraError != null) Banner(s.cameraError, colors.warning)
        else if (cameraMode && !s.faceVisible) Banner("چهره دیده نمی‌شود — اسکن متوقف شد. تبلت را روبه‌روی صورت بیمار قرار دهید.", colors.warning)
        if (cameraMode && s.sleeping) Banner("چشم‌ها مدتی بسته بوده‌اند — اسکن متوقف است. با باز کردن چشم ادامه می‌یابد.", colors.nav)
        if (cameraMode && !s.settings.calibrated) Banner("هنوز کالیبراسیون انجام نشده. از تنظیمات، «کالیبراسیون» را اجرا کنید.", colors.control)
        if (s.tts == TtsState.PERSIAN_MISSING || s.tts == TtsState.UNAVAILABLE) {
            Banner("صدای فارسی روی این دستگاه نصب نیست؛ پیام‌ها روی صفحه نمایش داده می‌شوند.", colors.warning, actionLabel = "نصب صدای فارسی", onAction = onInstallVoice)
        }
        s.notice?.let { Banner(it, colors.control, actionLabel = "باشه", onAction = onDismissNotice) }
        s.question?.let { Banner("سؤال: $it", colors.nav, big = true) }
        (s.bigMessage ?: s.lastSpoken)?.let { msg ->
            val big = s.bigMessage != null
            Box(
                Modifier.fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (big) colors.highlight else colors.cell).border(2.dp, colors.highlight, RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismissNotice).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "«$msg»", color = if (big) Color.Black else colors.onCell, fontWeight = FontWeight.Black,
                    fontSize = (if (big) 56 else 34).sp, lineHeight = (if (big) 68 else 42).sp, textAlign = TextAlign.Center,
                )
            }
        }

        val title = when (val sc = s.screen) {
            is Screen.Category -> sc.id.titleFa
            Screen.PainScale -> "شدت درد: ۰ یعنی بدون درد، ۱۰ یعنی شدیدترین درد"
            Screen.YesNo -> null
            else -> null
        }
        title?.let { Text(it, color = colors.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp)) }

        if (s.screen == Screen.Keyboard) ComposingText(s.composing)

        val isHome = s.screen == Screen.Home
        val isKeyboard = s.screen == Screen.Keyboard
        OptionGrid(
            grid = s.grid,
            highlight = s.highlight,
            progress = s.blinkProgress,
            onTap = onTap,
            modifier = Modifier.weight(1f),
            rowWeights = { r ->
                when {
                    isHome && r == 0 -> 0.55f
                    isKeyboard && r <= 1 -> 0.9f
                    else -> 1f
                }
            },
            emptyRowPlaceholder = { r -> if (isHome && r == 0 && s.settings.aiProvider != ir.cheshmgoya.core.ai.ProviderId.OFF) "پیشنهادها" else null },
        )
        if (s.settings.inputMode == InputMode.GAZE_BLINK) {
            Text(
                "برای جابه‌جایی، به چپ یا راست نگاه کنید؛ برای انتخاب، پلک بزنید.",
                color = colors.onBackground.copy(alpha = 0.7f), fontSize = 16.sp, modifier = Modifier.padding(6.dp),
            )
        }
    }
}

/** The text being typed, large, with a blinking caret. */
@Composable
fun ComposingText(text: String) {
    val colors = LocalAacColors.current
    val scale = LocalFontScale.current
    val caret by rememberInfiniteTransition(label = "caret").animateFloat(
        0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "caretAlpha",
    )
    Row(
        Modifier.fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(12.dp)).background(colors.cell)
            .border(2.dp, colors.outline, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.ifEmpty { " " }, color = colors.onCell, fontSize = (40 * scale).sp, lineHeight = (50 * scale).sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false),
        )
        Box(Modifier.size(width = 4.dp, height = (44 * scale).dp).background(colors.highlight.copy(alpha = caret)))
    }
}

@Composable
fun RestScreen(blinks: Int, usesCamera: Boolean, onWake: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("حالت استراحت", color = Color(0xFF9E9E9E), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text(
            if (usesCamera) "برای بازگشت: ۳ پلک ارادی پشت سر هم (در ۵ ثانیه)" else "برای بازگشت: ۳ بار کلید را بزنید",
            color = Color(0xFF757575), fontSize = 26.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (i < blinks) Color(0xFFFFD600) else Color(0xFF424242)))
            }
        }
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF212121)).clickable(onClick = onWake)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) { Text("بازگشت (همراه بیمار)", color = Color(0xFF9E9E9E), fontSize = 18.sp) }
    }
}

/**
 * Flashing full-screen alarm. Only the companion can stop it, by holding the
 * button for two seconds — blinks are ignored here.
 */
@Composable
fun EmergencyScreen(smsSent: Boolean, onStop: () -> Unit) {
    val t by rememberInfiniteTransition(label = "flash").animateFloat(
        0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "flashT",
    )
    val bg = lerp(Color(0xFFD50000), Color(0xFFFFEBEE), t)
    val fg = lerp(Color.White, Color(0xFFB71C1C), t)
    Column(
        Modifier.fillMaxSize().background(bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("کمک فوری!", color = fg, fontSize = 96.sp, fontWeight = FontWeight.Black)
        Text("بیمار به کمک نیاز دارد", color = fg, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        if (smsSent) Text("پیامک اضطراری ارسال شد", color = fg, fontSize = 22.sp)
        Spacer(Modifier.height(64.dp))
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        // null only if the finger stayed down for the whole two seconds
                        val letGo = withTimeoutOrNull(2000) { waitForUpOrCancellation(); true }
                        if (letGo == null) onStop()
                    }
                }
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Text("قطع آژیر: ۲ ثانیه نگه دارید (فقط همراه بیمار)", color = Color.White, fontSize = 24.sp)
        }
    }
}
