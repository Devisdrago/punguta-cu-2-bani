package ro.punguta.app.domain.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetsTest {
    @Test
    fun `levels at the 80 percent and 100 percent thresholds`() {
        assertEquals(BudgetLevel.OK, budgetLevel(0, 100_000))
        assertEquals(BudgetLevel.OK, budgetLevel(79_999, 100_000))
        assertEquals(BudgetLevel.WARNING, budgetLevel(80_000, 100_000))
        assertEquals(BudgetLevel.WARNING, budgetLevel(99_999, 100_000))
        assertEquals(BudgetLevel.WARNING, budgetLevel(100_000, 100_000)) // reached, not exceeded
        assertEquals(BudgetLevel.EXCEEDED, budgetLevel(100_001, 100_000))
    }

    @Test
    fun `odd limits use exact integer comparison`() {
        // 80% of 333 bani is 266,4 bani.
        assertEquals(BudgetLevel.OK, budgetLevel(266, 333))
        assertEquals(BudgetLevel.WARNING, budgetLevel(267, 333))
    }

    @Test
    fun `zero limit never divides by zero`() {
        assertEquals(BudgetLevel.OK, budgetLevel(0, 0))
        assertEquals(BudgetLevel.EXCEEDED, budgetLevel(1, 0))
        assertEquals(1f, BudgetStatus(1, "X", 0, 5).progress)
    }

    @Test
    fun `status progress and remaining`() {
        val s = BudgetStatus(1, "Mâncare", 100_000, 125_000)
        assertEquals(1f, s.progress)
        assertEquals(-25_000L, s.remainingBani)
        assertEquals(BudgetLevel.EXCEEDED, s.level)
        assertEquals(0.5f, BudgetStatus(1, "X", 100_000, 50_000).progress)
    }
}
