package ro.punguta.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ro.punguta.app.PungutaApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily background catch-up for recurring entries. The same catch-up also runs whenever the app
 * comes to the foreground, so missed runs (phone off, battery restrictions) are filled in later.
 */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PungutaApp).container.recurring.generateDue(LocalDate.now())
        Result.success()
    } catch (e: Exception) {
        // Deliberately no logging: messages could contain financial data.
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "recurring-entries"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
