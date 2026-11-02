package ro.punguta.app.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.ExpenseEntity
import ro.punguta.app.data.db.ExpenseItemEntity
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.db.RecurringGenerationEntity
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.recurring.RecurrenceRules
import java.time.LocalDate

enum class TemplateError { NAME_EMPTY, AMOUNT_INVALID, DAY_INVALID, CATEGORY_MISSING }

class InvalidTemplateException(val error: TemplateError) : Exception(error.name)

class RecurringRepository(private val db: AppDatabase) {
    private val dao = db.templateDao()
    private val incomeDao = db.incomeDao()
    private val expenseDao = db.expenseDao()

    /** Serializes catch-up runs from the worker and from app start within this process. */
    private val generationLock = Mutex()

    val templates: Flow<List<RecurringTemplateEntity>> = dao.observeAll()

    suspend fun get(id: Long): RecurringTemplateEntity? = dao.get(id)

    /**
     * Inserts or updates a template. Already generated entries are never touched, so an edit
     * affects only months that have not been generated yet.
     */
    suspend fun save(template: RecurringTemplateEntity): Long {
        val name = template.name.trim()
        when {
            name.isEmpty() -> throw InvalidTemplateException(TemplateError.NAME_EMPTY)
            template.amountBani <= 0 || template.amountBani > Money.MAX_BANI ->
                throw InvalidTemplateException(TemplateError.AMOUNT_INVALID)
            template.dayOfMonth !in 1..31 -> throw InvalidTemplateException(TemplateError.DAY_INVALID)
            template.kind == TemplateKind.EXPENSE && template.categoryId == null ->
                throw InvalidTemplateException(TemplateError.CATEGORY_MISSING)
        }
        val clean = template.copy(
            name = name,
            note = template.note?.trim()?.ifEmpty { null },
            categoryId = if (template.kind == TemplateKind.INCOME) null else template.categoryId,
        )
        if (clean.id == 0L) return dao.insert(clean)
        // The start date is fixed after creation so an edit can never backfill past months.
        val existing = dao.get(clean.id) ?: return dao.insert(clean.copy(id = 0))
        dao.update(clean.copy(startDate = existing.startDate))
        return clean.id
    }

    /** Stops future generation from [today] on. Entries already generated stay. */
    suspend fun stop(id: Long, today: LocalDate) {
        val t = dao.get(id) ?: return
        dao.update(t.copy(endDate = today.toEpochDay()))
    }

    /** Resumes a stopped template; months skipped while it was stopped are not backfilled. */
    suspend fun resume(id: Long, today: LocalDate) = db.withTransaction {
        val t = dao.get(id) ?: return@withTransaction
        val stoppedAt = t.endDate ?: return@withTransaction
        val due = RecurrenceRules.dueOccurrences(
            t.dayOfMonth, LocalDate.ofEpochDay(t.startDate), null, today,
        ).filter { it.date.toEpochDay() > stoppedAt }
        due.forEach { dao.logGeneration(RecurringGenerationEntity(t.id, it.periodKey)) }
        dao.update(t.copy(endDate = null))
    }

    /** Deletes a template. Generated entries stay, detached from it. */
    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Creates every due entry that has not been generated yet, up to and including [today].
     * Safe to call any number of times: each (template, month) is recorded in the generation log
     * inside the same transaction, and the entry tables have a unique (templateId, period) index.
     * Returns how many entries were created.
     */
    suspend fun generateDue(today: LocalDate): Int = generationLock.withLock {
        db.withTransaction {
            var created = 0
            for (t in dao.getAll()) {
                val due = RecurrenceRules.dueOccurrences(
                    t.dayOfMonth,
                    LocalDate.ofEpochDay(t.startDate),
                    t.endDate?.let(LocalDate::ofEpochDay),
                    today,
                )
                if (due.isEmpty()) continue
                val done = dao.generatedPeriods(t.id).toHashSet()
                for (o in due) {
                    if (o.periodKey in done) continue
                    if (t.kind == TemplateKind.EXPENSE && t.categoryId == null) continue
                    if (dao.logGeneration(RecurringGenerationEntity(t.id, o.periodKey)) == -1L) continue
                    val date = o.date.toEpochDay()
                    if (t.kind == TemplateKind.INCOME) {
                        val id = incomeDao.insertGenerated(
                            IncomeEntity(
                                name = t.name, amountBani = t.amountBani, date = date, note = t.note,
                                templateId = t.id, period = o.periodKey,
                            ),
                        )
                        if (id != -1L) created++
                    } else {
                        val id = expenseDao.insertGenerated(
                            ExpenseEntity(
                                title = t.name, date = date, note = t.note, source = ExpenseSource.RECURRING,
                                receiptTotalBani = null, templateId = t.id, period = o.periodKey,
                            ),
                        )
                        if (id != -1L) {
                            expenseDao.insertItems(
                                listOf(
                                    ExpenseItemEntity(
                                        expenseId = id, name = t.name, quantityMilli = null, unitPriceBani = null,
                                        discountBani = 0, amountBani = t.amountBani, categoryId = t.categoryId!!,
                                        position = 0,
                                    ),
                                ),
                            )
                            created++
                        }
                    }
                }
            }
            created
        }
    }
}
