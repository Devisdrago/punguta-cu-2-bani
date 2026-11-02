package ro.punguta.app.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.text.TextNormalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.Locale

// Versioned, human-readable backup format. Money is integer bani, dates are ISO yyyy-MM-dd.
// Bump BACKUP_VERSION and add an upgrade step in BackupCodec.decode when the format changes.

const val BACKUP_FORMAT = "punguta-cu-2-bani-backup"
const val BACKUP_VERSION = 1

@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    /** ISO-8601 local date-time of the export. */
    val createdAt: String,
    val data: BackupData,
)

@Serializable
data class BackupData(
    val categories: List<CategoryDto>,
    val categoryLearning: List<LearningDto>,
    val budgets: List<BudgetDto>,
    val recurringTemplates: List<TemplateDto>,
    val recurringGenerationLog: List<GenerationDto>,
    val incomes: List<IncomeDto>,
    val expenses: List<ExpenseDto>,
    val expenseItems: List<ExpenseItemDto>,
    val settings: Map<String, String>,
)

@Serializable data class CategoryDto(val id: Long, val name: String, val defaultKey: String?, val sortOrder: Int)
@Serializable data class LearningDto(val normalizedName: String, val categoryId: Long, val updatedAt: Long)
@Serializable data class BudgetDto(val categoryId: Long, val limitBani: Long)

@Serializable
data class TemplateDto(
    val id: Long,
    val kind: String,
    val name: String,
    val amountBani: Long,
    val dayOfMonth: Int,
    val categoryId: Long?,
    val startDate: String,
    val endDate: String?,
    val note: String?,
)

@Serializable data class GenerationDto(val templateId: Long, val period: String)

@Serializable
data class IncomeDto(
    val id: Long,
    val name: String,
    val amountBani: Long,
    val date: String,
    val note: String?,
    val templateId: Long?,
    val period: String?,
)

@Serializable
data class ExpenseDto(
    val id: Long,
    val title: String,
    val date: String,
    val note: String?,
    val source: String,
    val receiptTotalBani: Long?,
    val templateId: Long?,
    val period: String?,
)

@Serializable
data class ExpenseItemDto(
    val id: Long,
    val expenseId: Long,
    val name: String,
    val quantityMilli: Long?,
    val unitPriceBani: Long?,
    val discountBani: Long,
    val amountBani: Long,
    val categoryId: Long,
    val position: Int,
)

/** Why a backup file was rejected. Nothing is changed when any of these occur. */
enum class BackupProblem { NOT_JSON, NOT_A_BACKUP, NEWER_VERSION, INVALID_DATA }

class InvalidBackupException(val problem: BackupProblem, val detail: String) : Exception("$problem: $detail")

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses and fully validates a backup. Throws [InvalidBackupException]; never returns bad data. */
    fun decode(text: String): BackupFile {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw InvalidBackupException(BackupProblem.NOT_JSON, "malformed JSON")
        }
        val obj = element as? JsonObject
            ?: throw InvalidBackupException(BackupProblem.NOT_A_BACKUP, "not an object")
        val format = (obj["format"] as? JsonPrimitive)?.content
        if (format != BACKUP_FORMAT) throw InvalidBackupException(BackupProblem.NOT_A_BACKUP, "unknown format")
        val version = (obj["version"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw InvalidBackupException(BackupProblem.NOT_A_BACKUP, "missing version")
        if (version > BACKUP_VERSION) throw InvalidBackupException(BackupProblem.NEWER_VERSION, "version $version")
        if (version < 1) throw InvalidBackupException(BackupProblem.NOT_A_BACKUP, "version $version")
        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), element)
        } catch (e: SerializationException) {
            throw InvalidBackupException(BackupProblem.INVALID_DATA, "structure")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException(BackupProblem.INVALID_DATA, "structure")
        }
        BackupValidator.validate(file.data)?.let { throw InvalidBackupException(BackupProblem.INVALID_DATA, it) }
        return file
    }
}

/** Checks values and every reference so a restore can never insert inconsistent data. */
object BackupValidator {
    private val kinds = setOf("INCOME", "EXPENSE")
    private val sources = setOf("MANUAL", "RECEIPT", "RECURRING")

