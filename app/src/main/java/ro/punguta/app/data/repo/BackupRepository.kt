package ro.punguta.app.data.repo

import androidx.room.withTransaction
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.BudgetEntity
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.data.db.CategoryLearningEntity
import ro.punguta.app.data.db.ExpenseEntity
import ro.punguta.app.data.db.ExpenseItemEntity
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.db.RecurringGenerationEntity
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.SettingEntity
import ro.punguta.app.domain.backup.BACKUP_FORMAT
import ro.punguta.app.domain.backup.BACKUP_VERSION
import ro.punguta.app.domain.backup.BackupCodec
import ro.punguta.app.domain.backup.BackupData
import ro.punguta.app.domain.backup.BackupFile
import ro.punguta.app.domain.backup.BackupProblem
import ro.punguta.app.domain.backup.BackupValidator
import ro.punguta.app.domain.backup.BudgetDto
import ro.punguta.app.domain.backup.CategoryDto
import ro.punguta.app.domain.backup.ExpenseDto
import ro.punguta.app.domain.backup.ExpenseItemDto
import ro.punguta.app.domain.backup.GenerationDto
import ro.punguta.app.domain.backup.IncomeDto
import ro.punguta.app.domain.backup.InvalidBackupException
import ro.punguta.app.domain.backup.LearningDto
import ro.punguta.app.domain.backup.TemplateDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Summary shown before the user confirms a restore. */
data class BackupSummary(
    val createdAt: String,
    val categories: Int,
    val incomes: Int,
    val expenses: Int,
    val templates: Int,
)

class BackupRepository(
    private val db: AppDatabase,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    /** Test seam: runs inside the restore transaction after all rows are inserted. */
    private val beforeCommit: suspend () -> Unit = {},
) {
    private val dao = db.backupDao()

    /** Reads everything inside one transaction, so the snapshot is consistent. */
    suspend fun exportJson(): String {
        val data = db.withTransaction {
            BackupData(
                categories = dao.categories().map { CategoryDto(it.id, it.name, it.defaultKey, it.sortOrder) },
                categoryLearning = dao.learning().map { LearningDto(it.normalizedName, it.categoryId, it.updatedAt) },
                budgets = dao.budgets().map { BudgetDto(it.categoryId, it.limitBani) },
                recurringTemplates = dao.templates().map {
                    TemplateDto(
                        it.id, it.kind, it.name, it.amountBani, it.dayOfMonth, it.categoryId,
                        day(it.startDate), it.endDate?.let(::day), it.note,
                    )
                },
                recurringGenerationLog = dao.generationLog().map { GenerationDto(it.templateId, it.period) },
                incomes = dao.incomes().map {
                    IncomeDto(it.id, it.name, it.amountBani, day(it.date), it.note, it.templateId, it.period)
                },
                expenses = dao.expenses().map {
                    ExpenseDto(it.id, it.title, day(it.date), it.note, it.source, it.receiptTotalBani, it.templateId, it.period)
                },
                expenseItems = dao.expenseItems().map {
                    ExpenseItemDto(
                        it.id, it.expenseId, it.name, it.quantityMilli, it.unitPriceBani, it.discountBani,
                        it.amountBani, it.categoryId, it.position,
                    )
                },
                // The export timestamp describes this phone, not the data.
                settings = dao.settings().filterNot { it.key == SettingsRepository.KEY_LAST_EXPORT }
                    .associate { it.key to it.value },
            )
        }
        return BackupCodec.encode(
            BackupFile(BACKUP_FORMAT, BACKUP_VERSION, now().truncatedTo(ChronoUnit.SECONDS).toString(), data),
        )
    }

    /** Parses and validates without touching the database. */
    fun inspect(json: String): Pair<BackupFile, BackupSummary> {
        val file = BackupCodec.decode(json)
        val d = file.data
        return file to BackupSummary(file.createdAt, d.categories.size, d.incomes.size, d.expenses.size, d.recurringTemplates.size)
    }

    /**
     * Replaces all data with the backup in a single transaction. If anything fails, the
     * transaction rolls back and the existing data stays exactly as it was.
     */
    suspend fun restore(file: BackupFile) {
        BackupValidator.validate(file.data)?.let { throw InvalidBackupException(BackupProblem.INVALID_DATA, it) }
        val d = file.data
        val lastExport = db.settingDao().get(SettingsRepository.KEY_LAST_EXPORT)
        db.withTransaction {
            dao.clearExpenseItems()
            dao.clearExpenses()
            dao.clearIncomes()
            dao.clearGenerationLog()
            dao.clearTemplates()
            dao.clearBudgets()
            dao.clearLearning()
            dao.clearCategories()
            dao.clearSettings()

            dao.insertCategories(d.categories.map { CategoryEntity(it.id, it.name.trim(), it.defaultKey, it.sortOrder) })
            dao.insertLearning(d.categoryLearning.map { CategoryLearningEntity(it.normalizedName, it.categoryId, it.updatedAt) })
            dao.insertBudgets(d.budgets.map { BudgetEntity(it.categoryId, it.limitBani) })
            dao.insertTemplates(
                d.recurringTemplates.map {
                    RecurringTemplateEntity(
                        it.id, it.kind, it.name, it.amountBani, it.dayOfMonth, it.categoryId,
                        epoch(it.startDate), it.endDate?.let(::epoch), it.note,
                    )
                },
            )
            dao.insertGenerationLog(d.recurringGenerationLog.map { RecurringGenerationEntity(it.templateId, it.period) })
            dao.insertIncomes(d.incomes.map { IncomeEntity(it.id, it.name, it.amountBani, epoch(it.date), it.note, it.templateId, it.period) })
            dao.insertExpenses(
                d.expenses.map {
                    ExpenseEntity(it.id, it.title, epoch(it.date), it.note, it.source, it.receiptTotalBani, it.templateId, it.period)
                },
            )
            dao.insertExpenseItems(
                d.expenseItems.map {
                    ExpenseItemEntity(
                        it.id, it.expenseId, it.name, it.quantityMilli, it.unitPriceBani, it.discountBani,
                        it.amountBani, it.categoryId, it.position,
                    )
                },
            )
            val settings = d.settings.filterKeys { it != SettingsRepository.KEY_LAST_EXPORT }
                .map { SettingEntity(it.key, it.value) }.toMutableList()
            if (lastExport != null) settings += SettingEntity(SettingsRepository.KEY_LAST_EXPORT, lastExport)
            dao.insertSettings(settings)
            beforeCommit()
        }
    }

    private fun day(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

    private fun epoch(iso: String) = LocalDate.parse(iso).toEpochDay()
}
