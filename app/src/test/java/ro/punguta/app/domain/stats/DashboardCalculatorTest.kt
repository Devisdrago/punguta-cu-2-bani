package ro.punguta.app.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.punguta.app.domain.budget.BudgetLevel
import java.time.LocalDate
import java.time.YearMonth

class DashboardCalculatorTest {
    private val names = mapOf(1L to "Mâncare", 2L to "Transport", 3L to "Facturi")
    private val nov = YearMonth.of(2026, 11)

    private fun a(date: String, bani: Long, cat: Long? = null) = DatedAmount(LocalDate.parse(date), bani, cat)

    private val incomes = listOf(
        a("2026-10-10", 500_000),
        a("2026-11-10", 500_000),
        a("2026-11-20", 75_050),
        a("2026-12-01", 999_999), // next month, must be ignored
    )
    private val items = listOf(
        a("2026-10-05", 40_000, 1),
        a("2026-10-06", 20_000, 2),
        a("2026-11-01", 30_000, 1),
        a("2026-11-30", 15_025, 1), // last day of the month counts
        a("2026-11-15", 120_000, 3),
        a("2026-12-01", 1_000, 1),
    )

    @Test
    fun `monthly totals, balance and savings rate`() {
        val d = DashboardCalculator.build(nov, incomes, items, names, emptyMap())
        assertEquals(575_050L, d.incomeBani)
        assertEquals(165_025L, d.spendingBani)
        assertEquals(410_025L, d.balanceBani)
        assertEquals(7_130L, d.savingsRateBasisPoints) // 410025 / 575050 = 71,30%
        assertEquals(500_000L, d.previousIncomeBani)
        assertEquals(60_000L, d.previousSpendingBani)
    }

    @Test
    fun `month with zero income has no savings rate`() {
        val d = DashboardCalculator.build(nov, emptyList(), items, names, emptyMap())
        assertNull(d.savingsRateBasisPoints)
        assertEquals(-165_025L, d.balanceBani)
    }

    @Test
    fun `empty month`() {
        val d = DashboardCalculator.build(YearMonth.of(2020, 1), incomes, items, names, emptyMap())
        assertTrue(d.isEmpty)
        assertTrue(d.categories.isEmpty())
        assertEquals(6, d.trend.size)
    }

    @Test
    fun `categories sorted by spending`() {
        val d = DashboardCalculator.build(nov, incomes, items, names, emptyMap())
        assertEquals(listOf(3L to 120_000L, 1L to 45_025L), d.categories.map { it.categoryId to it.bani })
    }

    @Test
    fun `changes from the previous month include categories that disappeared`() {
        val d = DashboardCalculator.build(nov, incomes, items, names, emptyMap())
        val changes = d.categoryChanges.associate { it.categoryId to it.deltaBani }
        assertEquals(mapOf(3L to 120_000L, 2L to -20_000L, 1L to 5_025L), changes)
        assertEquals(3L, d.categoryChanges.first().categoryId) // largest absolute change first
    }

    @Test
    fun `trend covers six months oldest first with zero-filled gaps`() {
        val d = DashboardCalculator.build(nov, incomes, items, names, emptyMap())
        assertEquals((6 downTo 1).map { nov.minusMonths(it - 1L) }, d.trend.map { it.month })
        assertEquals(MonthTotals(YearMonth.of(2026, 10), 500_000, 60_000), d.trend[4])
        assertEquals(MonthTotals(YearMonth.of(2026, 6), 0, 0), d.trend[0])
    }

    @Test
    fun `budgets use the month's category spending`() {
        val d = DashboardCalculator.build(nov, incomes, items, names, mapOf(1L to 50_000L, 3L to 100_000L, 2L to 10_000L, 99L to 1L))
        val byCat = d.budgets.associateBy { it.categoryId }
        assertEquals(BudgetLevel.WARNING, byCat.getValue(1).level) // 45.025 / 50.000 = 90%
        assertEquals(BudgetLevel.EXCEEDED, byCat.getValue(3).level)
        assertEquals(BudgetLevel.OK, byCat.getValue(2).level) // nothing spent this month
        assertEquals(false, 99L in byCat) // budget for a deleted category is ignored
        assertEquals(BudgetLevel.EXCEEDED, d.budgets.first().level) // worst first
    }
}
