package ro.punguta.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates are stored as LocalDate epoch days, money as integer bani.

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true), Index(value = ["defaultKey"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Stable key of a built-in category (see DefaultCategoryKeys); null for user categories. */
    val defaultKey: String?,
    val sortOrder: Int,
)

/** The user's category corrections, keyed by normalized item name. */
@Entity(
    tableName = "category_learning",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class CategoryLearningEntity(
    @PrimaryKey val normalizedName: String,
    val categoryId: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    /** Monthly limit, applied to every month. */
    val limitBani: Long,
)

object TemplateKind {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
}

@Entity(
    tableName = "recurring_templates",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("categoryId")],
)
data class RecurringTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val amountBani: Long,
    val dayOfMonth: Int,
    /** Required for expense templates, null for income. */
    val categoryId: Long?,
    val startDate: Long,
    /** Set when the template is stopped; past entries are kept. */
    val endDate: Long?,
    val note: String?,
)

/**
 * Record of every (template, month) that has been generated. It is never removed when the user
 * deletes the generated entry, so a deleted entry is not recreated by the next catch-up run.
 */
@Entity(
    tableName = "recurring_generation_log",
    primaryKeys = ["templateId", "period"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecurringGenerationEntity(
    val templateId: Long,
    /** Calendar month as "yyyy-MM". */
    val period: String,
)

@Entity(
    tableName = "incomes",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index(value = ["templateId", "period"], unique = true)],
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountBani: Long,
    val date: Long,
    val note: String?,
    val templateId: Long? = null,
    val period: String? = null,
)

object ExpenseSource {
    const val MANUAL = "MANUAL"
    const val RECEIPT = "RECEIPT"
    const val RECURRING = "RECURRING"
}

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index(value = ["templateId", "period"], unique = true)],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Merchant for receipts, a short description otherwise. */
    val title: String,
    val date: Long,
    val note: String?,
    val source: String,
    /** Total printed on the receipt, kept for reference; spending is always the sum of items. */
    val receiptTotalBani: Long?,
    val templateId: Long? = null,
    val period: String? = null,
)

@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("expenseId"), Index("categoryId")],
)
data class ExpenseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val name: String,
    /** Quantity in thousandths (1000 = 1 piece, 1500 = 1,5 kg), null when unknown. */
    val quantityMilli: Long?,
    val unitPriceBani: Long?,
    /** Discount already subtracted from [amountBani]. */
    val discountBani: Long,
    /** Net amount paid for this line. */
    val amountBani: Long,
    val categoryId: Long,
    val position: Int,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** An expense row together with the sum of its items. */
data class ExpenseWithTotal(
    @Embedded val expense: ExpenseEntity,
    val itemsTotal: Long,
    val itemCount: Int,
)

/** An item joined with its expense's date and title, for monthly aggregation. */
data class ItemWithExpense(
    @Embedded val item: ExpenseItemEntity,
    val date: Long,
    val title: String,
)
