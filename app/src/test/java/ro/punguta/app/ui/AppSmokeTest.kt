package ro.punguta.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ro.punguta.app.MainActivity
import ro.punguta.app.PungutaApp

/** Real Application, but with WorkManager in test mode (no startup provider under Robolectric). */
class TestPungutaApp : PungutaApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().build())
        super.onCreate()
    }
}

/**
 * Launches the real activity on the JVM and walks through the main screens, so crashes at
 * startup or while composing a screen are caught without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestPungutaApp::class)
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction()).performClick()

    private fun waitForText(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `every tab renders and a manual expense shows up on the dashboard`() {
        waitForText("Sold rămas")
        rule.onNodeWithText("Nimic înregistrat încă").assertExists()

        tab("Venituri")
        waitForText("Niciun venit în această lună")

        tab("Setări")
        waitForText("Exportă datele")
        rule.onNodeWithText("Datele există doar pe acest telefon").assertExists()
        rule.onNodeWithText("Categorii").performClick()
        waitForText("Îmbrăcăminte")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        tab("Cheltuieli")
        waitForText("Nicio cheltuială în această lună")
        rule.onNodeWithText("Scanează bon", useUnmergedTree = true).assertExists()

        // Quick manual expense: description and amount only.
        rule.onNode(hasContentDescription("Cheltuială nouă")).performClick()
        waitForText("Descriere")
        rule.onNode(hasSetTextAction() and hasText("Descriere")).performTextInput("Piață")
        rule.onNode(hasSetTextAction() and hasText("Sumă")).performTextInput("42,50")
        rule.onAllNodesWithText("Salvează").onFirst().performClick()

        rule.waitUntil(10_000) { rule.onAllNodesWithText("Descriere").fetchSemanticsNodes().isEmpty() }
        waitForText("Piață")
        rule.onAllNodesWithText("42,50 lei").onFirst().assertExists()

        tab("Panou")
        waitForText("Cheltuieli pe categorii")
        rule.onNodeWithText("Cheltuieli pe categorii").performScrollTo()
        rule.onAllNodesWithText("Altele").onFirst().assertExists()
    }
}
