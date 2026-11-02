package ro.punguta.app.domain.budget

enum class BudgetLevel { OK, WARNING, EXCEEDED }

data class BudgetStatus(
    val categoryId: Long,
    val categoryName: String,
    val limitBani: Long,
    val spentBani: Long,
) {
    val level: BudgetLevel get() = budgetLevel(spentBani, limitBani)
    val remainingBani: Long get() = limitBani - spentBani

    /** Fraction used, clamped to 0..1 for progress bars. */
    val progress: Float
        get() = if (limitBani <= 0) 1f else (spentBani.coerceAtLeast(0).toFloat() / limitBani).coerceIn(0f, 1f)
}

/** Warning from 80% of the limit, exceeded once spending is strictly above the limit. */
const val BUDGET_WARNING_BASIS_POINTS = 8_000L

fun budgetLevel(spentBani: Long, limitBani: Long): BudgetLevel {
    if (limitBani <= 0) return if (spentBani > 0) BudgetLevel.EXCEEDED else BudgetLevel.OK
    return when {
        spentBani > limitBani -> BudgetLevel.EXCEEDED
        spentBani * 10_000 >= limitBani * BUDGET_WARNING_BASIS_POINTS -> BudgetLevel.WARNING
        else -> BudgetLevel.OK
    }
}
