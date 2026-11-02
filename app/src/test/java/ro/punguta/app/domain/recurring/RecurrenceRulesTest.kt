package ro.punguta.app.domain.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceRulesTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `day past month end falls on the last day`() {
        assertEquals(d("2026-02-28"), RecurrenceRules.occurrenceDate(YearMonth.of(2026, 2), 31))
        assertEquals(d("2028-02-29"), RecurrenceRules.occurrenceDate(YearMonth.of(2028, 2), 30)) // leap year
        assertEquals(d("2026-04-30"), RecurrenceRules.occurrenceDate(YearMonth.of(2026, 4), 31))
        assertEquals(d("2026-01-31"), RecurrenceRules.occurrenceDate(YearMonth.of(2026, 1), 31))
        assertEquals(d("2026-03-15"), RecurrenceRules.occurrenceDate(YearMonth.of(2026, 3), 15))
    }

    @Test
    fun `due includes today and excludes future days`() {
        val due = RecurrenceRules.dueOccurrences(15, d("2026-11-01"), null, d("2026-11-15"))
        assertEquals(listOf(d("2026-11-15")), due.map { it.date })
        assertTrue(RecurrenceRules.dueOccurrences(15, d("2026-11-01"), null, d("2026-11-14")).isEmpty())
    }

    @Test
    fun `catches up every missed month`() {
        val due = RecurrenceRules.dueOccurrences(31, d("2026-01-10"), null, d("2026-05-02"))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30").map(::d),
            due.map { it.date },
        )
        assertEquals(listOf("2026-01", "2026-02", "2026-03", "2026-04"), due.map { it.periodKey })
    }

    @Test
    fun `first month is skipped when the day is before the start date`() {
        val due = RecurrenceRules.dueOccurrences(5, d("2026-03-20"), null, d("2026-05-10"))
        assertEquals(listOf(d("2026-04-05"), d("2026-05-05")), due.map { it.date })
    }

    @Test
    fun `stopped template produces nothing after its end date`() {
        val due = RecurrenceRules.dueOccurrences(10, d("2026-01-01"), d("2026-03-05"), d("2026-06-30"))
        assertEquals(listOf(d("2026-01-10"), d("2026-02-10")), due.map { it.date })
    }

    @Test
    fun `future start date yields nothing`() {
        assertTrue(RecurrenceRules.dueOccurrences(1, d("2027-01-01"), null, d("2026-11-02")).isEmpty())
    }

    @Test
    fun `history is capped`() {
        val due = RecurrenceRules.dueOccurrences(1, d("1990-01-01"), null, d("2026-11-02"))
        assertEquals(RecurrenceRules.MAX_MONTHS, due.size)
    }

    @Test
    fun `next occurrence`() {
        assertEquals(d("2026-11-25"), RecurrenceRules.nextOccurrence(25, d("2026-01-01"), null, d("2026-11-02")))
        assertEquals(d("2026-12-01"), RecurrenceRules.nextOccurrence(1, d("2026-01-01"), null, d("2026-11-02")))
        assertEquals(d("2026-11-30"), RecurrenceRules.nextOccurrence(31, d("2026-01-01"), null, d("2026-11-02")))
        assertNull(RecurrenceRules.nextOccurrence(25, d("2026-01-01"), d("2026-11-02"), d("2026-11-02")))
    }
}
