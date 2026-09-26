package ir.cheshmgoya.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheshmgoya.app.CheshmGoyaApp
import ir.cheshmgoya.app.net.PairingInfo
import ir.cheshmgoya.app.ui.screens.CalibrationScreen
import ir.cheshmgoya.app.ui.screens.DebugScreen
import ir.cheshmgoya.app.ui.screens.EmergencyScreen
import ir.cheshmgoya.app.ui.screens.HistoryScreen
import ir.cheshmgoya.app.ui.screens.MyPhrasesScreen
import ir.cheshmgoya.app.ui.screens.PairingScreen
import ir.cheshmgoya.app.ui.screens.PatientScreen
import ir.cheshmgoya.app.ui.screens.RestScreen
import ir.cheshmgoya.app.ui.screens.SettingsScreen
import ir.cheshmgoya.app.ui.screens.VoiceHelpScreen
import ir.cheshmgoya.app.ui.screens.GazeCalibrationScreen
import ir.cheshmgoya.app.ui.screens.GazeDot

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as CheshmGoyaApp).container
    val context = LocalContext.current
    val installVoice = { vm.navigate(Screen.VoiceHelp) }

    CheshmGoyaTheme(dark = s.settings.darkTheme, highContrast = s.settings.highContrast, fontScale = s.settings.fontScale) {
      Box(Modifier.fillMaxSize().onGloballyPositioned { vm.onRootSize(it.size.width.toFloat(), it.size.height.toFloat()) }) {
      // Android 15 draws apps edge-to-edge: keep everything clear of the status and navigation bars.
      Box(Modifier.fillMaxSize().background(LocalAacColors.current.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (s.screen) {
            Screen.Emergency -> EmergencyScreen(
                smsSent = s.settings.emergencySms && s.settings.emergencyPhone.isNotBlank(),
                onStop = vm::stopEmergency,
            )
            Screen.Rest -> RestScreen(s.restBlinks, s.settings.inputMode.usesCamera) { vm.navigate(Screen.Home) }
            Screen.Settings -> SettingsScreen(
                s = s.settings,
                update = vm::updateSettings,
                onBack = { vm.navigate(Screen.Home) },
                onCalibrate = { vm.navigate(Screen.Calibration) },
                onDebug = { vm.navigate(Screen.Debug) },
                onHistory = { vm.navigate(Screen.History) },
                onMyPhrases = { vm.navigate(Screen.MyPhrasesEditor) },
                onPairing = { vm.navigate(Screen.Pairing) },
                onGazeCalibrate = { vm.navigate(Screen.GazeCalibration) },
                onInstallVoice = installVoice,
                onTestQuestion = vm::toggleListening,
            )
            Screen.History -> {
                val items by vm.history.collectAsStateWithLifecycle()
                HistoryScreen(items, onBack = { vm.navigate(Screen.Home) }, onClear = { vm.clearHistory() })
            }
            Screen.MyPhrasesEditor -> {
                val phrases by vm.customPhrases.collectAsStateWithLifecycle()
                MyPhrasesScreen(
                    phrases,
                    onBack = { vm.navigate(Screen.Settings) },
                    onAdd = vm::addPhrase,
                    onDelete = { vm.deletePhrase(it) },
                    onMove = { id, up -> vm.movePhrase(id, up) },
                    exportJson = vm::exportPhrases,
                    onImport = { vm.importPhrases(it) },
                )
            }
            Screen.Calibration -> CalibrationScreen(s.calibration, onStart = vm::startCalibration, onDone = vm::finishCalibration)
            Screen.Debug -> {
                val d by vm.debug.collectAsStateWithLifecycle()
                DebugScreen(container.eyeTracker, d, onBack = { vm.navigate(Screen.Settings) })
            }
            Screen.Pairing -> {
                val found by vm.discoveredServers.collectAsStateWithLifecycle()
                PairingScreen(
                    qrCodes = container.eyeTracker.qrCodes,
                    discovered = found,
                    onPaired = vm::applyPairing,
                    onManual = { url, token -> vm.applyPairing(PairingInfo(url = url, token = token)) },
                    onBack = { vm.navigate(Screen.Settings) },
                )
            }
            Screen.VoiceHelp -> {
                val engine by container.speaker.engineLabel.collectAsStateWithLifecycle()
                VoiceHelpScreen(
                    tts = s.tts,
                    engineLabel = engine,
                    onOpenTtsSettings = { runCatching { context.startActivity(container.speaker.ttsSettingsIntent()) } },
                    onOpenLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onRecheck = { container.speaker.recheck() },
                    onBack = { vm.navigate(Screen.Settings) },
                )
            }
            Screen.GazeCalibration -> GazeCalibrationScreen(
                state = s.gazeCalibration,
                onArea = vm::onGazeCalibrationArea,
                onStart = vm::startGazeCalibration,
                onUse = vm::useDirectGaze,
                onBack = vm::finishGazeCalibration,
            )
            else -> PatientScreen(
                s = s,
                onTap = vm::onCellTapped,
                onMic = vm::toggleListening,
                onHistory = { vm.navigate(Screen.History) },
                onSettings = { vm.navigate(Screen.Settings) },
                onInstallVoice = installVoice,
                onDismissNotice = vm::dismissNotice,
                onCellBounds = vm::onCellBounds,
                onGazeCalibrate = { vm.navigate(Screen.GazeCalibration) },
            )
        }
      }
      // Optional dot where the app thinks the patient is looking (window coordinates).
      val gp = s.gazePoint
      if (s.settings.showGazeDot && gp != null && s.screen.forPatient) GazeDot(gp.x.toFloat(), gp.y.toFloat())
      }
    }
}
