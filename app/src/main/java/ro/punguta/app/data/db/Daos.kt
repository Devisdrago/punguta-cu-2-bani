package ro.punguta.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM expense_items WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM recurring_templates WHERE categoryId = :id)",
    )
    suspend fun usageCount(id: Long): Int

    @Query("UPDATE expense_items SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveItems(from: Long, to: Long)

    @Query("UPDATE recurring_templates SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveTemplates(from: Long, to: Long)

    @Query("UPDATE category_learning SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveLearning(from: Long, to: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM category_learning")
    suspend fun getAll(): List<CategoryLearningEntity>

    @Upsert
    suspend fun upsert(entry: CategoryLearningEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(setting: SettingEntity)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM recurring_templates ORDER BY kind, name")
    fun observeAll(): Flow<List<RecurringTemplateEntity>>

    @Query("SELECT * FROM recurring_templates")
    suspend fun getAll(): List<RecurringTemplateEntity>

    @Query("SELECT * FROM recurring_templates WHERE id = :id")
    suspend fun get(id: Long): RecurringTemplateEntity?

    @Insert
    suspend fun insert(template: RecurringTemplateEntity): Long

    @Update
    suspend fun update(template: RecurringTemplateEntity)

    @Query("DELETE FROM recurring_templates WHERE id = :id")
    suspend fun delete(id: Long)

    /** Returns -1 when this (template, period) was already generated. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun logGeneration(entry: RecurringGenerationEntity): Long

    @Query("SELECT period FROM recurring_generation_log WHERE templateId = :templateId")
    suspend fun generatedPeriods(templateId: Long): List<String>
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    fun observeRange(start: Long, end: Long): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun get(id: Long): IncomeEntity?

    @Insert
    suspend fun insert(income: IncomeEntity): Long

    /** Generated entries rely on the unique (templateId, period) index as a second guard. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenerated(income: IncomeEntity): Long

    @Update
    suspend fun update(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExpenseDao {
    @Query(
        "SELECT e.*, " +
            "COALESCE((SELECT SUM(i.amountBani) FROM expense_items i WHERE i.expenseId = e.id), 0) AS itemsTotal, " +
            "(SELECT COUNT(*) FROM expense_items i WHERE i.expenseId = e.id) AS itemCount " +
            "FROM expenses e WHERE e.date BETWEEN :start AND :end ORDER BY e.date DESC, e.id DESC",
    )
    fun observeRange(start: Long, end: Long): Flow<List<ExpenseWithTotal>>

    @Query(
        "SELECT i.*, e.date AS date, e.title AS title FROM expense_items i " +
            "JOIN expenses e ON e.id = i.expenseId WHERE e.date BETWEEN :start AND :end " +
            "ORDER BY e.date DESC, i.expenseId DESC, i.position",
    )
    fun observeItemsRange(start: Long, end: Long): Flow<List<ItemWithExpense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expense_items WHERE expenseId = :expenseId ORDER BY position")
    suspend fun items(expenseId: Long): List<ExpenseItemEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenerated(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Insert
    suspend fun insertItems(items: List<ExpenseItemEntity>)

    @Query("DELETE FROM expense_items WHERE expenseId = :expenseId")
    suspend fun deleteItems(expenseId: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Whole-table access used only by backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM category_learning") suspend fun learning(): List<CategoryLearningEntity>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM recurring_templates") suspend fun templates(): List<RecurringTemplateEntity>
    @Query("SELECT * FROM recurring_generation_log") suspend fun generationLog(): List<RecurringGenerationEntity>
    @Query("SELECT * FROM incomes") suspend fun incomes(): List<IncomeEntity>
    @Query("SELECT * FROM expenses") suspend fun expenses(): List<ExpenseEntity>
    @Query("SELECT * FROM expense_items") suspend fun expenseItems(): List<ExpenseItemEntity>
    @Query("SELECT * FROM settings") suspend fun settings(): List<SettingEntity>

    // Deletes run children first so foreign keys stay satisfied inside the restore transaction.
    @Query("DELETE FROM expense_items") suspend fun clearExpenseItems()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM incomes") suspend fun clearIncomes()
    @Query("DELETE FROM recurring_generation_log") suspend fun clearGenerationLog()
    @Query("DELETE FROM recurring_templates") suspend fun clearTemplates()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM category_learning") suspend fun clearLearning()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Insert suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert suspend fun insertLearning(rows: List<CategoryLearningEntity>)
    @Insert suspend fun insertBudgets(rows: List<BudgetEntity>)
    @Insert suspend fun insertTemplates(rows: List<RecurringTemplateEntity>)
    @Insert suspend fun insertGenerationLog(rows: List<RecurringGenerationEntity>)
    @Insert suspend fun insertIncomes(rows: List<IncomeEntity>)
    @Insert suspend fun insertExpenses(rows: List<ExpenseEntity>)
    @Insert suspend fun insertExpenseItems(rows: List<ExpenseItemEntity>)
    @Insert suspend fun insertSettings(rows: List<SettingEntity>)
}
