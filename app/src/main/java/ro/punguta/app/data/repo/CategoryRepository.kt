package ro.punguta.app.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ro.punguta.app.data.db.AppDatabase
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.data.db.CategoryLearningEntity
import ro.punguta.app.domain.category.CategorySuggester
import ro.punguta.app.domain.text.TextNormalizer
import java.util.Locale

enum class CategoryNameError { EMPTY, TOO_LONG, DUPLICATE }

class CategoryNameException(val error: CategoryNameError) : Exception(error.name)

/** Thrown when deleting a category that is in use without choosing where its items go. */
class CategoryInUseException(val usage: Int) : Exception("Category in use")

class CategoryRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.categoryDao()
    private val learningDao = db.learningDao()

    val categories: Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun all(): List<CategoryEntity> = dao.getAll()

    suspend fun add(rawName: String): Long = db.withTransaction {
        val name = validateName(rawName, excludeId = null)
        dao.insert(CategoryEntity(name = name, defaultKey = null, sortOrder = dao.maxSortOrder() + 1))
    }

    suspend fun rename(id: Long, rawName: String) = db.withTransaction {
        val name = validateName(rawName, excludeId = id)
        dao.rename(id, name)
    }

    suspend fun usageCount(id: Long): Int = dao.usageCount(id)

    /**
     * Deletes a category. If it is used by expense items or recurring templates, [moveTo]
     * must name another category; everything (including learned corrections) moves there first.
     */
    suspend fun delete(id: Long, moveTo: Long?) = db.withTransaction {
        val usage = dao.usageCount(id)
        if (usage > 0) {
            if (moveTo == null || moveTo == id || dao.get(moveTo) == null) throw CategoryInUseException(usage)
            dao.moveItems(id, moveTo)
            dao.moveTemplates(id, moveTo)
        }
        if (moveTo != null && moveTo != id) dao.moveLearning(id, moveTo)
        dao.delete(id)
    }

    suspend fun suggester(): CategorySuggester {
        val learned = learningDao.getAll().associate { it.normalizedName to it.categoryId }
        val byKey = dao.getAll().mapNotNull { c -> c.defaultKey?.let { it to c.id } }.toMap()
        return CategorySuggester(learned, byKey)
    }

    /**
     * Remembers the user's choice for an item name. Called only when the user picked a category
     * different from the suggestion, so corrections override the default rules next time.
     */
    suspend fun learn(itemName: String, categoryId: Long) {
        val key = TextNormalizer.normalizeItemName(itemName)
        if (key.isEmpty()) return
        learningDao.upsert(CategoryLearningEntity(key, categoryId, now()))
    }

    private suspend fun validateName(raw: String, excludeId: Long?): String {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw CategoryNameException(CategoryNameError.EMPTY)
        if (name.length > MAX_NAME) throw CategoryNameException(CategoryNameError.TOO_LONG)
        val folded = fold(name)
        val clash = dao.getAll().any { it.id != excludeId && fold(it.name) == folded }
        if (clash) throw CategoryNameException(CategoryNameError.DUPLICATE)
        return name
    }

    private fun fold(s: String) = TextNormalizer.stripDiacritics(s).lowercase(Locale.ROOT)

    companion object {
        const val MAX_NAME = 40
    }
}
