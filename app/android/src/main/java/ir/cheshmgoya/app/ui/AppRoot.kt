package ir.cheshmgoya.app.ui

import androidx.compose.runtime.Composable
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

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as CheshmGoyaApp).container
    val context = LocalContext.current
    val installVoice = {
        runCatching { context.startActivity(container.speaker.installPersianVoiceIntent()) }
            .onFailure { runCatching { context.startActivity(container.speaker.ttsSettingsIntent()) } }
        Unit
    }

    CheshmGoyaTheme(dark = s.settings.darkTheme, highContrast = s.settings.highContrast, fontScale = s.settings.fontScale) {
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
            else -> PatientScreen(
                s = s,
                onTap = vm::onCellTapped,
                onMic = vm::toggleListening,
                onHistory = { vm.navigate(Screen.History) },
                onSettings = { vm.navigate(Screen.Settings) },
                onInstallVoice = installVoice,
                onDismissNotice = vm::dismissNotice,
            )
        }
    }
}
