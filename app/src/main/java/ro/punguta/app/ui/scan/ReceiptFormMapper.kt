package ro.punguta.app.ui.scan

import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.domain.category.CategorySuggester
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.receipt.ParsedReceipt
import ro.punguta.app.ui.expenses.ExpenseEditViewModel
import ro.punguta.app.ui.expenses.ExpenseForm
import ro.punguta.app.ui.expenses.HeaderField
import ro.punguta.app.ui.expenses.ItemField
import ro.punguta.app.ui.expenses.ItemForm
import java.time.LocalDate

/** Turns parser output into an editable review form, carrying every uncertainty flag along. */
object ReceiptFormMapper {
    fun toForm(parsed: ParsedReceipt, suggester: CategorySuggester, photoPath: String?, today: LocalDate): ExpenseForm {
        val header = buildSet {
            if (!parsed.merchant.certain) add(HeaderField.TITLE)
            if (!parsed.date.certain) add(HeaderField.DATE)
            if (!parsed.total.certain) add(HeaderField.TOTAL)
        }
        val items = parsed.items.mapIndexed { index, p ->
            val suggestion = suggester.suggest(p.name)
            val uncertain = buildSet {
                if (!p.nameCertain) add(ItemField.NAME)
                if (!p.amountCertain) add(ItemField.AMOUNT)
                if (!p.quantityCertain && p.quantityMilli != null) {
                    add(ItemField.QUANTITY)
                    add(ItemField.UNIT_PRICE)
                }
                if (!p.discountCertain && p.discountBani > 0) add(ItemField.DISCOUNT)
                if (suggestion.source == CategorySuggester.Source.FALLBACK || suggestion.source == CategorySuggester.Source.NONE) {
                    add(ItemField.CATEGORY)
                }
            }
            ItemForm(
                key = index + 1L,
                name = p.name,
                amount = Money.formatInput(p.amountBani),
                quantity = p.quantityMilli?.let(ExpenseEditViewModel::formatQuantity).orEmpty(),
                unitPrice = p.unitPriceBani?.let(Money::formatInput).orEmpty(),
                discount = if (p.discountBani > 0) Money.formatInput(p.discountBani) else "",
                categoryId = suggestion.categoryId,
                uncertain = uncertain,
                showDetails = uncertain.any { it == ItemField.QUANTITY || it == ItemField.DISCOUNT },
            )
        }.ifEmpty {
            // Nothing recognized as an item: start with one empty line to fill in.
            listOf(
                ItemForm(
                    key = 1,
                    categoryId = suggester.suggest("").categoryId,
                    uncertain = setOf(ItemField.NAME, ItemField.AMOUNT),
                ),
            )
        }
        return ExpenseForm(
            source = ExpenseSource.RECEIPT,
            title = parsed.merchant.value.orEmpty(),
            date = parsed.date.value ?: today,
            receiptTotal = parsed.total.value?.let(Money::formatInput).orEmpty(),
            items = items,
            uncertainHeader = header,
            photoPath = photoPath,
            receiptWarnings = parsed.warnings,
            unusedLines = parsed.unusedLines,
        )
    }
}
