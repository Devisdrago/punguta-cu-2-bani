package ro.punguta.app.domain.stats

import java.time.LocalDate
import java.time.YearMonth

/** One expense item reduced to what the calendar needs. */
data class ExpenseLine(val date: LocalDate, val expenseId: Long, val title: String, val bani: Long)

/** An expense's total for one day (an expense can hold several items). */
data class DayExpense(val expenseId: Long, val title: String, val bani: Long)

data class DaySpending(val date: LocalDate, val bani: Long, val expenses: List<DayExpense>)

/** Every day of a calendar month with what was spent on it. */
data class MonthCalendar(
    val month: YearMonth,
    /** One entry per day, day 1 first. */
    val days: List<DaySpending>,
) {
    /** Empty cells before day 1 in a Monday-first week, as Romanian calendars are laid out. */
    val leadingBlanks: Int get() = month.atDay(1).dayOfWeek.value - 1

    val maxDayBani: Long get() = days.maxOfOrNull { it.bani } ?: 0

    fun day(date: LocalDate): DaySpending? = if (YearMonth.from(date) == month) days[date.dayOfMonth - 1] else null
}

object DailySpending {
    fun build(month: YearMonth, lines: List<ExpenseLine>): MonthCalendar {
        val byDay = lines.filter { YearMonth.from(it.date) == month }.groupBy { it.date.dayOfMonth }
        val days = (1..month.lengthOfMonth()).map { day ->
            val dayLines = byDay[day].orEmpty()
            val expenses = dayLines.groupBy { it.expenseId }
                .map { (id, items) -> DayExpense(id, items.first().title, items.sumOf { it.bani }) }
                .sortedByDescending { it.bani }
            DaySpending(month.atDay(day), dayLines.sumOf { it.bani }, expenses)
        }
        return MonthCalendar(month, days)
    }
}
