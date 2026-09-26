package ir.cheshmgoya.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.cheshmgoya.app.CheshmGoyaApp
import ir.cheshmgoya.app.data.AppSettings
import ir.cheshmgoya.app.data.CustomPhraseEntity
import ir.cheshmgoya.app.data.HistoryEntity
import ir.cheshmgoya.app.data.InputMode
import ir.cheshmgoya.app.data.NgramStatEntity
import ir.cheshmgoya.app.data.PhrasesJson
import ir.cheshmgoya.app.data.SentenceStatEntity
import ir.cheshmgoya.app.data.WordStatEntity
import ir.cheshmgoya.app.net.PairingInfo
import ir.cheshmgoya.app.tracking.FaceFrame
import ir.cheshmgoya.core.ai.AiContext
import ir.cheshmgoya.core.ai.LocalServerProvider
import ir.cheshmgoya.core.ai.ProviderId
import ir.cheshmgoya.core.ai.ServerApi
import ir.cheshmgoya.core.blink.BlinkConfig
import ir.cheshmgoya.core.blink.BlinkDetector
import ir.cheshmgoya.core.blink.BlinkEvent
import ir.cheshmgoya.core.blink.Calibration
import ir.cheshmgoya.core.blink.CalibrationOutcome
import ir.cheshmgoya.core.blink.CalibrationSample
import ir.cheshmgoya.core.blink.RestWakeDetector
import ir.cheshmgoya.core.gaze.GazeConfig
import ir.cheshmgoya.core.gaze.GazeDetector
import ir.cheshmgoya.core.gaze.GazeDirection
import ir.cheshmgoya.core.keyboard.Key
import ir.cheshmgoya.core.keyboard.PersianKeyboard
import ir.cheshmgoya.core.phrases.CategoryId
import ir.cheshmgoya.core.phrases.PhraseBank
import ir.cheshmgoya.core.predict.LearnDelta
import ir.cheshmgoya.core.predict.NgramRow
import ir.cheshmgoya.core.predict.SentenceRow
import ir.cheshmgoya.core.predict.WordRow
import ir.cheshmgoya.core.scan.ScanConfig
import ir.cheshmgoya.core.scan.ScanLevel
import ir.cheshmgoya.core.scan.ScanMode
import ir.cheshmgoya.core.scan.ScanSelection
import ir.cheshmgoya.core.scan.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * The heart of the app. All scanning/blink state is confined to the main thread.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CheshmGoyaApp).container

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _debug = MutableStateFlow(DebugData())
    val debug: StateFlow<DebugData> = _debug.asStateFlow()

    val history = c.db.history().recent(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customPhrases = c.db.customPhrases().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val discoveredServers = c.discovery.servers

    private val blink = BlinkDetector()
    private val gaze = GazeDetector()
    private val scanner = Scanner()
    private val restWake = RestWakeDetector()
    private var settings = AppSettings()
    private var settingsLoaded = false

    private var homeSuggestions: List<String> = emptyList()
    private var wordSuggestions: List<String> = emptyList()
    private var sentenceSuggestions: List<String> = emptyList()
    private val recent = ArrayDeque<String>()

    private var suggestionJob: Job? = null
    private var homeJob: Job? = null
    private var messageJob: Job? = null
    private var calibrationJob: Job? = null
    private var listenJob: Job? = null

    private val openSamples = ArrayList<CalibrationSample>()
    private val closedSamples = ArrayList<CalibrationSample>()
    private val signalBuf = ArrayDeque<Float>()
    private val gazeBuf = ArrayDeque<Float>()
    private var fpsWindowStart = 0L
    private var fpsFrames = 0
    private var fps = 0f

    private fun now() = SystemClock.uptimeMillis()

    init {
        c.speaker.onCannotSpeak = { text ->
            viewModelScope.launch {
                _state.update { it.copy(bigMessage = text) }
                c.sounds.attention()
            }
        }
        viewModelScope.launch {
            c.settings.settings.collect { s -> applySettings(s) }
        }
        viewModelScope.launch { c.eyeTracker.frames.collect { onFrame(it) } }
        viewModelScope.launch { c.ai.status.collect { st -> _state.update { it.copy(connection = st) } } }
        viewModelScope.launch { c.speaker.state.collect { st -> _state.update { it.copy(tts = st) } } }
        viewModelScope.launch { c.eyeTracker.error.collect { e -> _state.update { it.copy(cameraError = e) } } }
        viewModelScope.launch { customPhrases.collect { if (_state.value.screen == Screen.Category(CategoryId.MY_PHRASES)) rebuildGrid(reset = true) } }
        viewModelScope.launch { restoreLanguageModel() }
        // Scan clock.
        viewModelScope.launch {
            while (true) {
                delay(40)
                tick()
            }
        }
        // Keep the connection indicator honest.
        viewModelScope.launch {
            while (true) {
                if (settings.aiProvider != ProviderId.OFF && settings.aiProvider != ProviderId.OFFLINE) {
                    val st = c.ai.ping()
                    if (st != ir.cheshmgoya.core.ai.ConnectionStatus.CONNECTED && settings.aiProvider == ProviderId.LOCAL_SERVER) tryRediscoverServer()
                }
                delay(30_000)
            }
        }
    }

    // ------------------------------------------------------------------ settings

    private fun applySettings(s: AppSettings) {
        val first = !settingsLoaded
        settings = s
        settingsLoaded = true
        blink.config = BlinkConfig(
            minClosedMs = s.blinkMinMs,
            maxClosedMs = s.blinkMaxMs.coerceAtLeast(s.blinkMinMs + 100),
            closeThreshold = s.closeThreshold,
            openThreshold = s.openThreshold.coerceAtMost(s.closeThreshold - 0.02f),
            smoothingAlpha = s.smoothing.coerceIn(0.05f, 1f),
            eyes = s.eyes,
        )
        gaze.config = GazeConfig(holdMs = s.gazeHoldMs, center = s.gazeCenter, inverted = s.gazeInverted)
        val mode = if (s.inputMode == InputMode.GAZE_BLINK || s.inputMode == InputMode.SWITCH_MANUAL) ScanMode.LINEAR else ScanMode.AUTO
        val newScan = ScanConfig(stepMs = s.scanStepMs, mode = mode)
        if (newScan != scanner.config || first) scanner.updateConfig(newScan, now())
        c.applyAiSettings(s)
        _state.update { it.copy(settings = s) }
        rebuildGrid(reset = first)
        resumeScanning()
        if (first) refreshHomeSuggestions()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    private val autoScan get() = settings.inputMode == InputMode.SCAN_BLINK || settings.inputMode == InputMode.SWITCH_SCAN
    private val usesCamera get() = settings.inputMode.usesCamera

    // ------------------------------------------------------------------ camera frames

    private fun onFrame(f: FaceFrame) {
        val t = f.timeMs
        countFps(t)
        val (l, r) = if (settings.swapEyes) f.blinkRight to f.blinkLeft else f.blinkLeft to f.blinkRight
        val horizontal = if (f.face) gaze.horizontal(f.lookOutLeft, f.lookInLeft, f.lookInRight, f.lookOutRight) else null

        val phase = _state.value.calibration.phase
        if (f.face && horizontal != null) {
            if (phase == CalibrationPhase.OPEN) openSamples += CalibrationSample(l, r, horizontal)
            if (phase == CalibrationPhase.CLOSED) closedSamples += CalibrationSample(l, r, horizontal)
        }

        val screen = _state.value.screen
        // Blinks drive the patient screens (and rest-mode wake-up) only in camera modes.
        val active = usesCamera && (screen.forPatient) && phase == CalibrationPhase.IDLE
        val events = blink.onSample(t, if (f.face) l else null, if (f.face) r else null)
        if (screen == Screen.Debug) pushDebug(f, l, r, horizontal)
        if (!active) return

        for (e in events) onBlinkEvent(e)
        if (settings.inputMode == InputMode.GAZE_BLINK && screen != Screen.Rest && screen != Screen.Emergency) {
            val closing = blink.isClosed || blink.lastSignal > blink.config.openThreshold
            when (gaze.onSample(t, horizontal, closing)) {
                GazeDirection.LEFT -> move(forward = true)   // in RTL, reading order runs leftwards
                GazeDirection.RIGHT -> move(forward = false)
                null -> Unit
            }
        }
    }

    private fun onBlinkEvent(e: BlinkEvent) {
        val screen = _state.value.screen
        when (e) {
            is BlinkEvent.Closed -> if (hasGrid(screen)) { scanner.freeze(e.atMs); publishHighlight() }
            is BlinkEvent.Ready -> if (screen != Screen.Emergency) c.sounds.ready()
            is BlinkEvent.Voluntary -> when (screen) {
                Screen.Rest -> {
                    val woke = restWake.onVoluntaryBlink(e.atMs)
                    if (woke) navigate(Screen.Home) else _state.update { it.copy(restBlinks = restWake.pending(e.atMs)) }
                }
                Screen.Emergency -> Unit // only the companion can stop the alarm
                else -> select(e.atMs)
            }
            is BlinkEvent.Ignored -> { scanner.unfreeze(e.atMs); publishHighlight() }
            is BlinkEvent.SleepStarted -> { _state.update { it.copy(sleeping = true) }; scanner.stop() }
            is BlinkEvent.SleepEnded -> { _state.update { it.copy(sleeping = false) }; scanner.unfreeze(e.atMs); resumeScanning() }
            is BlinkEvent.FaceLost -> { _state.update { it.copy(faceVisible = false) }; scanner.stop() }
            is BlinkEvent.FaceFound -> { _state.update { it.copy(faceVisible = true) }; resumeScanning() }
        }
    }

    // ------------------------------------------------------------------ scanning

    private fun hasGrid(s: Screen) = s.forPatient && s != Screen.Rest && s != Screen.Emergency

    private fun resumeScanning() {
        val st = _state.value
        val ok = autoScan && hasGrid(st.screen) && !st.sleeping && (!usesCamera || st.faceVisible) &&
            st.calibration.phase == CalibrationPhase.IDLE
        if (ok) { if (!scanner.running) scanner.start(now()) } else scanner.stop()
        publishHighlight()
    }

    private fun tick() {
        val t = now()
        if (scanner.tick(t)) {
            publishHighlight()
            if (settings.readAloud) readHighlight()
        }
        val p = if (usesCamera && _state.value.screen.forPatient) blink.progress else 0f
        if (p != _state.value.blinkProgress) _state.update { it.copy(blinkProgress = p) }
        val msg = _state.value
        if (msg.screen == Screen.Rest && msg.restBlinks != restWake.pending(t)) _state.update { it.copy(restBlinks = restWake.pending(t)) }
    }

    private fun publishHighlight() {
        val h = if (hasGrid(_state.value.screen)) scanner.highlight() else null
        if (h != _state.value.highlight) _state.update { it.copy(highlight = h) }
    }

    private fun readHighlight() {
        val h = scanner.highlight() ?: return
        val rows = _state.value.grid.rows
        val label = if (h.level == ScanLevel.ROWS) GridBuilder.rowLabel(rows.getOrNull(h.row).orEmpty())
        else rows.getOrNull(h.row)?.getOrNull(h.col)?.label.orEmpty()
        if (label.isNotBlank()) c.speaker.preview(label)
    }

    private fun move(forward: Boolean) {
        val t = now()
        if (forward) scanner.next(t) else scanner.previous(t)
        publishHighlight()
        if (settings.readAloud) readHighlight()
    }

    private fun select(t: Long) {
        if (!hasGrid(_state.value.screen)) return
        when (val sel = scanner.select(t)) {
            is ScanSelection.Item -> {
                val cell = _state.value.grid.rows.getOrNull(sel.row)?.getOrNull(sel.col)
                publishHighlight()
                if (cell != null && cell.action != CellAction.None) {
                    c.sounds.selected()
                    perform(cell.action)
                }
            }
            is ScanSelection.EnteredRow -> {
                publishHighlight()
                if (settings.readAloud) readHighlight()
            }
            ScanSelection.None -> Unit
        }
        resumeScanning()
    }

    /** Keys from a Bluetooth switch, keyboard or the volume buttons. Returns true if consumed. */
    fun onKey(keyCode: Int): Boolean {
        val screen = _state.value.screen
        if (!screen.forPatient) return false
        val t = now()
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_BUTTON_A -> {
                when (screen) {
                    Screen.Rest -> if (restWake.onVoluntaryBlink(t)) navigate(Screen.Home)
                    Screen.Emergency -> Unit
                    else -> select(t)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_TAB -> {
                if (hasGrid(screen)) move(forward = true); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> {
                if (hasGrid(screen)) move(forward = false); true
            }
            else -> false
        }
    }

    /** Direct touch on an option (companion or patients who can touch). */
    fun onCellTapped(row: Int, col: Int) {
        val cell = _state.value.grid.rows.getOrNull(row)?.getOrNull(col) ?: return
        scanner.moveTo(row, col, now())
        publishHighlight()
        if (cell.action != CellAction.None) perform(cell.action)
        scanner.restart(now())
        resumeScanning()
    }

    // ------------------------------------------------------------------ actions

    private fun perform(a: CellAction) {
        when (a) {
            is CellAction.Say -> speakMessage(a.text)
            is CellAction.Go -> navigate(a.screen)
            CellAction.GoHome -> navigate(Screen.Home)
            CellAction.Emergency -> navigate(Screen.Emergency)
            CellAction.Rest -> navigate(Screen.Rest)
            is CellAction.PainLevel -> speakMessage(PhraseBank.painLevelSentence(a.level))
            is CellAction.AcceptWord -> setComposing(PersianKeyboard.acceptWord(_state.value.composing, a.word))
            is CellAction.Type -> when (a.key) {
                Key.Speak -> {
                    val text = _state.value.composing.trim()
                    if (text.isNotEmpty()) {
                        speakMessage(text)
                        setComposing("")
                    }
                }
                Key.Home -> navigate(Screen.Home)
                else -> setComposing(PersianKeyboard.apply(_state.value.composing, a.key))
            }
            CellAction.None -> Unit
        }
    }

    fun navigate(screen: Screen) {
        val prev = _state.value.screen
        if (prev == Screen.Emergency && screen != Screen.Emergency) return // only stopEmergency() leaves
        if (prev == Screen.Debug && screen != Screen.Debug) c.eyeTracker.setPreview(null)
        if (prev == Screen.Pairing) { c.eyeTracker.qrMode = false; c.discovery.stop() }
        val keepQuestion = screen is Screen.Category || screen == Screen.YesNo || screen == Screen.Keyboard || screen == Screen.PainScale
        _state.update {
            it.copy(
                screen = screen,
                question = if (keepQuestion) it.question else null,
                restBlinks = 0,
            )
        }
        when (screen) {
            Screen.Rest -> { restWake.reset(); c.speaker.stop() }
            Screen.Emergency -> startEmergency()
            Screen.Home -> refreshHomeSuggestions()
            Screen.Keyboard -> refreshKeyboardSuggestions()
            Screen.Pairing -> { c.eyeTracker.qrMode = true; c.discovery.start() }
            else -> Unit
        }
        rebuildGrid(reset = true)
        resumeScanning()
    }

    private fun rebuildGrid(reset: Boolean) {
        val st = _state.value
        val grid = when (val s = st.screen) {
            Screen.Home -> GridBuilder.home(homeSuggestions)
            is Screen.Category -> GridBuilder.category(s.id, customPhrases.value.map { it.text })
            Screen.PainScale -> GridBuilder.painScale()
            Screen.YesNo -> GridBuilder.yesNo()
            Screen.Keyboard -> GridBuilder.keyboard(settings.keyboardLayout, wordSuggestions, sentenceSuggestions)
            else -> Grid.EMPTY
        }
        val sizesChanged = grid.rowSizes != st.grid.rowSizes
        _state.update { it.copy(grid = grid) }
        if (reset || sizesChanged) scanner.setLayout(grid.rowSizes, now())
        publishHighlight()
    }

    private fun speakMessage(text: String) {
        c.speaker.speak(text)
        recent.addLast(text)
        while (recent.size > 5) recent.removeFirst()
        messageJob?.cancel()
        _state.update { it.copy(lastSpoken = text) }
        messageJob = viewModelScope.launch {
            delay(6000)
            _state.update { it.copy(lastSpoken = null, bigMessage = null) }
        }
        val delta = c.languageModel.learn(text)
        viewModelScope.launch(Dispatchers.IO) {
            c.db.history().insert(HistoryEntity(text = text, timeMs = System.currentTimeMillis()))
            persist(delta)
            syncToServer()
        }
        if (settings.goHomeAfterSpeak && _state.value.screen != Screen.Keyboard && _state.value.screen != Screen.Home) {
            navigate(Screen.Home)
        } else if (_state.value.screen == Screen.Home) {
            refreshHomeSuggestions()
        }
    }

    // ------------------------------------------------------------------ emergency & rest

    private fun startEmergency() {
        scanner.stop()
        c.speaker.stop()
        c.sounds.startAlarm()
        if (settings.emergencySms && settings.emergencyPhone.isNotBlank()) sendEmergencySms(settings.emergencyPhone)
    }

    /** Called by the companion's long-press on the emergency screen. */
    fun stopEmergency() {
        c.sounds.stopAlarm()
        _state.update { it.copy(screen = Screen.Home) }
        navigate(Screen.Home)
    }

    private fun sendEmergencySms(phone: String) {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(notice = "اجازه‌ی ارسال پیامک داده نشده است.") }
            return
        }
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(phone, null, "چشم‌گویا: بیمار درخواست «کمک فوری» کرده است. لطفاً سریع رسیدگی کنید.", null, null)
        } catch (e: Exception) {
            Log.w("MainViewModel", "SMS failed", e)
            _state.update { it.copy(notice = "ارسال پیامک ناموفق بود.") }
        }
    }

    // ------------------------------------------------------------------ text & suggestions

    private fun setComposing(text: String) {
        _state.update { it.copy(composing = text) }
        refreshKeyboardSuggestions()
    }

    private fun context(typed: String) = AiContext(
        typed = typed,
        recent = recent.toList(),
        hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    )

    private fun refreshKeyboardSuggestions() {
        val text = _state.value.composing
        wordSuggestions = c.languageModel.suggestWords(text, 4)
        sentenceSuggestions = c.languageModel.suggestSentences(text, 3)
        rebuildGrid(reset = false)
        suggestionJob?.cancel()
        if (settings.aiProvider == ProviderId.OFF || settings.aiProvider == ProviderId.OFFLINE) return
        suggestionJob = viewModelScope.launch {
            delay(250) // wait for the patient to stop typing
            val ai = c.ai.complete(context(text))
            if (_state.value.composing == text && _state.value.screen == Screen.Keyboard) {
                sentenceSuggestions = ai
                rebuildGrid(reset = false)
            }
        }
    }

    private fun refreshHomeSuggestions() {
        homeJob?.cancel()
        if (settings.aiProvider == ProviderId.OFF) {
            if (homeSuggestions.isNotEmpty()) { homeSuggestions = emptyList(); if (_state.value.screen == Screen.Home) rebuildGrid(reset = false) }
            return
        }
        homeJob = viewModelScope.launch {
            val candidates = (customPhrases.value.map { it.text } + PhraseBank.needs + PhraseBank.pain + PhraseBank.feelings + PhraseBank.people).distinct()
            val s = c.ai.suggestionsFor(context(""), candidates, 4)
            if (s != homeSuggestions) {
                homeSuggestions = s
                if (_state.value.screen == Screen.Home) rebuildGrid(reset = false)
            }
        }
    }

    // ------------------------------------------------------------------ companion question (microphone)

    fun toggleListening() {
        if (_state.value.listening) {
            c.voiceInput.stop()
            return
        }
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(notice = "برای شنیدن سؤال، اجازه‌ی میکروفون لازم است.") }
            return
        }
        _state.update { it.copy(listening = true) }
        listenJob = viewModelScope.launch {
            val text = c.voiceInput.listen(c.serverClient)
            _state.update { it.copy(listening = false) }
            if (text.isNullOrBlank()) {
                _state.update { it.copy(notice = "سؤال شنیده نشد. دوباره امتحان کنید.") }
                return@launch
            }
            onCompanionQuestion(text)
        }
    }

    fun onCompanionQuestion(text: String) {
        viewModelScope.launch {
            _state.update { it.copy(question = text) }
            val q = c.ai.classify(text)
            val topic = when (q.topic) {
                "needs" -> CategoryId.NEEDS
                "pain" -> CategoryId.PAIN
                "feelings" -> CategoryId.FEELINGS
                "people" -> CategoryId.PEOPLE
                else -> null
            }
            val target = when {
                q.type == "yes_no" -> Screen.YesNo
                topic != null -> Screen.Category(topic)
                else -> Screen.Keyboard
            }
            if (_state.value.screen != Screen.Emergency) navigate(target)
            _state.update { it.copy(question = text) }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null, bigMessage = null) }

    // ------------------------------------------------------------------ calibration

    fun startCalibration() {
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            scanner.stop()
            openSamples.clear(); closedSamples.clear()
            fun set(p: CalibrationPhase, msg: String, sec: Int = 0) = _state.update { it.copy(calibration = CalibrationState(p, msg, sec)) }
            suspend fun countdown(p: CalibrationPhase, msg: String, ms: Long) {
                var left = ms
                while (left > 0) { set(p, msg, ((left + 999) / 1000).toInt()); delay(250); left -= 250 }
            }
            val openMsg = "چشم‌هایتان را باز نگه دارید و به وسط صفحه نگاه کنید."
            set(CalibrationPhase.PREPARE_OPEN, openMsg)
            c.speaker.speak(openMsg)
            delay(3500)
            countdown(CalibrationPhase.OPEN, openMsg, Calibration.OPEN_PHASE_MS)
            val closeMsg = "حالا چشم‌هایتان را ببندید و بسته نگه دارید."
            set(CalibrationPhase.PREPARE_CLOSED, closeMsg)
            c.speaker.speak(closeMsg)
            delay(3000)
            countdown(CalibrationPhase.CLOSED, closeMsg, Calibration.CLOSED_PHASE_MS)
            c.sounds.selected()
            when (val out = Calibration.compute(openSamples.toList(), closedSamples.toList(), settings.eyes)) {
                is CalibrationOutcome.Success -> {
                    val r = out.result
                    c.settings.update {
                        it.copy(closeThreshold = r.closeThreshold, openThreshold = r.openThreshold, gazeCenter = r.gazeCenter, calibrated = true)
                    }
                    val done = "تمام شد. می‌توانید چشم‌هایتان را باز کنید."
                    set(CalibrationPhase.DONE, done)
                    c.speaker.speak(done)
                }
                is CalibrationOutcome.Failure -> {
                    set(CalibrationPhase.FAILED, out.reasonFa)
                    c.speaker.speak("تنظیم انجام نشد. دوباره امتحان می‌کنیم.")
                }
            }
            blink.reset()
        }
    }

    fun finishCalibration() {
        calibrationJob?.cancel()
        _state.update { it.copy(calibration = CalibrationState()) }
        navigate(Screen.Home)
    }

    // ------------------------------------------------------------------ debug

    private fun countFps(t: Long) {
        fpsFrames++
        if (t - fpsWindowStart >= 1000) {
            fps = fpsFrames * 1000f / (t - fpsWindowStart).coerceAtLeast(1)
            fpsWindowStart = t
            fpsFrames = 0
        }
    }

    private fun pushDebug(f: FaceFrame, l: Float, r: Float, horizontal: Float?) {
        signalBuf.addLast(if (f.face) blink.lastSignal else 0f)
        gazeBuf.addLast(if (horizontal != null) horizontal - settings.gazeCenter else 0f)
        while (signalBuf.size > 240) signalBuf.removeFirst()
        while (gazeBuf.size > 240) gazeBuf.removeFirst()
        _debug.value = DebugData(
            signal = signalBuf.toList(),
            gaze = gazeBuf.toList(),
            left = l,
            right = r,
            closeThreshold = blink.config.closeThreshold,
            openThreshold = blink.config.openThreshold,
            face = f.face,
            fps = fps,
        )
    }

    // ------------------------------------------------------------------ my phrases

    fun addPhrase(text: String) {
        val t = ir.cheshmgoya.core.text.PersianText.normalize(text)
        if (t.isBlank()) return
        c.languageModel.seedSentence(t, 1.0) // the language model is only touched on the main thread
        viewModelScope.launch(Dispatchers.IO) {
            val pos = (c.db.customPhrases().allOnce().maxOfOrNull { it.position } ?: -1) + 1
            c.db.customPhrases().insert(CustomPhraseEntity(text = t, position = pos))
        }
    }

    fun deletePhrase(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.db.customPhrases().delete(id) }

    fun movePhrase(id: Long, up: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val list = c.db.customPhrases().allOnce().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@launch
        java.util.Collections.swap(list, i, j)
        c.db.customPhrases().upsertAll(list.mapIndexed { idx, e -> e.copy(position = idx) })
    }

    fun exportPhrases(): String = PhrasesJson.export(customPhrases.value.map { it.text })

    fun importPhrases(json: String) = viewModelScope.launch {
        try {
            val list = PhrasesJson.import(json)
            withContext(Dispatchers.IO) { c.db.customPhrases().replaceAll(list) }
            list.forEach { c.languageModel.seedSentence(it, 1.0) }
            _state.update { it.copy(notice = "${list.size} عبارت وارد شد.") }
        } catch (e: Exception) {
            _state.update { it.copy(notice = "فایل قابل خواندن نبود.") }
        }
    }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { c.db.history().clear() }

    // ------------------------------------------------------------------ server pairing & sync

    fun applyPairing(p: PairingInfo) {
        updateSettings { it.copy(serverUrl = p.url, serverToken = p.token, serverId = p.id, aiProvider = ProviderId.LOCAL_SERVER) }
        _state.update { it.copy(notice = "به سرور «${p.name.ifBlank { p.url }}» وصل شد.") }
        navigate(Screen.Settings)
    }

    private fun tryRediscoverServer() {
        if (settings.serverId.isBlank()) return
        c.discovery.start()
        viewModelScope.launch {
            delay(5000)
            val found = c.discovery.servers.value.firstOrNull { it.serverId == settings.serverId }
            if (_state.value.screen != Screen.Pairing) c.discovery.stop()
            if (found != null && found.url != settings.serverUrl) updateSettings { it.copy(serverUrl = found.url) }
        }
    }

    private suspend fun syncToServer() {
        if (!settings.syncSentences || settings.aiProvider != ProviderId.LOCAL_SERVER) return
        val provider = c.ai.provider as? LocalServerProvider ?: return
        val pending = c.db.history().unsynced()
        if (pending.isEmpty()) return
        try {
            provider.syncSentences(pending.map { ServerApi.SentenceItem(it.text, it.timeMs) })
            c.db.history().markSynced(pending.map { it.id })
        } catch (e: Exception) {
            // Server not reachable: try again after the next message.
        }
    }

    // ------------------------------------------------------------------ persistence of learned language

    private suspend fun persist(d: LearnDelta) {
        val dao = c.db.languageStats()
        dao.upsertWords(d.words.map { WordStatEntity(it.key, it.display, it.count, it.lastUsedMs) })
        dao.upsertNgrams(d.ngrams.map { NgramStatEntity(it.context, it.next, it.count, it.lastUsedMs) })
        dao.upsertSentences(d.sentences.map { SentenceStatEntity(it.key, it.text, it.count, it.lastUsedMs) })
    }

    private suspend fun restoreLanguageModel() {
        val dao = c.db.languageStats()
        val (w, n, s) = withContext(Dispatchers.IO) { Triple(dao.words(), dao.ngrams(), dao.sentences()) }
        c.languageModel.restore(
            w.map { WordRow(it.key, it.display, it.count, it.lastUsedMs) },
            n.map { NgramRow(it.context, it.next, it.count, it.lastUsedMs) },
            s.map { SentenceRow(it.key, it.text, it.count, it.lastUsedMs) },
        )
        val last = withContext(Dispatchers.IO) { c.db.history().recentOnce(5) }
        recent.clear()
        last.reversed().forEach { recent.addLast(it.text) }
    }

    override fun onCleared() {
        c.sounds.stopAlarm()
        super.onCleared()
    }
}
