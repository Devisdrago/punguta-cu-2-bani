package ro.punguta.app.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.ExpenseEntity
import ro.punguta.app.data.db.ExpenseItemEntity
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.repo.CategoryInUseException
import ro.punguta.app.data.repo.CategoryNameError
import ro.punguta.app.data.repo.CategoryNameException
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.domain.category.CategorySuggester.Source
import ro.punguta.app.domain.category.DefaultCategoryKeys

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() {
        db = testDb()
        repo = CategoryRepository(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seeds the ten Romanian default categories`() = runTest {
        val names = repo.all().map { it.name }
        assertEquals(
            listOf(
                "Mâncare", "Băuturi", "Casă", "Facturi", "Transport",
                "Sănătate", "Îmbrăcăminte", "Divertisment", "Abonamente", "Altele",
            ),
            names,
        )
    }

    @Test
    fun `add and rename validate names`() = runTest {
        val id = repo.add("  Animale   de companie ")
        assertEquals("Animale de companie", repo.all().first { it.id == id }.name)
        expectNameError(CategoryNameError.EMPTY) { repo.add("   ") }
        expectNameError(CategoryNameError.DUPLICATE) { repo.add("mancare") } // diacritics and case folded
        expectNameError(CategoryNameError.TOO_LONG) { repo.add("x".repeat(41)) }
        repo.rename(id, "Animale")
        assertEquals("Animale", repo.all().first { it.id == id }.name)
        // Renaming to its own name is fine.
        repo.rename(id, "animale")
    }

    @Test
    fun `renaming a default category keeps its keyword rules`() = runTest {
        val food = repo.all().first { it.defaultKey == DefaultCategoryKeys.FOOD }
        repo.rename(food.id, "Alimente")
        assertEquals(food.id, repo.suggester().suggest("PAINE").categoryId)
    }

    @Test
    fun `deleting a used category requires a target and moves items`() = runTest {
        val all = repo.all()
        val drinks = all.first { it.defaultKey == DefaultCategoryKeys.DRINKS }.id
        val food = all.first { it.defaultKey == DefaultCategoryKeys.FOOD }.id
        val expenseId = db.expenseDao().insert(ExpenseEntity(title = "Mega", date = 0, note = null, source = ExpenseSource.MANUAL, receiptTotalBani = null))
        db.expenseDao().insertItems(listOf(item(expenseId, "Bere", drinks), item(expenseId, "Suc", drinks)))
        repo.learn("Bere Ursus", drinks)

        try {
            repo.delete(drinks, moveTo = null)
            fail("Expected CategoryInUseException")
        } catch (e: CategoryInUseException) {
            assertEquals(2, e.usage)
        }

        repo.delete(drinks, moveTo = food)
        assertTrue(db.expenseDao().items(expenseId).all { it.categoryId == food })
        assertNull(repo.all().firstOrNull { it.id == drinks })
        // The learned correction moved too.
        assertEquals(food, repo.suggester().suggest("BERE URSUS").categoryId)
    }

    @Test
    fun `learning a correction overrides rules`() = runTest {
        val all = repo.all()
        val home = all.first { it.defaultKey == DefaultCategoryKeys.HOME }.id
        assertEquals(Source.RULE, repo.suggester().suggest("Apa plata 2L").source)
        repo.learn("APA PLATA 2L", home)
        val s = repo.suggester().suggest("Apă plată 2 L")
        assertEquals(home, s.categoryId)
        assertEquals(Source.LEARNED, s.source)
    }

    private fun item(expenseId: Long, name: String, categoryId: Long) = ExpenseItemEntity(
        expenseId = expenseId, name = name, quantityMilli = 1000, unitPriceBani = 500,
        discountBani = 0, amountBani = 500, categoryId = categoryId, position = 0,
    )

    private suspend fun expectNameError(expected: CategoryNameError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: CategoryNameException) {
            assertEquals(expected, e.error)
        }
    }
}
