package ro.punguta.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ro.punguta.app.MainActivity
import ro.punguta.app.PungutaApp
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.domain.expense.ExpenseDraft
import ro.punguta.app.domain.expense.ItemDraft
import java.io.File
import java.time.LocalDate

/**
 * Renders the main screens with realistic demo data into app/build/screenshots/ for visual
 * review. Not an assertion test: it fails only if a screen cannot be composed or captured.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestPungutaApp::class, qualifiers = "w393dp-h852dp-xhdpi")
open class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    protected open val suffix = "light"

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name-$suffix.png").outputStream().use {
            windowBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Draws the whole window, including the floating bar, straight from the view tree. */
    private fun windowBitmap(): Bitmap {
        var bitmap: Bitmap? = null
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        return bitmap!!
    }

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction()).performClick()

    private fun waitForText(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun seed() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<PungutaApp>().container
        val cats = c.categories.all().associateBy { it.defaultKey }
        fun cat(key: String) = cats.getValue(key).id
        val today = LocalDate.now()
        val thisMonth = today.withDayOfMonth(1)
        val lastMonth = thisMonth.minusMonths(1)

        c.recurring.save(
            RecurringTemplateEntity(
                kind = TemplateKind.INCOME, name = "Salariu", amountBani = 650_000, dayOfMonth = 1, categoryId = null,
                startDate = lastMonth.minusMonths(4).toEpochDay(), endDate = null, note = null,
            ),
        )
        c.recurring.save(
            RecurringTemplateEntity(
                kind = TemplateKind.EXPENSE, name = "Chirie", amountBani = 220_000, dayOfMonth = 1, categoryId = cat("bills"),
                startDate = lastMonth.minusMonths(4).toEpochDay(), endDate = null, note = null,
            ),
        )
        c.recurring.generateDue(today)
        c.incomes.save(IncomeEntity(name = "Proiect logo", amountBani = 120_050, date = thisMonth.toEpochDay(), note = "factură 14"))
        c.budgets.set(cat("food"), 80_000)
        c.budgets.set(cat("entertainment"), 15_000)
        c.expenses.save(
            ExpenseDraft(
                null, "Kaufland", thisMonth, null, ExpenseSource.RECEIPT, 34_896,
                listOf(
                    ItemDraft("LAPTE ZUZU 1,5% 1L", 2000, 550, 0, 1_100, cat("food")),
                    ItemDraft("PIEPT PUI", 1_250, 2_999, 0, 3_749, cat("food")),
                    ItemDraft("BRÂNZĂ TELEMEA", null, null, 0, 2_850, cat("food")),
                    ItemDraft("BERE URSUS 6X0,5L", null, null, 300, 2_697, cat("drinks")),
                    ItemDraft("DETERGENT ARIEL 2KG", null, null, 0, 4_599, cat("home")),
                    ItemDraft("CUMPĂRĂTURI SĂPTĂMÂNA", null, null, 0, 19_901, cat("food")),
                ),
            ),
        )
        c.expenses.save(
            ExpenseDraft(null, "Petrom", thisMonth, null, ExpenseSource.RECEIPT, 24_880,
                listOf(ItemDraft("MOTORINA STANDARD", 32_150, 749, 0, 24_080, cat("transport")), ItemDraft("CAFEA", null, null, 0, 800, cat("drinks")))),
        )
        c.expenses.save(
            ExpenseDraft(null, "Cinema", thisMonth, null, ExpenseSource.MANUAL, null,
                listOf(ItemDraft("Bilete film", null, null, 0, 16_400, cat("entertainment")))),
        )
        c.expenses.save(
            ExpenseDraft(null, "Mega Image", lastMonth, null, ExpenseSource.RECEIPT, 41_000,
                listOf(ItemDraft("Cumpărături", null, null, 0, 41_000, cat("food")))),
        )
    }

    @Test
    fun screens() {
        seed()
        waitForText("Sold rămas")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Cheltuieli pe categorii").fetchSemanticsNodes().isNotEmpty() }
        shot("1-dashboard")
        rule.onAllNodesWithText("Cheltuieli pe categorii")[0].performScrollTo()
        shot("2-dashboard-categories")
        rule.onAllNodesWithText("Evoluție în ultimele 6 luni")[0].performScrollTo()
        shot("3-dashboard-trend")

        tab("Venituri")
        waitForText("Salariu")
        shot("4-incomes")

        tab("Cheltuieli")
        waitForText("Kaufland")
        shot("5-expenses")
        rule.onAllNodesWithText("Kaufland")[0].performClick()
        waitForText("Articole (6)")
        shot("6-expense-editor")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        tab("Setări")
        waitForText("Exportă datele")
        shot("7-settings")
    }
}

@Config(application = TestPungutaApp::class, qualifiers = "w393dp-h852dp-night-xhdpi")
class ScreenshotDarkTest : ScreenshotTest() {
    override val suffix = "dark"
}
