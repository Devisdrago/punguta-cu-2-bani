package ro.punguta.app.domain.receipt

import java.time.LocalDate

/** A parsed value with a flag saying whether the parser trusts it. Uncertain values are highlighted. */
data class Parsed<T>(val value: T, val certain: Boolean)

data class ParsedItem(
    val name: String,
    val nameCertain: Boolean,
    /** Thousandths of a unit (1000 = 1 piece), null when the receipt shows no quantity. */
    val quantityMilli: Long?,
    val unitPriceBani: Long?,
    val quantityCertain: Boolean,
    val discountBani: Long,
    val discountCertain: Boolean,
    /** Net amount paid for the line, after [discountBani]. */
    val amountBani: Long,
    val amountCertain: Boolean,
)

enum class ReceiptWarning {
    NO_ITEMS,
    NO_TOTAL,
    /** The total was taken from the payment line (CARD/NUMERAR) because no TOTAL line was found. */
    TOTAL_FROM_PAYMENT,
    NO_DATE,
    NO_MERCHANT,
    /** Some lines with amounts could not be matched to an item. */
    UNUSED_LINES,
    /** A discount appeared before any item and could not be assigned. */
    ORPHAN_DISCOUNT,
    /** A receipt-level discount was spread proportionally over the items. */
    RECEIPT_DISCOUNT_SPREAD,
}

data class ParsedReceipt(
    val merchant: Parsed<String?>,
    val date: Parsed<LocalDate?>,
    val items: List<ParsedItem>,
    val total: Parsed<Long?>,
    val subtotalBani: Long?,
    /** Lines that looked relevant (they contain an amount) but were not used; shown for review. */
    val unusedLines: List<String>,
    val warnings: Set<ReceiptWarning>,
) {
    val itemsTotalBani: Long get() = items.sumOf { it.amountBani }
}
