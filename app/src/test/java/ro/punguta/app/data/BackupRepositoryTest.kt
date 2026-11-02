package ro.punguta.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.data.repo.BackupRepository
import ro.punguta.app.data.repo.BudgetRepository
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.data.repo.IncomeRepository
import ro.punguta.app.data.repo.RecurringRepository
import ro.punguta.app.data.repo.SettingsRepository
import ro.punguta.app.domain.backup.BackupCodec
import ro.punguta.app.domain.backup.BackupProblem
import ro.punguta.app.domain.backup.InvalidBackupException
import ro.punguta.app.domain.expense.ExpenseDraft
import ro.punguta.app.domain.expense.ItemDraft
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private val clock = { LocalDateTime.parse("2026-11-02T10:00:00") }

    @Before
    fun setUp() {
        db = testDb()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(target: AppDatabase) {
        val categories = CategoryRepository(target)
        val food = categories.all().first { it.defaultKey == "food" }.id
        val pets = categories.add("Animale")
        categories.learn("HRANA PISICI", pets)
        BudgetRepository(target).set(food, 150_000)
        IncomeRepository(target).save(IncomeEntity(name = "Proiect", amountBani = 120_050, date = LocalDate.parse("2026-10-15").toEpochDay(), note = "factura 12"))
        val recurring = RecurringRepository(target)
        recurring.save(
            RecurringTemplateEntity(
                kind = TemplateKind.INCOME, name = "Salariu", amountBani = 650_000, dayOfMonth = 31, categoryId = null,
                startDate = LocalDate.parse("2026-09-01").toEpochDay(), endDate = null, note = null,
            ),
        )
        recurring.generateDue(LocalDate.parse("2026-11-02"))
        ExpenseRepository(target, categories).save(
            ExpenseDraft(
                null, "Kaufland", LocalDate.parse("2026-11-01"), null, ExpenseSource.RECEIPT, 6797,
                listOf(
                    ItemDraft("LAPTE ZUZU", 2000, 550, 0, 1100, food),
                    ItemDraft("Hrană pisici", null, null, 50, 5697, pets),
                ),
            ),
        )
        SettingsRepository(target).setLockEnabled(true)
    }

    @Test
    fun `export then restore into an empty database reproduces everything`() = runTest {
        seed(db)
        val json = BackupRepository(db, clock).exportJson()
        assertTrue(json.contains("\"format\": \"punguta-cu-2-bani-backup\""))
        assertTrue(json.contains("\"amountBani\": 120050"))

        val other = testDb()
        try {
            val repo = BackupRepository(other, clock)
            val (file, summary) = repo.inspect(json)
            assertEquals(3, summary.incomes) // one-time + September and October salary
            assertEquals(1, summary.expenses)
            repo.restore(file)
            // Exporting the restored database gives the identical document.
            assertEquals(json, repo.exportJson())
            assertTrue(SettingsRepository(other).isLockEnabled())
            assertEquals(5697L, ExpenseRepository(other, CategoryRepository(other)).observeItems(YearMonth.of(2026, 11), YearMonth.of(2026, 11)).first().first { it.item.name == "Hrană pisici" }.item.amountBani)
            // Restored generation log keeps recurring entries from being duplicated.
            assertEquals(0, RecurringRepository(other).generateDue(LocalDate.parse("2026-11-02")))
        } finally {
            other.close()
        }
    }

    @Test
    fun `a failure during restore leaves existing data untouched`() = runTest {
        seed(db)
        val before = BackupRepository(db, clock).exportJson()
        val source = testDb()
        try {
            IncomeRepository(source).save(IncomeEntity(name = "Altceva", amountBani = 1, date = 0, note = null))
            val file = BackupCodec.decode(BackupRepository(source, clock).exportJson())
            val failing = BackupRepository(db, clock) { throw IllegalStateException("simulated crash mid-restore") }
            try {
                failing.restore(file)
                fail("Expected failure")
            } catch (e: IllegalStateException) {
                // expected
            }
            assertEquals(before, BackupRepository(db, clock).exportJson())
        } finally {
            source.close()
        }
    }

    @Test
    fun `invalid backups are rejected before anything changes`() = runTest {
        seed(db)
        val before = BackupRepository(db, clock).exportJson()
        val repo = BackupRepository(db, clock)
        expect(BackupProblem.NOT_JSON) { repo.inspect("{ not json") }
        expect(BackupProblem.NOT_A_BACKUP) { repo.inspect("""{"hello": 1}""") }
        expect(BackupProblem.NOT_A_BACKUP) { repo.inspect("[1,2,3]") }
        expect(BackupProblem.NEWER_VERSION) { repo.inspect(before.replace("\"version\": 1", "\"version\": 99")) }
        // Broken reference: an item pointing to a category that does not exist.
        expect(BackupProblem.INVALID_DATA) { repo.inspect(before.replace(Regex("\"categoryId\": (\\d+),\\s*\"position\""), "\"categoryId\": 9999, \"position\"")) }
        // Negative money.
        expect(BackupProblem.INVALID_DATA) { repo.inspect(before.replace("\"amountBani\": 120050", "\"amountBani\": -5")) }
        // Unknown field (tampered or different format).
        expect(BackupProblem.INVALID_DATA) { repo.inspect(before.replace("\"format\":", "\"extra\": 1, \"format\":")) }
        // Truncated file.
        expect(BackupProblem.NOT_JSON) { repo.inspect(before.substring(0, before.length / 2)) }
        assertEquals(before, repo.exportJson())
    }

    private fun expect(problem: BackupProblem, block: () -> Unit) {
        try {
            block()
            fail("Expected $problem")
        } catch (e: InvalidBackupException) {
            assertEquals(problem, e.problem)
        }
    }
}
