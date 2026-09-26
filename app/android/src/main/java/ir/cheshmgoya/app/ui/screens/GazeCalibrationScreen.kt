package ir.cheshmgoya.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheshmgoya.app.ui.GazeCalPhase
import ir.cheshmgoya.app.ui.GazeCalibrationState
import ir.cheshmgoya.core.text.PersianText
import java.util.Locale

/**
 * 9-point gaze calibration, then a 5-point accuracy test. The patient follows
 * a yellow dot with their eyes; the result says how big options must be.
 */
@Composable
fun GazeCalibrationScreen(
    state: GazeCalibrationState,
    onArea: (left: Float, top: Float, width: Float, height: Float) -> Unit,
    onStart: () -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
) {
    val running = state.phase == GazeCalPhase.CALIBRATING || state.phase == GazeCalPhase.TESTING
    // Dot positions are absolute fractions of this area, independent of the RTL layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { lc ->
                val b = lc.boundsInRoot()
                onArea(b.left, b.top, b.width, b.height)
            }
        ) {
            val target = state.target
            if (running && target != null) {
                val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
                    0.6f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse",
                )
                val dot = 44.dp
                Box(
                    Modifier
                        .offset(x = maxWidth * target.x.toFloat() - dot / 2, y = maxHeight * target.y.toFloat() - dot / 2)
                        .size(dot).alpha(pulse).background(Color(0xFFFFD600), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(10.dp).background(Color.Black, CircleShape)) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (state.phase) {
                        GazeCalPhase.INTRO -> {
                            Big("کالیبراسیون نگاه")
                            Small(
                                "تبلت را ثابت و روبه‌روی صورت بیمار (۴۰ تا ۶۰ سانتی‌متر) بگذارید. نور صورت کافی باشد.\n" +
                                    "یک نقطه‌ی زرد در ۹ جای صفحه ظاهر می‌شود؛ بیمار فقط با چشم دنبالش می‌کند. " +
                                    "بعد ۵ نقطه‌ی دیگر دقت را می‌سنجد. حدود ۴۰ ثانیه طول می‌کشد."
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onStart) { Text("شروع", fontSize = 22.sp) }
                                OutlinedButton(onClick = onBack) { Text("بازگشت", fontSize = 22.sp, color = Color.White) }
                            }
                        }
                        GazeCalPhase.CALIBRATING, GazeCalPhase.TESTING -> if (target == null) {
                            Big(state.message)
                        }
                        GazeCalPhase.DONE -> {
                            val fa = Locale("fa")
                            Big("نتیجه")
                            Small(String.format(fa, "خطای میانگین: %.1f سانتی‌متر   لرزش: %.1f سانتی‌متر", state.errorCm, state.jitterCm))
                            Small("حداکثر گزینه‌ی قابل انتخاب مستقیم روی این صفحه: ${PersianText.toPersianDigits(state.maxCells)}")
                            Spacer(Modifier.height(8.dp))
                            Small(state.message)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onUse) { Text("استفاده از نگاه مستقیم", fontSize = 20.sp) }
                                OutlinedButton(onClick = onStart) { Text("دوباره", fontSize = 20.sp, color = Color.White) }
                                OutlinedButton(onClick = onBack) { Text("بازگشت", fontSize = 20.sp, color = Color.White) }
                            }
                        }
                        GazeCalPhase.FAILED -> {
                            Big("انجام نشد")
                            Small(state.message)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onStart) { Text("دوباره", fontSize = 22.sp) }
                                OutlinedButton(onClick = onBack) { Text("بازگشت", fontSize = 22.sp, color = Color.White) }
                            }
                        }
                    }
                }
            }
            if (running && target != null) {
                Text(
                    "${PersianText.toPersianDigits(state.step)} / ${PersianText.toPersianDigits(state.steps)}",
                    color = Color(0xFF757575), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun Big(text: String) =
    Text(text, color = Color.White, fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Small(text: String) =
    Text(text, color = Color(0xFFCFD8DC), fontSize = 20.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))

/** Small translucent dot at a window-normalised position. */
@Composable
fun GazeDot(x: Float, y: Float) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = 28.dp
            Box(
                Modifier.offset(x = maxWidth * x.coerceIn(0f, 1f) - d / 2, y = maxHeight * y.coerceIn(0f, 1f) - d / 2)
                    .size(d).background(Color(0x99FF4081), CircleShape)
            )
        }
    }
}
