package ro.punguta.app.domain.expense

import ro.punguta.app.domain.money.Money
import java.time.LocalDate

/** A validated purchased line, ready to store. */
data class ItemDraft(
    val name: String,
    val quantityMilli: Long?,
    val unitPriceBani: Long?,
    val discountBani: Long,
    /** Net amount paid for the line, discount already subtracted. */
    val amountBani: Long,
    val categoryId: Long,
)

/** An expense as reviewed by the user, before it is saved. */
data class ExpenseDraft(
    val id: Long?,
    val title: String,
    val date: LocalDate,
    val note: String?,
    val source: String,
    val receiptTotalBani: Long?,
    val items: List<ItemDraft>,
) {
    val itemsTotalBani: Long get() = items.sumOf { it.amountBani }
}

enum class DraftError { TITLE_EMPTY, NO_ITEMS, ITEM_NAME_EMPTY, ITEM_AMOUNT_INVALID, ITEM_CATEGORY_MISSING, TOTAL_ZERO }

class InvalidDraftException(val error: DraftError) : Exception(error.name)

object DraftValidator {
    fun validate(draft: ExpenseDraft): DraftError? = when {
        draft.title.isBlank() -> DraftError.TITLE_EMPTY
        draft.items.isEmpty() -> DraftError.NO_ITEMS
        draft.items.any { it.name.isBlank() } -> DraftError.ITEM_NAME_EMPTY
        draft.items.any { it.amountBani < 0 || it.amountBani > Money.MAX_BANI } -> DraftError.ITEM_AMOUNT_INVALID
        draft.itemsTotalBani <= 0 -> DraftError.TOTAL_ZERO
        else -> null
    }
}

/** Comparison of the sum of items with the total printed on the receipt. */
data class TotalCheck(val itemsTotalBani: Long, val receiptTotalBani: Long?) {
    val differenceBani: Long? get() = receiptTotalBani?.let { itemsTotalBani - it }
    val matches: Boolean get() = differenceBani == 0L
}
