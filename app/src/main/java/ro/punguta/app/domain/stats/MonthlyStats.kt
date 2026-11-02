package ro.punguta.app.domain.stats

import java.time.LocalDate
import java.time.YearMonth

/** Spending of one category in one month. */
data class CategoryTotal(val categoryId: Long, val name: String, val bani: Long)

data class CategoryChange(
    val categoryId: Long,
    val name: String,
    val currentBani: Long,
    val previousBani: Long,
) {
    val deltaBani: Long get() = currentBani - previousBani
}

data class MonthTotals(val month: YearMonth, val incomeBani: Long, val spendingBani: Long) {
    val balanceBani: Long get() = incomeBani - spendingBani
}

/** A dated amount with an optional category, the common shape for aggregation. */
data class DatedAmount(val date: LocalDate, val bani: Long, val categoryId: Long? = null)

object MonthlyStats {
    /** Groups amounts by category and sorts by spending, largest first. */
    fun categoryTotals(items: List<DatedAmount>, names: Map<Long, String>): List<CategoryTotal> =
        items.filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .map { (id, list) -> CategoryTotal(id, names[id] ?: "?", list.sumOf { it.bani }) }
            .filter { it.bani != 0L }
            .sortedWith(compareByDescending<CategoryTotal> { it.bani }.thenBy { it.name })

    /** Per-category change between two months, including categories present in only one of them. */
    fun compareCategories(current: List<CategoryTotal>, previous: List<CategoryTotal>): List<CategoryChange> {
        val prev = previous.associateBy { it.categoryId }
        val curr = current.associateBy { it.categoryId }
        return (curr.keys + prev.keys).map { id ->
            CategoryChange(
                categoryId = id,
                name = curr[id]?.name ?: prev[id]!!.name,
                currentBani = curr[id]?.bani ?: 0,
                previousBani = prev[id]?.bani ?: 0,
            )
        }.filter { it.deltaBani != 0L }
            .sortedByDescending { kotlin.math.abs(it.deltaBani) }
    }

    /** Income and spending per month for every month in [months], zero-filled. */
    fun monthTotals(
        months: List<YearMonth>,
        incomes: List<DatedAmount>,
        expenses: List<DatedAmount>,
    ): List<MonthTotals> {
        val incomeByMonth = incomes.groupBy { YearMonth.from(it.date) }.mapValues { e -> e.value.sumOf { it.bani } }
        val spendByMonth = expenses.groupBy { YearMonth.from(it.date) }.mapValues { e -> e.value.sumOf { it.bani } }
        return months.map { MonthTotals(it, incomeByMonth[it] ?: 0, spendByMonth[it] ?: 0) }
    }

    /** The [count] months ending with [last], oldest first. */
    fun recentMonths(last: YearMonth, count: Int): List<YearMonth> =
        (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }
}

/** Inclusive epoch-day range of a calendar month. */
fun YearMonth.epochDayRange(): LongRange = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()
