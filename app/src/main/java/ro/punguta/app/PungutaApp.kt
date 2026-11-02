package ro.punguta.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.ocr.ReceiptOcr
import ro.punguta.app.data.ocr.ReceiptPhotos
import ro.punguta.app.data.repo.BackupRepository
import ro.punguta.app.data.repo.BudgetRepository
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.data.repo.IncomeRepository
import ro.punguta.app.data.repo.RecurringRepository
import ro.punguta.app.data.repo.SettingsRepository
import ro.punguta.app.security.AppLock
import ro.punguta.app.ui.expenses.PendingDraftStore
import ro.punguta.app.work.RecurringWorker
import java.time.LocalDate

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context, scope: CoroutineScope) {
    val appContext: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.build(context)
    val categories = CategoryRepository(db)
    val settings = SettingsRepository(db)
    val incomes = IncomeRepository(db)
    val expenses = ExpenseRepository(db, categories)
    val recurring = RecurringRepository(db)
    val budgets = BudgetRepository(db)
    val pendingDrafts = PendingDraftStore()
    val receiptPhotos = ReceiptPhotos(context)
    val ocr = ReceiptOcr()
    val backup = BackupRepository(db)
    val appLock = AppLock(settings.lockEnabled, scope)
}

open class PungutaApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.appLock)
        RecurringWorker.schedule(this)
        appScope.launch { runCatching { container.receiptPhotos.clearAll() } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Catch up on recurring entries every time the app comes to the foreground.
                appScope.launch { runCatching { container.recurring.generateDue(LocalDate.now()) } }
            }
        })
    }
}
