package ir.cheshmgoya.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import ir.cheshmgoya.app.ui.AppRoot
import ir.cheshmgoya.app.ui.MainViewModel
import ir.cheshmgoya.app.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens every patient screen through the real UI to catch crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = CheshmGoyaApp::class)
class ScreensSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun start(): MainViewModel {
        val vm = ViewModelProvider(rule.activity)[MainViewModel::class.java]
        rule.setContent { AppRoot(vm) }
        rule.waitForIdle()
        return vm
    }

    @Test fun keyboardOpensFromHome() {
        val vm = start()
        rule.onNodeWithText("نوشتن").performClick()
        rule.waitForIdle()
        assertEquals(Screen.Keyboard, vm.state.value.screen)
        rule.onNodeWithText("فاصله").assertExists()
        // type a letter and a word suggestion
        rule.onAllNodesWithText("س")[0].performClick()
        rule.waitForIdle()
        assertTrue(vm.state.value.composing.startsWith("س"))
        rule.onNodeWithText("خانه").performClick()
        rule.waitForIdle()
        assertEquals(Screen.Home, vm.state.value.screen)
    }

    @Test fun everyPatientScreenRenders() {
        val vm = start()
        for (label in listOf("نیازها", "درد و بدن", "احساسات", "افراد و درخواست‌ها", "عبارت‌های من")) {
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
            rule.onAllNodesWithText("خانه")[0].performClick()
            rule.waitForIdle()
            assertEquals(Screen.Home, vm.state.value.screen)
        }
        rule.onNodeWithText("استراحت").performClick()
        rule.waitForIdle()
        assertEquals(Screen.Rest, vm.state.value.screen)
    }

    @Test fun companionScreensRender() {
        val vm = start()
        for (s in listOf(Screen.Settings, Screen.History, Screen.MyPhrasesEditor, Screen.Calibration, Screen.VoiceHelp, Screen.GazeCalibration)) {
            rule.runOnUiThread { vm.navigate(s) }
            rule.waitForIdle()
            assertEquals(s, vm.state.value.screen)
        }
    }
}
