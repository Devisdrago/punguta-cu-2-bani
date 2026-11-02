package ro.punguta.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.BudgetEntity
import ro.punguta.app.domain.money.Money

class BudgetRepository(db: AppDatabase) {
    private val dao = db.budgetDao()

    /** Category id to monthly limit in bani. */
    val budgets: Flow<Map<Long, Long>> = dao.observeAll().map { list -> list.associate { it.categoryId to it.limitBani } }

    /** Sets a monthly limit for a category, or removes it when [limitBani] is null or zero. */
    suspend fun set(categoryId: Long, limitBani: Long?) {
        if (limitBani == null || limitBani <= 0) {
            dao.delete(categoryId)
        } else {
            require(limitBani <= Money.MAX_BANI)
            dao.upsert(BudgetEntity(categoryId, limitBani))
        }
    }
}
