package ro.punguta.app.ui.expenses

import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.domain.expense.DraftError
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.receipt.ReceiptWarning
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ItemField { NAME, AMOUNT, QUANTITY, UNIT_PRICE, DISCOUNT, CATEGORY }
enum class HeaderField { TITLE, DATE, TOTAL }

/** One editable line. Text fields hold what the user typed; they are parsed on save. */
data class ItemForm(
    val key: Long,
    val name: String = "",
    val amount: String = "",
    val quantity: String = "",
    val unitPrice: String = "",
    val discount: String = "",
    val categoryId: Long? = null,
    /** True once the user picked a category, so name edits stop re-suggesting it. */
    val categoryTouched: Boolean = false,
    /** Fields the receipt parser was not sure about; cleared field by field as the user edits. */
    val uncertain: Set<ItemField> = emptySet(),
    val showDetails: Boolean = false,
) {
    val amountBani: Long? get() = Money.parse(amount)
}

data class ExpenseForm(
    val loaded: Boolean = false,
    val id: Long? = null,
    val source: String = ExpenseSource.MANUAL,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val receiptTotal: String = "",
    val items: List<ItemForm> = emptyList(),
    val uncertainHeader: Set<HeaderField> = emptySet(),
    /** App-private copy of the receipt photo; deleted after save or discard. */
    val photoPath: String? = null,
    /** Parser findings shown at the top of the receipt review. */
    val receiptWarnings: Set<ReceiptWarning> = emptySet(),
    /** OCR rows with amounts that could not be matched to an item. */
    val unusedLines: List<String> = emptyList(),
    val error: DraftError? = null,
    val invalidItemKeys: Set<Long> = emptySet(),
    val done: Boolean = false,
) {
    val itemsTotalBani: Long get() = items.sumOf { it.amountBani ?: 0 }
    val receiptTotalBani: Long? get() = Money.parse(receiptTotal)
    val isReceipt: Boolean get() = source == ExpenseSource.RECEIPT
    val hasUncertainty: Boolean get() = uncertainHeader.isNotEmpty() || items.any { it.uncertain.isNotEmpty() }

    /** Difference between items and the printed total, or null when there is no total to compare. */
    val totalDifferenceBani: Long? get() = receiptTotalBani?.let { itemsTotalBani - it }
}

/** Hands a parsed receipt from the scanner to the editor without passing it through navigation. */
class PendingDraftStore {
    private val drafts = ConcurrentHashMap<String, ExpenseForm>()

    fun put(form: ExpenseForm): String = UUID.randomUUID().toString().also { drafts[it] = form }

    fun take(key: String): ExpenseForm? = drafts.remove(key)
}
