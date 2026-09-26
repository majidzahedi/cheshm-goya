package ir.cheshmgoya.app

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ir.cheshmgoya.app.ui.AppRoot
import ir.cheshmgoya.app.ui.MainViewModel
import ir.cheshmgoya.app.ui.Screen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val container get() = (application as CheshmGoyaApp).container

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) container.eyeTracker.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A bedside communication device must never go dark on its own.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { AppRoot(vm) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back never leaves the app: companion screens go home, patient screens ignore it.
                val screen = vm.state.value.screen
                if (!screen.forPatient) vm.navigate(if (screen == Screen.Settings) Screen.Home else Screen.Settings)
                else if (screen != Screen.Home && screen != Screen.Emergency) vm.navigate(Screen.Home)
            }
        })

        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) container.eyeTracker.start(this) else permissions.launch(needed.toTypedArray())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.state.map { it.settings.lockTask }.distinctUntilChanged().collect { applyLockTask(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.speaker.recheck() // the family may just have installed a Persian voice
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            // Holding a switch must not repeat selections (nor change the volume on patient screens).
            val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (volume && vm.state.value.screen.forPatient) return true
            return super.onKeyDown(keyCode, event)
        }
        return vm.onKey(keyCode) || super.onKeyDown(keyCode, event)
    }

    private fun applyLockTask(enabled: Boolean) {
        val am = getSystemService(ActivityManager::class.java)
        val pinned = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        try {
            // Without device-owner provisioning Android asks once to confirm screen pinning.
            if (enabled && !pinned) startLockTask()
            if (!enabled && pinned) stopLockTask()
        } catch (e: Exception) {
            Log.w("MainActivity", "lock task", e)
        }
    }

    override fun onDestroy() {
        if (isFinishing) container.eyeTracker.close()
        super.onDestroy()
    }
}
