package ir.cheshmgoya.app.ui.screens

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import ir.cheshmgoya.app.data.CustomPhraseEntity
import ir.cheshmgoya.app.data.HistoryEntity
import ir.cheshmgoya.app.net.DiscoveredServer
import ir.cheshmgoya.app.net.Pairing
import ir.cheshmgoya.app.net.PairingInfo
import ir.cheshmgoya.app.tracking.EyeTracker
import ir.cheshmgoya.app.ui.CalibrationPhase
import ir.cheshmgoya.app.ui.CalibrationState
import ir.cheshmgoya.app.ui.DebugData
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- history

private val persianDate = SimpleDateFormat("yyyy/MM/dd  HH:mm", ULocale("fa_IR@calendar=persian"))

@Composable
fun HistoryScreen(entries: List<HistoryEntity>, onBack: () -> Unit, onClear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = { CompanionTopBar("تاریخچه‌ی پیام‌ها", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entries.size} پیام", modifier = Modifier.weight(1f))
                if (confirm) {
                    Button(onClick = { onClear(); confirm = false }) { Text("بله، پاک شود") }
                    TextButton(onClick = { confirm = false }) { Text("لغو") }
                } else OutlinedButton(onClick = { confirm = true }) { Text("پاک کردن تاریخچه") }
            }
            LazyColumn {
                items(entries, key = { it.id }) { h ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(h.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(persianDate.format(Date(h.timeMs)), fontSize = 14.sp, color = Color.Gray)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- my phrases

@Composable
fun MyPhrasesScreen(
    phrases: List<CustomPhraseEntity>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Boolean) -> Unit,
    exportJson: () -> String,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray()) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { onImport(it.readBytes().decodeToString()) }
    }
    Scaffold(topBar = { CompanionTopBar("عبارت‌های من", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, label = { Text("عبارت تازه، مثلاً «عینکم رو بدید»") }, modifier = Modifier.weight(1f))
                Button(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text("افزودن") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { exporter.launch("cheshm-goya-phrases.json") }) { Text("خروجی JSON") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("ورود از JSON (جایگزین می‌شود)") }
            }
            LazyColumn {
                itemsIndexed(phrases, key = { _, p -> p.id }) { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onMove(p.id, true) }, enabled = i > 0) { Text("▲") }
                        TextButton(onClick = { onMove(p.id, false) }, enabled = i < phrases.size - 1) { Text("▼") }
                        TextButton(onClick = { onDelete(p.id) }) { Text("حذف", color = Color(0xFFC62828)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- calibration

@Composable
fun CalibrationScreen(state: CalibrationState, onStart: () -> Unit, onDone: () -> Unit) {
    val bg = when (state.phase) {
        CalibrationPhase.OPEN -> Color(0xFF0D47A1)
        CalibrationPhase.CLOSED -> Color(0xFF1B1B1B)
        CalibrationPhase.DONE -> Color(0xFF1B5E20)
        CalibrationPhase.FAILED -> Color(0xFFB71C1C)
        else -> Color(0xFF263238)
    }
    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.phase == CalibrationPhase.OPEN || state.phase == CalibrationPhase.PREPARE_OPEN) {
            // The patient looks at this dot in the middle of the screen.
            Box(Modifier.size(56.dp).background(Color(0xFFFFD600), shape = androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.height(24.dp))
        }
        Text(
            when (state.phase) {
                CalibrationPhase.IDLE -> "کالیبراسیون: تبلت را روبه‌روی صورت بیمار بگذارید. راهنما با صدا گفته می‌شود:\n۴ ثانیه چشم باز و نگاه به وسط، سپس ۳ ثانیه چشم بسته."
                else -> state.message
            },
            color = Color.White, fontSize = 34.sp, lineHeight = 46.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
        )
        if (state.secondsLeft > 0) Text(ir.cheshmgoya.core.text.PersianText.toPersianDigits(state.secondsLeft), color = Color.White, fontSize = 96.sp)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.phase) {
                CalibrationPhase.IDLE, CalibrationPhase.FAILED -> Button(onClick = onStart) { Text(if (state.phase == CalibrationPhase.FAILED) "دوباره" else "شروع", fontSize = 22.sp) }
                else -> Unit
            }
            OutlinedButton(onClick = onDone) { Text(if (state.phase == CalibrationPhase.DONE) "تمام" else "بازگشت", color = Color.White, fontSize = 22.sp) }
        }
    }
}

// ---------------------------------------------------------------- debug

@Composable
fun DebugScreen(tracker: EyeTracker, data: DebugData, onBack: () -> Unit) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    DisposableEffect(Unit) {
        tracker.setPreview(previewView.surfaceProvider)
        onDispose { tracker.setPreview(null) }
    }
    Scaffold(topBar = { CompanionTopBar("عیب‌یابی (Debug)", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("تصویر فقط روی همین صفحه نمایش داده می‌شود و هیچ‌وقت ذخیره یا ارسال نمی‌شود.", fontSize = 14.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView({ previewView }, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    val fa = Locale("fa")
                    Text(if (data.face) "چهره: دیده می‌شود" else "چهره: دیده نمی‌شود", fontSize = 20.sp, color = if (data.face) Color(0xFF2E7D32) else Color(0xFFC62828))
                    Text(String.format(fa, "پلک چپ: %.2f   پلک راست: %.2f", data.left, data.right), fontSize = 18.sp)
                    Text(String.format(fa, "آستانه‌ی بسته: %.2f   باز: %.2f", data.closeThreshold, data.openThreshold), fontSize = 18.sp)
                    Text(String.format(fa, "فریم در ثانیه: %.0f", data.fps), fontSize = 18.sp)
                    Text("برای آزمایش، فقط یک چشم را ببندید تا ببینید کدام عدد بالا می‌رود.", fontSize = 14.sp, color = Color.Gray)
                }
            }
            Text("سیگنال پلک (زرد) و آستانه‌ها (قرمز: بسته، سبز: باز)", fontSize = 14.sp)
            SignalChart(data.signal, listOf(data.closeThreshold to Color.Red, data.openThreshold to Color(0xFF00C853)), 0f, 1f, Modifier.fillMaxWidth().height(160.dp))
            Text("نگاه افقی نسبت به مرکز (+ چپ / − راست)", fontSize = 14.sp)
            SignalChart(data.gaze, listOf(0.25f to Color.Gray, -0.25f to Color.Gray), -1f, 1f, Modifier.fillMaxWidth().height(100.dp))
        }
    }
}

@Composable
private fun SignalChart(values: List<Float>, lines: List<Pair<Float, Color>>, min: Float, max: Float, modifier: Modifier) {
    // Time runs left→right regardless of the RTL layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.background(Color(0xFF111111))) {
            fun y(v: Float) = size.height * (1f - ((v - min) / (max - min)).coerceIn(0f, 1f))
            for ((v, c) in lines) drawLine(c, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 3f)
            if (values.size < 2) return@Canvas
            val step = size.width / (240 - 1)
            val path = Path()
            val start = 240 - values.size
            values.forEachIndexed { i, v ->
                val x = (start + i) * step
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, Color(0xFFFFD600), style = Stroke(width = 4f))
        }
    }
}

