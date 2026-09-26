package ir.cheshmgoya.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheshmgoya.app.speech.TtsState

const val SHERPA_TTS_FDROID = "https://f-droid.org/packages/org.woheller69.ttsengine/"
const val SHERPA_ONNX_TTS_APKS = "https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html"

/**
 * Google's speech engine has no Persian voice, so its "install voice data" screen
 * never lists Persian. This screen explains the free offline alternatives.
 */
@Composable
fun VoiceHelpScreen(
    tts: TtsState,
    engineLabel: String?,
    onOpenTtsSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRecheck: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { CompanionTopBar("صدای فارسی", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.padding(bottom = 12.dp)) {
                Text(
                    when (tts) {
                        TtsState.READY -> "✅ صدای فارسی فعال است" + (engineLabel?.let { " (موتور: $it)" } ?: "")
                        TtsState.INITIALIZING -> "در حال بررسی موتورهای صدا…"
                        else -> "❌ هیچ موتور صدای نصب‌شده‌ای فارسی بلد نیست."
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                "موتور صدای گوگل (Speech Services) زبان فارسی ندارد؛ برای همین در فهرست دانلود آن فارسی دیده نمی‌شود. " +
                    "باید یک موتور صدای دیگر که فارسی دارد نصب کنید. چشم‌گویا خودش همه‌ی موتورهای نصب‌شده را می‌گردد " +
                    "و آنکه فارسی دارد را انتخاب می‌کند؛ لازم نیست موتور پیش‌فرض گوشی را عوض کنید.",
                fontSize = 18.sp,
            )
            SectionTitle("راه ۱ (پیشنهادی): SherpaTTS — رایگان، آفلاین، صدای طبیعی Piper")
            Hint("از F-Droid نصب کنید، برنامه را یک بار باز کنید و صدای فارسی (fa) را دانلود کنید.")
            Button(onClick = { onOpenLink(SHERPA_TTS_FDROID) }) { Text("صفحه‌ی SherpaTTS در F-Droid") }
            SectionTitle("راه ۲: موتور صدای sherpa-onnx مخصوص فارسی")
            Hint("در صفحه‌ی زیر، فایل APK با زبان «fa» (مثلاً صدای fa_IR-amir یا ganji) و معماری arm64-v8a را دانلود و نصب کنید.")
            OutlinedButton(onClick = { onOpenLink(SHERPA_ONNX_TTS_APKS) }) { Text("فهرست APKهای موتور صدای sherpa-onnx") }
            SectionTitle("راه ۳: صدای سرور خودمان")
            Hint("اگر سرور هوش مصنوعی را راه انداخته‌اید، در تنظیمات «پخش صدا با سرور» را روشن کنید.")
            SectionTitle("بعد از نصب")
            OutlinedButton(onClick = onOpenTtsSettings) { Text("تنظیمات متن به گفتار اندروید") }
            Button(onClick = onRecheck, modifier = Modifier.padding(top = 8.dp)) { Text("دوباره بررسی کن") }
            Hint("تا وقتی صدای فارسی نصب نشده، پیام‌ها با حروف بزرگ روی صفحه نمایش داده می‌شوند و بوق زده می‌شود.")
        }
    }
}
