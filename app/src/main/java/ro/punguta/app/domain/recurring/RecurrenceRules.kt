package ro.punguta.app.domain.recurring

import java.time.LocalDate
import java.time.YearMonth

/** A single due occurrence of a recurring template. */
data class Occurrence(val period: YearMonth, val date: LocalDate) {
    /** Stable key used for duplicate prevention, e.g. "2026-11". */
    val periodKey: String get() = period.toString()
}

/** Pure date rules for monthly recurring income and expenses. Months are calendar months. */
object RecurrenceRules {
    /** Safety cap so a bad start date can never generate thousands of rows. */
    const val MAX_MONTHS = 240

    /** The occurrence date in [month]; days past the month's end fall on its last day. */
    fun occurrenceDate(month: YearMonth, dayOfMonth: Int): LocalDate {
        require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31" }
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }

    /**
     * Every occurrence that is due on or before [today], within the template's active range.
     * The caller filters out periods that were already generated, which makes catching up after
     * missed runs, reboots and reruns safe.
     */
    fun dueOccurrences(
        dayOfMonth: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        today: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        val lastMonth = YearMonth.from(today)
        var month = YearMonth.from(startDate)
        val earliest = lastMonth.minusMonths(MAX_MONTHS.toLong() - 1)
        if (month.isBefore(earliest)) month = earliest
        while (!month.isAfter(lastMonth)) {
            val date = occurrenceDate(month, dayOfMonth)
            val afterStart = !date.isBefore(startDate)
            val notFuture = !date.isAfter(today)
            val beforeEnd = endDate == null || !date.isAfter(endDate)
            if (afterStart && notFuture && beforeEnd) result += Occurrence(month, date)
            month = month.plusMonths(1)
        }
        return result
    }

    /** The next date the template will produce an entry, or null if it has ended. */
    fun nextOccurrence(dayOfMonth: Int, startDate: LocalDate, endDate: LocalDate?, today: LocalDate): LocalDate? {
        var month = YearMonth.from(maxOf(startDate, today))
        repeat(2) {
            val date = occurrenceDate(month, dayOfMonth)
            if (!date.isBefore(startDate) && date.isAfter(today)) {
                return if (endDate == null || !date.isAfter(endDate)) date else null
            }
            month = month.plusMonths(1)
        }
        return null
    }
}
