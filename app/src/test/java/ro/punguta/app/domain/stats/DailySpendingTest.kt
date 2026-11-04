package ro.punguta.app.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DailySpendingTest {
    private val nov = YearMonth.of(2026, 11)
    private fun line(day: String, expense: Long, title: String, bani: Long) = ExpenseLine(LocalDate.parse(day), expense, title, bani)

    private val lines = listOf(
        line("2026-11-03", 1, "Kaufland", 1_100),
        line("2026-11-03", 1, "Kaufland", 2_850), // second item of the same receipt
        line("2026-11-03", 2, "Petrom", 24_880),
        line("2026-11-30", 3, "Cinema", 16_400),
        line("2026-10-31", 4, "Mega", 9_999), // previous month
        line("2026-12-01", 5, "Lidl", 500), // next month
    )

    @Test
    fun `one entry per day of the month, totals per day`() {
        val c = DailySpending.build(nov, lines)
        assertEquals(30, c.days.size)
        assertEquals(LocalDate.parse("2026-11-01"), c.days.first().date)
        assertEquals(28_830L, c.day(LocalDate.parse("2026-11-03"))!!.bani)
        assertEquals(16_400L, c.days.last().bani)
        assertEquals(0L, c.days[0].bani)
        assertEquals(45_230L, c.days.sumOf { it.bani }) // other months excluded
        assertEquals(28_830L, c.maxDayBani)
    }

    @Test
    fun `items of one expense are combined, biggest expense first`() {
        val day3 = DailySpending.build(nov, lines).day(LocalDate.parse("2026-11-03"))!!
        assertEquals(listOf(DayExpense(2, "Petrom", 24_880), DayExpense(1, "Kaufland", 3_950)), day3.expenses)
        assertTrue(DailySpending.build(nov, lines).days[0].expenses.isEmpty())
    }

    @Test
    fun `grid starts on Monday`() {
        assertEquals(6, DailySpending.build(nov, emptyList()).leadingBlanks) // 1 Nov 2026 is a Sunday
        assertEquals(0, DailySpending.build(YearMonth.of(2026, 6), emptyList()).leadingBlanks) // 1 Jun 2026 is a Monday
        assertEquals(28, DailySpending.build(YearMonth.of(2026, 2), emptyList()).days.size)
        assertEquals(29, DailySpending.build(YearMonth.of(2028, 2), emptyList()).days.size)
    }

    @Test
    fun `empty month and dates outside it`() {
        val c = DailySpending.build(nov, emptyList())
        assertEquals(0L, c.maxDayBani)
        assertNull(c.day(LocalDate.parse("2026-12-01")))
    }
}
