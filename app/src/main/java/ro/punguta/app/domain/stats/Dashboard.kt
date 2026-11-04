package ro.punguta.app.domain.stats

import ro.punguta.app.domain.budget.BudgetStatus
import ro.punguta.app.domain.money.savingsRateBasisPoints
import java.time.YearMonth

/** Everything the dashboard shows for one month. Pure data, built by [DashboardCalculator]. */
data class DashboardData(
    val month: YearMonth,
    val incomeBani: Long,
    val spendingBani: Long,
    /** Null when the month has no income, so a percentage would be meaningless. */
    val savingsRateBasisPoints: Long?,
    val categories: List<CategoryTotal>,
    val previousIncomeBani: Long,
    val previousSpendingBani: Long,
    val categoryChanges: List<CategoryChange>,
    val trend: List<MonthTotals>,
    val budgets: List<BudgetStatus>,
    val calendar: MonthCalendar,
) {
    val balanceBani: Long get() = incomeBani - spendingBani
    val isEmpty: Boolean get() = incomeBani == 0L && spendingBani == 0L
}

object DashboardCalculator {
    const val TREND_MONTHS = 6

    /**
     * @param incomes income amounts covering at least the trend window
     * @param items expense item amounts (with categories) covering at least the trend window
     * @param budgets category id to monthly limit
     * @param lines expense items with their expense, for the daily calendar
     */
    fun build(
        month: YearMonth,
        incomes: List<DatedAmount>,
        items: List<DatedAmount>,
        categoryNames: Map<Long, String>,
        budgets: Map<Long, Long>,
        lines: List<ExpenseLine> = emptyList(),
    ): DashboardData {
        val previous = month.minusMonths(1)
        fun inMonth(list: List<DatedAmount>, m: YearMonth) = list.filter { YearMonth.from(it.date) == m }

        val currentItems = inMonth(items, month)
        val previousItems = inMonth(items, previous)
        val income = inMonth(incomes, month).sumOf { it.bani }
        val spending = currentItems.sumOf { it.bani }
        val categories = MonthlyStats.categoryTotals(currentItems, categoryNames)
        val previousCategories = MonthlyStats.categoryTotals(previousItems, categoryNames)
        val spentByCategory = categories.associate { it.categoryId to it.bani }

        val budgetStatuses = budgets
            .filterKeys { it in categoryNames }
            .map { (categoryId, limit) ->
                BudgetStatus(categoryId, categoryNames.getValue(categoryId), limit, spentByCategory[categoryId] ?: 0)
            }
            .sortedWith(compareByDescending<BudgetStatus> { it.level }.thenByDescending { it.progress })

        return DashboardData(
            month = month,
            incomeBani = income,
            spendingBani = spending,
            savingsRateBasisPoints = savingsRateBasisPoints(income, spending),
            categories = categories,
            previousIncomeBani = inMonth(incomes, previous).sumOf { it.bani },
            previousSpendingBani = previousItems.sumOf { it.bani },
            categoryChanges = MonthlyStats.compareCategories(categories, previousCategories),
            trend = MonthlyStats.monthTotals(MonthlyStats.recentMonths(month, TREND_MONTHS), incomes, items),
            budgets = budgetStatuses,
            calendar = DailySpending.build(month, lines),
        )
    }
}