    /** Returns a short description of the first problem, or null when the data is consistent. */
    fun validate(d: BackupData): String? {
        val categoryIds = d.categories.map { it.id }
        if (d.categories.isEmpty()) return "no categories"
        if (categoryIds.toSet().size != categoryIds.size) return "duplicate category id"
        val folded = d.categories.map { TextNormalizer.stripDiacritics(it.name.trim()).lowercase(Locale.ROOT) }
        if (folded.any { it.isEmpty() } || folded.toSet().size != folded.size) return "category names"
        val keys = d.categories.mapNotNull { it.defaultKey }
        if (keys.toSet().size != keys.size) return "duplicate default key"
        val cats = categoryIds.toSet()

        if (d.categoryLearning.any { it.categoryId !in cats || it.normalizedName.isBlank() }) return "learning"
        if (d.categoryLearning.map { it.normalizedName }.toSet().size != d.categoryLearning.size) return "learning duplicate"
        if (d.budgets.any { it.categoryId !in cats || it.limitBani !in 1..Money.MAX_BANI }) return "budgets"
        if (d.budgets.map { it.categoryId }.toSet().size != d.budgets.size) return "budget duplicate"

        val templateIds = d.recurringTemplates.map { it.id }
        if (templateIds.toSet().size != templateIds.size) return "duplicate template id"
        val templates = templateIds.toSet()
        for (t in d.recurringTemplates) {
            if (t.kind !in kinds || t.name.isBlank() || t.amountBani !in 1..Money.MAX_BANI || t.dayOfMonth !in 1..31) return "template ${t.id}"
            if (t.kind == "EXPENSE" && (t.categoryId == null || t.categoryId !in cats)) return "template ${t.id} category"
            if (t.kind == "INCOME" && t.categoryId != null) return "template ${t.id} category"
            if (!isDate(t.startDate) || (t.endDate != null && !isDate(t.endDate))) return "template ${t.id} dates"
        }
        if (d.recurringGenerationLog.any { it.templateId !in templates || !isPeriod(it.period) }) return "generation log"
        if (d.recurringGenerationLog.toSet().size != d.recurringGenerationLog.size) return "generation log duplicate"

        if (!uniqueIds(d.incomes.map { it.id })) return "duplicate income id"
        for (i in d.incomes) {
            if (i.name.isBlank() || i.amountBani !in 1..Money.MAX_BANI || !isDate(i.date)) return "income ${i.id}"
            if (!linkOk(i.templateId, i.period, templates)) return "income ${i.id} template"
        }
        if (!uniqueLinks(d.incomes.mapNotNull { i -> i.templateId?.let { it to i.period } })) return "income duplicate month"

        if (!uniqueIds(d.expenses.map { it.id })) return "duplicate expense id"
        for (e in d.expenses) {
            if (e.title.isBlank() || e.source !in sources || !isDate(e.date)) return "expense ${e.id}"
            if (e.receiptTotalBani != null && e.receiptTotalBani !in 0..Money.MAX_BANI) return "expense ${e.id} total"
            if (!linkOk(e.templateId, e.period, templates)) return "expense ${e.id} template"
        }
        if (!uniqueLinks(d.expenses.mapNotNull { e -> e.templateId?.let { it to e.period } })) return "expense duplicate month"

        val expenseIds = d.expenses.map { it.id }.toSet()
        if (!uniqueIds(d.expenseItems.map { it.id })) return "duplicate item id"
        for (it in d.expenseItems) {
            if (it.expenseId !in expenseIds || it.categoryId !in cats) return "item ${it.id} reference"
            if (it.name.isBlank() || it.amountBani !in 0..Money.MAX_BANI || it.discountBani !in 0..Money.MAX_BANI) return "item ${it.id}"
            if (it.quantityMilli != null && it.quantityMilli <= 0) return "item ${it.id} quantity"
        }
        return null
    }

    private fun uniqueIds(ids: List<Long>) = ids.toSet().size == ids.size

    private fun uniqueLinks(links: List<Pair<Long, String?>>) = links.toSet().size == links.size

    private fun linkOk(templateId: Long?, period: String?, templates: Set<Long>): Boolean = when {
        templateId == null -> true // detached from a deleted template; period is kept for reference
        templateId !in templates -> false
        else -> period != null && isPeriod(period)
    } && (period == null || isPeriod(period))

    private fun isDate(s: String): Boolean = try {
        LocalDate.parse(s).year in 1900..2200
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isPeriod(s: String): Boolean = try {
        YearMonth.parse(s).year in 1900..2200
    } catch (e: DateTimeParseException) {
        false
    }
}
