package ro.punguta.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.domain.category.CategorySuggester.Source
import ro.punguta.app.domain.expense.DraftError
import ro.punguta.app.domain.expense.ExpenseDraft
import ro.punguta.app.domain.expense.InvalidDraftException
import ro.punguta.app.domain.expense.ItemDraft
import ro.punguta.app.ui.expenses.ExpenseEditViewModel
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var repo: ExpenseRepository

    @Before
    fun setUp() {
        db = testDb()
        categories = CategoryRepository(db)
        repo = ExpenseRepository(db, categories)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(key: String) = categories.all().first { it.defaultKey == key }.id

    private fun item(name: String, bani: Long, category: Long) = ItemDraft(name, 1000, bani, 0, bani, category)

    @Test
    fun `one receipt split across categories`() = runTest {
        val food = cat("food")
        val home = cat("home")
        repo.save(
            ExpenseDraft(
                null, "Kaufland", LocalDate.parse("2026-11-02"), null, ExpenseSource.RECEIPT, 2_349,
                listOf(item("Paine", 450, food), item("Lapte", 899, food), item("Detergent", 1_000, home)),
            ),
        )
        val items = repo.observeItems(YearMonth.of(2026, 11), YearMonth.of(2026, 11)).first()
        assertEquals(1_349L, items.filter { it.item.categoryId == food }.sumOf { it.item.amountBani })
        assertEquals(1_000L, items.filter { it.item.categoryId == home }.sumOf { it.item.amountBani })
        assertEquals(2_349L, repo.observeMonth(YearMonth.of(2026, 11)).first().single().itemsTotal)
    }

    @Test
    fun `correcting a category is learned, keeping the suggestion is not`() = runTest {
        val food = cat("food")
        val drinks = cat("drinks")
        repo.save(
            ExpenseDraft(
                null, "Lidl", LocalDate.parse("2026-11-02"), null, ExpenseSource.RECEIPT, null,
                listOf(item("CAFEA BOABE 1KG", 6_000, food), item("PAINE", 400, food)),
            ),
        )
        val s = categories.suggester()
        assertEquals(food to Source.LEARNED, s.suggest("Cafea boabe 1 kg").let { it.categoryId to it.source })
        assertEquals(Source.RULE, s.suggest("PAINE").source)
        assertEquals(drinks, s.suggest("CAFEA MACINATA").categoryId)
    }

    @Test
    fun `editing replaces items and keeps the source`() = runTest {
        val food = cat("food")
        val id = repo.save(
            ExpenseDraft(null, "Piata", LocalDate.parse("2026-11-01"), null, ExpenseSource.MANUAL, null, listOf(item("Rosii", 1_000, food))),
        )
        val draft = repo.getDraft(id)!!
        repo.save(draft.copy(items = draft.items + item("Ardei", 700, food), source = ExpenseSource.RECEIPT))
        val reloaded = repo.getDraft(id)!!
        assertEquals(2, reloaded.items.size)
        assertEquals(ExpenseSource.MANUAL, reloaded.source)
        assertEquals(1_700L, reloaded.itemsTotalBani)
    }

    @Test
    fun `invalid drafts are rejected`() = runTest {
        val food = cat("food")
        val base = ExpenseDraft(null, "X", LocalDate.parse("2026-11-01"), null, ExpenseSource.MANUAL, null, listOf(item("A", 100, food)))
        expect(DraftError.TITLE_EMPTY) { repo.save(base.copy(title = " ")) }
        expect(DraftError.NO_ITEMS) { repo.save(base.copy(items = emptyList())) }
        expect(DraftError.ITEM_NAME_EMPTY) { repo.save(base.copy(items = listOf(item(" ", 100, food)))) }
        expect(DraftError.TOTAL_ZERO) { repo.save(base.copy(items = listOf(item("A", 0, food)))) }
        assertEquals(0, repo.observeMonth(YearMonth.of(2026, 11)).first().size)
    }

    @Test
    fun `quantity text parsing`() {
        assertEquals(2_000L, ExpenseEditViewModel.parseQuantity("2"))
        assertEquals(1_500L, ExpenseEditViewModel.parseQuantity("1,5"))
        assertEquals(355L, ExpenseEditViewModel.parseQuantity("0.355"))
        assertEquals(null, ExpenseEditViewModel.parseQuantity("abc"))
        assertEquals(null, ExpenseEditViewModel.parseQuantity("0"))
        assertEquals("1,5", ExpenseEditViewModel.formatQuantity(1_500))
        assertEquals("2", ExpenseEditViewModel.formatQuantity(2_000))
        assertEquals("0,355", ExpenseEditViewModel.formatQuantity(355))
    }

    private suspend fun expect(error: DraftError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $error")
        } catch (e: InvalidDraftException) {
            assertEquals(error, e.error)
        }
    }
}
