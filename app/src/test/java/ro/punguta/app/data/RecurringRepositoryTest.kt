package ro.punguta.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.data.repo.IncomeRepository
import ro.punguta.app.data.repo.RecurringRepository
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var recurring: RecurringRepository
    private lateinit var incomes: IncomeRepository
    private lateinit var expenses: ExpenseRepository

    @Before
    fun setUp() {
        db = testDb()
        recurring = RecurringRepository(db)
        incomes = IncomeRepository(db)
        expenses = ExpenseRepository(db, CategoryRepository(db))
    }

    @After
    fun tearDown() = db.close()

    private fun d(s: String) = LocalDate.parse(s)

    private suspend fun salary(day: Int = 10, start: String = "2026-01-01", amount: Long = 500_000) =
        recurring.save(
            RecurringTemplateEntity(
                kind = TemplateKind.INCOME, name = "Salariu", amountBani = amount, dayOfMonth = day,
                categoryId = null, startDate = d(start).toEpochDay(), endDate = null, note = null,
            ),
        )

    private suspend fun allIncomes(): List<IncomeEntity> =
        incomes.observeRange(YearMonth.of(2025, 1), YearMonth.of(2027, 12)).first().sortedBy { it.date }

    @Test
    fun `reruns never duplicate entries`() = runTest {
        salary()
        assertEquals(3, recurring.generateDue(d("2026-03-15")))
        assertEquals(0, recurring.generateDue(d("2026-03-15")))
        assertEquals(0, recurring.generateDue(d("2026-03-20")))
        assertEquals(3, allIncomes().size)
    }

    @Test
    fun `concurrent runs never duplicate entries`() = runTest {
        salary()
        val created = (1..5).map { async { recurring.generateDue(d("2026-04-30")) } }.awaitAll()
        assertEquals(4, created.sum())
        assertEquals(4, allIncomes().size)
    }

    @Test
    fun `missed months are caught up after a long gap`() = runTest {
        salary(day = 31)
        recurring.generateDue(d("2026-01-31"))
        // Phone off from February to May.
        assertEquals(4, recurring.generateDue(d("2026-05-31")))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31").map(::d),
            allIncomes().map { LocalDate.ofEpochDay(it.date) },
        )
    }

    @Test
    fun `a deleted generated entry is not recreated`() = runTest {
        salary()
        recurring.generateDue(d("2026-02-15"))
        val feb = allIncomes().last()
        incomes.delete(feb.id)
        recurring.generateDue(d("2026-02-20"))
        assertEquals(1, allIncomes().size)
    }

    @Test
    fun `editing one month keeps the template and other months`() = runTest {
        val id = salary()
        recurring.generateDue(d("2026-02-15"))
        val jan = allIncomes().first()
        incomes.save(jan.copy(amountBani = 550_000, templateId = null, period = null))

        val after = allIncomes()
        assertEquals(550_000L, after[0].amountBani)
        assertEquals(id, after[0].templateId) // link kept, so it is never regenerated
        assertEquals(500_000L, after[1].amountBani)
        assertEquals(500_000L, recurring.get(id)!!.amountBani)
        assertEquals(0, recurring.generateDue(d("2026-02-28")))
    }

    @Test
    fun `editing a template affects only future months`() = runTest {
        val id = salary()
        recurring.generateDue(d("2026-02-15"))
        val t = recurring.get(id)!!
        recurring.save(t.copy(amountBani = 600_000, startDate = d("2025-01-01").toEpochDay()))
        recurring.generateDue(d("2026-03-15"))
        assertEquals(listOf(500_000L, 500_000L, 600_000L), allIncomes().map { it.amountBani })
        // The start date cannot be moved back to backfill months.
        assertEquals(d("2026-01-01").toEpochDay(), recurring.get(id)!!.startDate)
    }

    @Test
    fun `stopping keeps past entries and stops new ones`() = runTest {
        val id = salary()
        recurring.generateDue(d("2026-02-15"))
        recurring.stop(id, d("2026-02-20"))
        assertEquals(0, recurring.generateDue(d("2026-06-15")))
        assertEquals(2, allIncomes().size)
    }

    @Test
    fun `resuming does not backfill the stopped months`() = runTest {
        val id = salary()
        recurring.generateDue(d("2026-01-15"))
        recurring.stop(id, d("2026-01-20"))
        recurring.resume(id, d("2026-04-05"))
        recurring.generateDue(d("2026-04-15"))
        assertEquals(listOf(d("2026-01-10"), d("2026-04-10")), allIncomes().map { LocalDate.ofEpochDay(it.date) })
    }

    @Test
    fun `deleting a template keeps generated entries`() = runTest {
        val id = salary()
        recurring.generateDue(d("2026-02-15"))
        recurring.delete(id)
        assertEquals(2, allIncomes().size)
        assertEquals(0, recurring.generateDue(d("2026-05-15")))
    }

    @Test
    fun `recurring expenses create one item in the template category`() = runTest {
        val bills = CategoryRepository(db).all().first { it.defaultKey == "bills" }.id
        recurring.save(
            RecurringTemplateEntity(
                kind = TemplateKind.EXPENSE, name = "Chirie", amountBani = 250_000, dayOfMonth = 1,
                categoryId = bills, startDate = d("2026-01-01").toEpochDay(), endDate = null, note = null,
            ),
        )
        recurring.generateDue(d("2026-02-01"))
        recurring.generateDue(d("2026-02-01"))
        val rows = expenses.observeMonth(YearMonth.of(2026, 2)).first()
        assertEquals(1, rows.size)
        assertEquals(250_000L, rows[0].itemsTotal)
        val items = expenses.observeItems(YearMonth.of(2026, 1), YearMonth.of(2026, 2)).first()
        assertEquals(2, items.size)
        assertEquals(setOf(bills), items.map { it.item.categoryId }.toSet())
    }
}
