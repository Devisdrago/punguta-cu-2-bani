package ro.punguta.app.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.domain.category.CategorySuggester
import ro.punguta.app.domain.expense.DraftError
import ro.punguta.app.domain.expense.ExpenseDraft
import ro.punguta.app.domain.expense.InvalidDraftException
import ro.punguta.app.domain.expense.ItemDraft
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.incomes.defaultDate
import java.io.File
import java.time.YearMonth

/**
 * Editor for manual expenses, existing expenses, and receipt review. Nothing is stored until
 * the user taps save; a receipt photo copy is deleted on save or discard.
 */
class ExpenseEditViewModel(
    private val repo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val pending: PendingDraftStore,
    state: SavedStateHandle,
) : ViewModel() {
    private val id: Long = state.get<Long>("id") ?: 0L
    private val draftKey: String? = state.get<String>("draft")
    private var nextKey = 1L
    private var suggester: CategorySuggester? = null

    private val _form = MutableStateFlow(ExpenseForm())
    val form = _form.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when the user tried to save while the items do not add up to the receipt total. */
    private val _confirmMismatch = MutableStateFlow(false)
    val confirmMismatch = _confirmMismatch.asStateFlow()

    fun load(defaultMonth: YearMonth) = viewModelScope.launch {
        if (_form.value.loaded) return@launch
        val s = categoryRepo.suggester().also { suggester = it }
        val pendingForm = draftKey?.let { pending.take(it) }
        val existing = if (id != 0L) repo.getDraft(id) else null
        _form.value = when {
            pendingForm != null -> pendingForm.copy(
                loaded = true,
                items = pendingForm.items.map { it.copy(key = nextKey++) },
            )
            existing != null -> ExpenseForm(
                loaded = true,
                id = existing.id,
                source = existing.source,
                title = existing.title,
                date = existing.date,
                note = existing.note.orEmpty(),
                receiptTotal = existing.receiptTotalBani?.let(Money::formatInput).orEmpty(),
                items = existing.items.map {
                    ItemForm(
                        key = nextKey++,
                        name = it.name,
                        amount = Money.formatInput(it.amountBani),
                        quantity = it.quantityMilli?.let(::formatQuantity).orEmpty(),
                        unitPrice = it.unitPriceBani?.let(Money::formatInput).orEmpty(),
                        discount = if (it.discountBani > 0) Money.formatInput(it.discountBani) else "",
                        categoryId = it.categoryId,
                        categoryTouched = true,
                    )
                },
            )
            else -> ExpenseForm(
                loaded = true,
                date = defaultDate(defaultMonth),
                items = listOf(ItemForm(key = nextKey++, categoryId = s.suggest("").categoryId)),
            )
        }
    }

    fun updateHeader(field: HeaderField?, transform: (ExpenseForm) -> ExpenseForm) = _form.update {
        val updated = transform(it)
        updated.copy(
            uncertainHeader = if (field != null) updated.uncertainHeader - field else updated.uncertainHeader,
            error = null,
        )
    }

    fun updateItem(key: Long, field: ItemField, transform: (ItemForm) -> ItemForm) = _form.update { form ->
        form.copy(
            items = form.items.map { item ->
                if (item.key != key) return@map item
                var updated = transform(item).let { it.copy(uncertain = it.uncertain - field) }
                if (field == ItemField.NAME && !updated.categoryTouched) {
                    suggester?.suggest(updated.name)?.categoryId?.let { updated = updated.copy(categoryId = it) }
                }
                if (field == ItemField.CATEGORY) updated = updated.copy(categoryTouched = true)
                if (field in setOf(ItemField.QUANTITY, ItemField.UNIT_PRICE, ItemField.DISCOUNT)) {
                    updated = recalculate(updated)
                }
                updated
            },
            invalidItemKeys = form.invalidItemKeys - key,
            error = null,
        )
    }

    fun addItem() = _form.update {
        it.copy(items = it.items + ItemForm(key = nextKey++, categoryId = suggester?.suggest("")?.categoryId, showDetails = it.isReceipt))
    }

    fun removeItem(key: Long) = _form.update { it.copy(items = it.items.filterNot { item -> item.key == key }) }

    fun toggleDetails(key: Long) = _form.update {
        it.copy(items = it.items.map { item -> if (item.key == key) item.copy(showDetails = !item.showDetails) else item })
    }

    fun dismissMismatch() { _confirmMismatch.value = false }

    fun save(ignoreMismatch: Boolean = false) = viewModelScope.launch {
        _confirmMismatch.value = false
        val f = _form.value
        val invalid = f.items.filter { it.amountBani == null }.map { it.key }.toSet()
        if (invalid.isNotEmpty()) {
            _form.update { it.copy(error = DraftError.ITEM_AMOUNT_INVALID, invalidItemKeys = invalid) }
            return@launch
        }
        if (f.items.any { it.categoryId == null }) {
            _form.update { it.copy(error = DraftError.ITEM_CATEGORY_MISSING) }
            return@launch
        }
        val diff = f.totalDifferenceBani
        if (!ignoreMismatch && diff != null && diff != 0L) {
            _confirmMismatch.value = true
            return@launch
        }
        val draft = toDraft(f)
        try {
            repo.save(draft)
            deletePhoto(f.photoPath)
            _form.update { it.copy(done = true) }
        } catch (e: InvalidDraftException) {
            _form.update { it.copy(error = e.error) }
        }
    }

    fun delete() = viewModelScope.launch {
        if (id != 0L) repo.delete(id)
        discard()
    }

    /** Leaves without saving; the app's photo copy is removed either way. */
    fun discard() {
        deletePhoto(_form.value.photoPath)
        _form.update { it.copy(done = true) }
    }

    private fun toDraft(f: ExpenseForm): ExpenseDraft {
        // Convenience for quick manual entries: one field may be left empty.
        val firstName = f.items.firstOrNull()?.name?.trim().orEmpty()
        val title = f.title.trim().ifEmpty { if (f.items.size == 1) firstName else "" }
        val items = f.items.map { item ->
            ItemDraft(
                name = item.name.trim().ifEmpty { if (f.items.size == 1) title else "" },
                quantityMilli = parseQuantity(item.quantity),
                unitPriceBani = Money.parse(item.unitPrice),
                discountBani = Money.parse(item.discount) ?: 0,
                amountBani = item.amountBani ?: 0,
                categoryId = item.categoryId!!,
            )
        }
        return ExpenseDraft(
            id = f.id,
            title = title,
            date = f.date,
            note = f.note,
            source = f.source,
            receiptTotalBani = if (f.source == ExpenseSource.RECEIPT) f.receiptTotalBani else null,
            items = items,
        )
    }

    private fun recalculate(item: ItemForm): ItemForm {
        val qty = parseQuantity(item.quantity) ?: return item
        val unit = Money.parse(item.unitPrice) ?: return item
        val discount = Money.parse(item.discount) ?: 0
        val net = (Money.lineTotal(qty, unit) - discount).coerceAtLeast(0)
        return item.copy(amount = Money.formatInput(net), uncertain = item.uncertain - ItemField.AMOUNT)
    }

    private fun deletePhoto(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    companion object {
        val Factory = appViewModelFactory { c, s -> ExpenseEditViewModel(c.expenses, c.categories, c.pendingDrafts, s) }

        /** Quantity text like "2", "1,5" or "0,355" to thousandths. */
        fun parseQuantity(text: String): Long? {
            val t = text.trim().replace('.', ',')
            if (t.isEmpty()) return null
            val parts = t.split(',')
            if (parts.size > 2 || parts.any { p -> p.any { !it.isDigit() } }) return null
            val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
            val frac = parts.getOrNull(1)?.take(3)?.padEnd(3, '0')?.toLongOrNull() ?: 0
            val milli = whole * 1000 + frac
            return if (milli in 1..1_000_000_000) milli else null
        }

        fun formatQuantity(milli: Long): String {
            val whole = milli / 1000
            val frac = milli % 1000
            return if (frac == 0L) whole.toString() else "$whole," + frac.toString().padStart(3, '0').trimEnd('0')
        }
    }
}
