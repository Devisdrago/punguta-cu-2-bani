package ro.punguta.app.data.repo

import kotlinx.coroutines.flow.Flow
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.stats.epochDayRange
import java.time.YearMonth

enum class IncomeError { NAME_EMPTY, AMOUNT_INVALID }

class InvalidIncomeException(val error: IncomeError) : Exception(error.name)

class IncomeRepository(db: AppDatabase) {
    private val dao = db.incomeDao()

    fun observeMonth(month: YearMonth): Flow<List<IncomeEntity>> = observeRange(month, month)

    fun observeRange(from: YearMonth, to: YearMonth): Flow<List<IncomeEntity>> =
        dao.observeRange(from.epochDayRange().first, to.epochDayRange().last)

    suspend fun get(id: Long): IncomeEntity? = dao.get(id)

    /**
     * Inserts or updates one income entry. Updating a generated entry changes only that month:
     * its template link and period are kept, so the template and other months are untouched.
     */
    suspend fun save(income: IncomeEntity): Long {
        val name = income.name.trim()
        if (name.isEmpty()) throw InvalidIncomeException(IncomeError.NAME_EMPTY)
        if (income.amountBani <= 0 || income.amountBani > Money.MAX_BANI) {
            throw InvalidIncomeException(IncomeError.AMOUNT_INVALID)
        }
        val clean = income.copy(name = name, note = income.note?.trim()?.ifEmpty { null })
        return if (clean.id == 0L) {
            dao.insert(clean)
        } else {
            val existing = dao.get(clean.id)
            dao.update(clean.copy(templateId = existing?.templateId, period = existing?.period))
            clean.id
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
