package ir.cheshmgoya.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.unit.dp
import ir.cheshmgoya.app.data.AppSettings
import ir.cheshmgoya.app.data.InputMode
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.blink.EyeSelection
import ir.cheshmgoya.core.keyboard.KeyboardLayout
import ir.cheshmgoya.core.text.PersianText
import java.util.Locale

private fun sec(ms: Float) = String.format(Locale("fa"), "%.1f ثانیه", ms / 1000f)
private fun msText(ms: Float) = PersianText.toPersianDigits(ms.toInt()) + " میلی‌ثانیه"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    s: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    onDebug: () -> Unit,
    onHistory: () -> Unit,
    onMyPhrases: () -> Unit,
    onPairing: () -> Unit,
    onGazeCalibrate: () -> Unit,
    onInstallVoice: () -> Unit,
    onTestQuestion: () -> Unit,
) {
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { it.copy(emergencySms = granted) }
    }
    Scaffold(topBar = { CompanionTopBar("تنظیمات (همراه بیمار)", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCalibrate) { Text("کالیبراسیون پلک") }
                Button(onClick = onGazeCalibrate) { Text("کالیبراسیون نگاه (۹ نقطه)") }
                OutlinedButton(onClick = onDebug) { Text("صفحه‌ی عیب‌یابی (Debug)") }
                OutlinedButton(onClick = onHistory) { Text("تاریخچه‌ی پیام‌ها") }
                OutlinedButton(onClick = onMyPhrases) { Text("ویرایش «عبارت‌های من»") }
                OutlinedButton(onClick = onInstallVoice) { Text("نصب صدای فارسی") }
            }

            SectionTitle("روش ورودی")
            RadioGroup(InputMode.entries, s.inputMode, { it.titleFa }) { m -> update { it.copy(inputMode = m) } }
            Hint("کلیدها: Space/Enter یا کلید کم‌کردن صدا = انتخاب؛ کلیدهای جهت یا کلید زیاد‌کردن صدا = حرکت. لمس صفحه همیشه کار می‌کند.")
            SliderRow("سرعت اسکن", s.scanStepMs.toFloat(), 600f..5000f, ::sec) { v -> update { it.copy(scanStepMs = v.toLong()) } }
            SwitchRow("خواندن بلند گزینه‌ها هنگام اسکن (برای کم‌بینایی)", s.readAloud) { v -> update { it.copy(readAloud = v) } }
            SwitchRow("بعد از گفتن پیام، برگشت به صفحه‌ی اصلی", s.goHomeAfterSpeak) { v -> update { it.copy(goHomeAfterSpeak = v) } }

            SectionTitle("پلک")
            SliderRow("حداقل مدت پلک ارادی", s.blinkMinMs.toFloat(), 150f..1500f, ::msText) { v ->
                update { it.copy(blinkMinMs = v.toLong(), blinkMaxMs = maxOf(it.blinkMaxMs, v.toLong() + 300)) }
            }
            SliderRow("حداکثر مدت (بیشتر = خواب، انتخاب نمی‌شود)", s.blinkMaxMs.toFloat(), 800f..6000f, ::msText) { v ->
                update { it.copy(blinkMaxMs = maxOf(v.toLong(), it.blinkMinMs + 300)) }
            }
            Text("چشم مورد استفاده:")
            RadioGroup(EyeSelection.entries, s.eyes, {
                when (it) { EyeSelection.BOTH -> "هر دو چشم"; EyeSelection.LEFT -> "فقط چشم چپ"; EyeSelection.RIGHT -> "فقط چشم راست" }
            }) { e -> update { it.copy(eyes = e) } }
            SwitchRow("جابه‌جایی چپ و راست (اگر در صفحه‌ی عیب‌یابی برعکس بود)", s.swapEyes) { v -> update { it.copy(swapEyes = v) } }
            SliderRow("هموارسازی سیگنال (کمتر = نرم‌تر)", s.smoothing, 0.1f..1f, { String.format(Locale("fa"), "%.2f", it) }) { v -> update { it.copy(smoothing = v) } }
            SliderRow("آستانه‌ی بسته شدن", s.closeThreshold, 0.1f..0.95f, { String.format(Locale("fa"), "%.2f", it) }) { v ->
                update { it.copy(closeThreshold = v, openThreshold = minOf(it.openThreshold, v - 0.05f)) }
            }
            SliderRow("آستانه‌ی باز شدن (هیسترزیس)", s.openThreshold, 0.05f..0.9f, { String.format(Locale("fa"), "%.2f", it) }) { v ->
                update { it.copy(openThreshold = minOf(v, it.closeThreshold - 0.05f)) }
            }

            SectionTitle("حالت نگاه")
            SliderRow("مدت نگه‌داشتن نگاه", s.gazeHoldMs.toFloat(), 300f..2000f, ::msText) { v -> update { it.copy(gazeHoldMs = v.toLong()) } }
            SwitchRow("برعکس کردن جهت نگاه", s.gazeInverted) { v -> update { it.copy(gazeInverted = v) } }

            SectionTitle("نگاه مستقیم به گزینه")
            Hint(
                if (s.gazeModel.isBlank()) "هنوز کالیبراسیون نگاه انجام نشده."
                else String.format(Locale("fa"), "آخرین کالیبراسیون: خطای میانگین %.1f سانتی‌متر.", s.gazeErrorCm)
            )
            Hint("اگر صفحه‌ای بیشتر از این تعداد گزینه داشته باشد، گزینه‌ها گروه‌گروه و بزرگ نشان داده می‌شوند (انتخاب دومرحله‌ای). کالیبراسیون این عدد را بر اساس دقت اندازه‌گیری‌شده تنظیم می‌کند.")
            SliderRow("حداکثر گزینه‌ی مستقیم", s.gazeMaxCells.toFloat(), 2f..60f, { PersianText.toPersianDigits(it.toInt()) }) { v ->
                update { it.copy(gazeMaxCells = v.toInt()) }
            }
            SliderRow("مکث نگاه تا جابه‌جایی هایلایت", s.gazeDwellMs.toFloat(), 100f..1000f, ::msText) { v -> update { it.copy(gazeDwellMs = v.toLong()) } }
            SwitchRow("نمایش نقطه‌ی نگاه روی صفحه (برای تنظیم)", s.showGazeDot) { v -> update { it.copy(showGazeDot = v) } }
            Hint("اگر تبلت یا سر بیمار جابه‌جا شد، یا صفحه چرخید، کالیبراسیون نگاه را دوباره انجام دهید.")

            SectionTitle("نمایش")
            SliderRow("اندازه‌ی نوشته‌ها", s.fontScale, 0.7f..1.8f, { String.format(Locale("fa"), "%.0f٪", it * 100) }) { v -> update { it.copy(fontScale = v) } }
            SwitchRow("کنتراست بالا", s.highContrast) { v -> update { it.copy(highContrast = v) } }
            SwitchRow("حالت تاریک", s.darkTheme) { v -> update { it.copy(darkTheme = v) } }
            Text("چینش صفحه‌کلید:")
            RadioGroup(KeyboardLayout.entries, s.keyboardLayout, { it.titleFa }) { l -> update { it.copy(keyboardLayout = l) } }
            SwitchRow("قفل صفحه روی این برنامه (Screen Pinning)", s.lockTask) { v -> update { it.copy(lockTask = v) } }

            SectionTitle("کمک فوری")
            TextSetting("شماره‌ی تلفن برای پیامک اضطراری", s.emergencyPhone, phone = true) { v -> update { it.copy(emergencyPhone = v) } }
            SwitchRow("ارسال پیامک هنگام «کمک فوری»", s.emergencySms) { v ->
                if (v) smsPermission.launch(Manifest.permission.SEND_SMS) else update { it.copy(emergencySms = false) }
            }
            Hint("آژیر روی کانال هشدار پخش می‌شود و در حالت بی‌صدا هم شنیده می‌شود. فقط همراه بیمار با نگه‌داشتن دکمه می‌تواند آن را قطع کند.")

            SectionTitle("هوش مصنوعی (اختیاری)")
            Hint("پیش‌بینی آفلاین همیشه فعال است. هر قابلیت هوش مصنوعی یک لایه‌ی اضافه است؛ اگر سرویس در دسترس نباشد، برنامه بی‌صدا به حالت آفلاین برمی‌گردد.")
            RadioGroup(ProviderId.entries, s.aiProvider, { it.titleFa }) { p -> update { it.copy(aiProvider = p) } }
            PrivacyCard(s.aiProvider)
            when (s.aiProvider) {
                ProviderId.LOCAL_SERVER -> {
                    Button(onClick = onPairing, modifier = Modifier.padding(vertical = 6.dp)) { Text("اتصال به سرور (اسکن QR یا جست‌وجو در شبکه)") }
                    TextSetting("آدرس سرور", s.serverUrl) { v -> update { it.copy(serverUrl = v) } }
                    TextSetting("توکن", s.serverToken, secret = true) { v -> update { it.copy(serverToken = v) } }
                    SwitchRow("پخش صدا با سرور (صدای طبیعی‌تر یا صدای شخصی)", s.serverTts) { v -> update { it.copy(serverTts = v) } }
                    TextSetting("نام صدا در سرور", s.serverVoice) { v -> update { it.copy(serverVoice = v.ifBlank { "default" }) } }
                    SwitchRow("اجازه: جمله‌های بیمار برای بهتر شدن پیشنهادها به سرور خودمان فرستاده شود", s.syncSentences) { v -> update { it.copy(syncSentences = v) } }
                }
                ProviderId.JEV -> TextSetting("کلید API مدل Jev (TypeSafe AI)", s.jevApiKey, secret = true) { v -> update { it.copy(jevApiKey = v) } }
                ProviderId.CLAUDE -> {
                    TextSetting("کلید API Claude", s.claudeApiKey, secret = true) { v -> update { it.copy(claudeApiKey = v) } }
                    TextSetting("مدل Claude", s.claudeModel) { v -> update { it.copy(claudeModel = v.ifBlank { "claude-opus-5" }) } }
                }
                else -> Unit
            }
            if (s.aiProvider != ProviderId.OFF) {
                SliderRow("حداقل اطمینان برای ردیف «پیشنهادها»", s.rankThreshold, 0.1f..0.9f, { String.format(Locale("fa"), "%.0f٪", it * 100) }) { v ->
                    update { it.copy(rankThreshold = v) }
                }
            }
            OutlinedButton(onClick = onTestQuestion, modifier = Modifier.padding(vertical = 6.dp)) { Text("آزمایش فهم سؤال (با میکروفون)") }
            Text(" ", modifier = Modifier.padding(bottom = 48.dp))
        }
    }
}

@Composable
private fun PrivacyCard(p: ProviderId) {
    val text = when (p) {
        ProviderId.OFF -> "هوش مصنوعی خاموش است. هیچ داده‌ای از دستگاه خارج نمی‌شود."
        ProviderId.OFFLINE -> "همه‌چیز روی همین تبلت انجام می‌شود. هیچ داده‌ای از دستگاه خارج نمی‌شود."
        ProviderId.LOCAL_SERVER -> "اطلاعات فقط به کامپیوتر خودمان در شبکه‌ی خانه یا بیمارستان (یا از طریق Tailscale) فرستاده می‌شود و به هیچ سرویس اینترنتی نمی‌رود."
        ProviderId.JEV -> "توجه: با فعال کردن Jev، پیام‌های اخیر بیمار و متن تایپ‌شده از طریق اینترنت به سرورهای شرکت TypeSafe AI فرستاده می‌شود."
        ProviderId.CLAUDE -> "توجه: با فعال کردن Claude، پیام‌های اخیر بیمار و متن تایپ‌شده از طریق اینترنت به سرورهای شرکت Anthropic فرستاده می‌شود."
    }
    Card(Modifier.padding(vertical = 8.dp)) { Text(text, modifier = Modifier.padding(12.dp)) }
}
