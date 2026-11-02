package ro.punguta.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.domain.category.CategorySuggester
import ro.punguta.app.domain.category.DefaultCategoryKeys
import ro.punguta.app.domain.receipt.ReceiptParser
import ro.punguta.app.ui.expenses.HeaderField
import ro.punguta.app.ui.expenses.ItemField
import java.time.LocalDate

class ReceiptFormMapperTest {
    private val today = LocalDate.parse("2026-11-02")
    private val ids = DefaultCategoryKeys.defaults.mapIndexed { i, (key, _) -> key to (i + 1L) }.toMap()
    private val suggester = CategorySuggester(emptyMap(), ids)

    private fun parse(name: String) =
        ReceiptParser(today).parse(javaClass.getResource("/receipts/$name")!!.readText().lines())

    @Test
    fun `clean receipt maps to a form with categories and no highlights`() {
        val form = ReceiptFormMapper.toForm(parse("kaufland_clean.txt"), suggester, "/tmp/x.jpg", today)
        assertEquals(ExpenseSource.RECEIPT, form.source)
        assertEquals("Kaufland", form.title)
        assertEquals("67,97", form.receiptTotal)
        assertEquals(0L, form.totalDifferenceBani)
        assertEquals("/tmp/x.jpg", form.photoPath)
        assertEquals(
            listOf(DefaultCategoryKeys.FOOD, DefaultCategoryKeys.FOOD, DefaultCategoryKeys.HOME, DefaultCategoryKeys.FOOD).map(ids::getValue),
            form.items.map { it.categoryId },
        )
        assertEquals("2", form.items[0].quantity)
        assertEquals("0,50", form.items[1].discount)
        assertTrue(form.uncertainHeader.isEmpty())
        assertTrue(form.items.all { it.uncertain.isEmpty() })
    }

    @Test
    fun `messy receipt keeps uncertainty for the review screen`() {
        val form = ReceiptFormMapper.toForm(parse("profi_messy_ocr.txt"), suggester, null, today)
        assertTrue(HeaderField.DATE in form.uncertainHeader)
        assertTrue(ItemField.AMOUNT in form.items[0].uncertain)
        assertTrue(ItemField.NAME in form.items[3].uncertain)
        assertTrue(form.hasUncertainty)
    }

    @Test
    fun `missing data stays editable instead of failing`() {
        val form = ReceiptFormMapper.toForm(parse("cropped_no_total.txt"), suggester, null, today)
        assertEquals("", form.title)
        assertEquals("", form.receiptTotal)
        assertEquals(today, form.date)
        assertTrue(form.uncertainHeader.containsAll(listOf(HeaderField.TITLE, HeaderField.DATE, HeaderField.TOTAL)))
        assertEquals(null, form.totalDifferenceBani)

        val empty = ReceiptFormMapper.toForm(ReceiptParser(today).parse(listOf("~~~")), suggester, null, today)
        assertEquals(1, empty.items.size)
        assertTrue(ItemField.AMOUNT in empty.items[0].uncertain)
    }

    @Test
    fun `unknown items get the fallback category highlighted`() {
        val form = ReceiptFormMapper.toForm(ReceiptParser(today).parse(listOf("XQZW 5,00", "TOTAL 5,00")), suggester, null, today)
        assertEquals(ids[DefaultCategoryKeys.OTHER], form.items.single().categoryId)
        assertTrue(ItemField.CATEGORY in form.items.single().uncertain)
    }
}
