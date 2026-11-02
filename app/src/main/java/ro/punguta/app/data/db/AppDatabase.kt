package ro.punguta.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ro.punguta.app.domain.category.DefaultCategoryKeys

@Database(
    entities = [
        CategoryEntity::class,
        CategoryLearningEntity::class,
        BudgetEntity::class,
        RecurringTemplateEntity::class,
        RecurringGenerationEntity::class,
        IncomeEntity::class,
        ExpenseEntity::class,
        ExpenseItemEntity::class,
        SettingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun learningDao(): LearningDao
    abstract fun budgetDao(): BudgetDao
    abstract fun settingDao(): SettingDao
    abstract fun templateDao(): TemplateDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "punguta.db"

        /**
         * Every schema change must add a Migration here and bump [VERSION].
         * Destructive fallback is deliberately never enabled.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            configure(Room.databaseBuilder(context, AppDatabase::class.java, NAME)).build()

        fun configure(builder: Builder<AppDatabase>): Builder<AppDatabase> =
            builder.addMigrations(*MIGRATIONS).addCallback(SeedCallback)

        /** Inserts the default Romanian categories when the database is first created. */
        fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            DefaultCategoryKeys.defaults.forEachIndexed { index, (key, name) ->
                db.execSQL(
                    "INSERT INTO categories (name, defaultKey, sortOrder) VALUES (?, ?, ?)",
                    arrayOf<Any>(name, key, index),
                )
            }
        }
    }

    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            seedDefaultCategories(db)
        }
    }
}