// ---------------------------------------------------------------- pairing

@Composable
fun PairingScreen(
    qrCodes: SharedFlow<String>,
    discovered: List<DiscoveredServer>,
    onPaired: (PairingInfo) -> Unit,
    onManual: (url: String, token: String) -> Unit,
    onBack: () -> Unit,
) {
    var status by remember { mutableStateOf("کد QR صفحه‌ی سرور را جلوی دوربین جلوی تبلت بگیرید…") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(qrCodes) {
        qrCodes.collect { code ->
            val info = Pairing.parse(code)
            if (info != null) onPaired(info) else status = "این کد QR مربوط به سرور چشم‌گویا نیست."
        }
    }
    Scaffold(topBar = { CompanionTopBar("اتصال به سرور هوش مصنوعی", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("۱. روی کامپیوتر، صفحه‌ی سرور را باز کنید (http://آدرس-کامپیوتر:8765/pair).", fontSize = 18.sp)
                    Text("۲. کد QR را جلوی دوربین جلوی تبلت بگیرید.", fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(status, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            SectionTitle("سرورهای پیدا‌شده در شبکه (mDNS)")
            if (discovered.isEmpty()) Hint("در حال جست‌وجو…")
            discovered.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${d.name}  —  ${d.url}", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { url = d.url }) { Text("انتخاب") }
                }
            }
            SectionTitle("ورود دستی")
            OutlinedTextField(url, { url = it }, label = { Text("آدرس، مثلاً http://192.168.1.20:8765") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("توکن (روی صفحه‌ی سرور نوشته شده)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { scope.launch { onManual(url.trim(), token.trim()) } },
                enabled = url.startsWith("http") && token.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("ذخیره و اتصال") }
        }
    }
}
