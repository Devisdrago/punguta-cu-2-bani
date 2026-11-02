package ro.punguta.app.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.CategoryLearningEntity
import ro.punguta.app.data.db.ExpenseEntity
import ro.punguta.app.data.db.ExpenseItemEntity
import ro.punguta.app.data.db.ExpenseWithTotal
import ro.punguta.app.data.db.ItemWithExpense
import ro.punguta.app.domain.expense.DraftValidator
import ro.punguta.app.domain.expense.ExpenseDraft
import ro.punguta.app.domain.expense.InvalidDraftException
import ro.punguta.app.domain.expense.ItemDraft
import ro.punguta.app.domain.stats.epochDayRange
import ro.punguta.app.domain.text.TextNormalizer
import java.time.LocalDate
import java.time.YearMonth

class ExpenseRepository(
    private val db: AppDatabase,
    private val categories: CategoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.expenseDao()

    fun observeMonth(month: YearMonth): Flow<List<ExpenseWithTotal>> {
        val range = month.epochDayRange()
        return dao.observeRange(range.first, range.last)
    }

    fun observeItems(from: YearMonth, to: YearMonth): Flow<List<ItemWithExpense>> =
        dao.observeItemsRange(from.epochDayRange().first, to.epochDayRange().last)

    suspend fun getDraft(id: Long): ExpenseDraft? {
        val e = dao.get(id) ?: return null
        val items = dao.items(id).map {
            ItemDraft(it.name, it.quantityMilli, it.unitPriceBani, it.discountBani, it.amountBani, it.categoryId)
        }
        return ExpenseDraft(e.id, e.title, LocalDate.ofEpochDay(e.date), e.note, e.source, e.receiptTotalBani, items)
    }

    /**
     * Saves a reviewed expense and its items in one transaction. Any item whose category differs
     * from what would have been suggested is remembered as a user correction.
     */
    suspend fun save(draft: ExpenseDraft): Long {
        DraftValidator.validate(draft)?.let { throw InvalidDraftException(it) }
        val suggester = categories.suggester()
        return db.withTransaction {
            val existing = draft.id?.let { dao.get(it) }
            val entity = ExpenseEntity(
                id = existing?.id ?: 0,
                title = draft.title.trim(),
                date = draft.date.toEpochDay(),
                note = draft.note?.trim()?.ifEmpty { null },
                source = existing?.source ?: draft.source,
                receiptTotalBani = draft.receiptTotalBani,
                templateId = existing?.templateId,
                period = existing?.period,
            )
            val id = if (existing != null) {
                dao.update(entity)
                dao.deleteItems(existing.id)
                existing.id
            } else {
                dao.insert(entity)
            }
            dao.insertItems(
                draft.items.mapIndexed { index, item ->
                    ExpenseItemEntity(
                        expenseId = id,
                        name = item.name.trim(),
                        quantityMilli = item.quantityMilli,
                        unitPriceBani = item.unitPriceBani,
                        discountBani = item.discountBani,
                        amountBani = item.amountBani,
                        categoryId = item.categoryId,
                        position = index,
                    )
                },
            )
            val learning = db.learningDao()
            draft.items.forEach { item ->
                if (suggester.suggest(item.name).categoryId != item.categoryId) {
                    val key = TextNormalizer.normalizeItemName(item.name)
                    if (key.isNotEmpty()) learning.upsert(CategoryLearningEntity(key, item.categoryId, now()))
                }
            }
            id
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
