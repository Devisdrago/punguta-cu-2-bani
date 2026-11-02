package ro.punguta.app.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.parse("2026-11-02")
    private val parser = ReceiptParser(today)

    private fun fixture(name: String): ParsedReceipt {
        val text = javaClass.getResource("/receipts/$name")!!.readText(Charsets.UTF_8)
        return parser.parse(text.lines())
    }

    private fun ParsedReceipt.amounts() = items.map { it.amountBani }
    private fun ParsedReceipt.names() = items.map { it.name }

    @Test
    fun `kaufland - quantity line above item, item discount, VAT and payment ignored`() {
        val r = fixture("kaufland_clean.txt")
        assertEquals(Parsed("Kaufland", true), r.merchant)
        assertEquals(Parsed(LocalDate.parse("2026-11-02"), true), r.date)
        assertEquals(
            listOf("LAPTE ZUZU 1,5% 1L", "PAINE FELIATA 500G", "DETERGENT ARIEL 2KG", "ROSII CHERRY 250G"),
            r.names(),
        )
        assertEquals(listOf(1100L, 299L, 4599L, 799L), r.amounts())
        assertEquals(2000L, r.items[0].quantityMilli)
        assertEquals(550L, r.items[0].unitPriceBani)
        assertEquals(50L, r.items[1].discountBani)
        assertEquals(Parsed<Long?>(6797, true), r.total)
        assertEquals(6797L, r.subtotalBani)
        assertEquals(r.total.value, r.itemsTotalBani)
        assertTrue(r.items.all { it.amountCertain && it.nameCertain })
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun `lidl - weighed item with name on previous row and loyalty discount`() {
        val r = fixture("lidl_weighed.txt")
        assertEquals("Lidl", r.merchant.value)
        assertEquals(LocalDate.parse("2026-11-02"), r.date.value)
        assertEquals(listOf("Lapte Pilos 3,5%", "Banane", "Iaurt grecesc", "Bere Pilsner 0,5L"), r.names())
        assertEquals(listOf(599L, 801L, 758L, 329L), r.amounts())
        assertEquals(1234L, r.items[1].quantityMilli)
        assertEquals(649L, r.items[1].unitPriceBani)
        assertEquals(2000L, r.items[2].quantityMilli)
        assertEquals(100L, r.items[2].discountBani)
        assertEquals(2487L, r.total.value)
        assertEquals(r.total.value, r.itemsTotalBani)
        assertTrue(r.items.all { it.amountCertain })
    }

    @Test
    fun `mega image - dot decimals, quantity with total above the name, percent discount`() {
        val r = fixture("mega_dot_decimals.txt")
        assertEquals("Mega Image", r.merchant.value)
        assertEquals(LocalDate.parse("2026-11-01"), r.date.value)
        assertEquals(listOf("APA PLATA DORNA 2L", "COCA COLA 0.5L", "PAINE INTEGRALA"), r.names())
        assertEquals(listOf(998L, 450L, 558L), r.amounts())
        assertEquals(2000L, r.items[0].quantityMilli)
        assertEquals(2006L, r.total.value)
        assertEquals(2068L, r.subtotalBani)
        assertEquals(r.total.value, r.itemsTotalBani)
    }

    @Test
    fun `profi - messy OCR is repaired but flagged as uncertain`() {
        val r = fixture("profi_messy_ocr.txt")
        assertEquals("Profi", r.merchant.value)
        assertEquals(listOf(250L, 649L, 360L, 1899L), r.amounts())
        assertEquals(3000L, r.items[2].quantityMilli)
        assertEquals(120L, r.items[2].unitPriceBani)
        assertEquals(Parsed<Long?>(3158, true), r.total) // "T0TAL" recognized as TOTAL
        assertEquals(r.total.value, r.itemsTotalBani)

        assertFalse("2,5O needed O->0", r.items[0].amountCertain)
        assertTrue("6, 49 is only a spacing problem", r.items[1].amountCertain)
        assertFalse(r.items[2].amountCertain)
        assertFalse("I8,99 needed I->1", r.items[3].amountCertain)
        assertFalse("C4SCAVAL looks misread", r.items[3].nameCertain)
        assertTrue(r.items[0].nameCertain)

        assertEquals(LocalDate.parse("2026-10-30"), r.date.value)
        assertFalse(r.date.certain)
    }

    @Test
    fun `catena - diacritics with cedilla, sizes inside names, cash and change ignored`() {
        val r = fixture("catena_diacritics.txt")
        assertEquals("Catena", r.merchant.value)
        assertEquals(
            listOf("NUROFEN FORTE 400MG 12CPR", "VITAMINA C 1000MG 20CPR", "PLASTURI 20 BUC", "SERVEŢELE UMEDE"),
            r.names(),
        )
        assertEquals(listOf(2150L, 1830L, 990L, 640L), r.amounts())
        assertEquals(5610L, r.total.value)
        assertNull(r.items[2].quantityMilli)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun `carrefour - discount after subtotal is spread over items and sums exactly`() {
        val r = fixture("carrefour_receipt_discount.txt")
        assertEquals("Carrefour", r.merchant.value)
        assertEquals(listOf(100L, 55L, 250L), r.items.map { it.discountBani })
        assertEquals(listOf(899L, 494L, 2249L), r.amounts())
        assertEquals(3642L, r.total.value)
        assertEquals(r.total.value, r.itemsTotalBani)
        assertTrue(ReceiptWarning.RECEIPT_DISCOUNT_SPREAD in r.warnings)
        assertTrue(r.items.none { it.discountCertain })
    }

    @Test
    fun `cropped photo - partial data is returned with warnings, never thrown away`() {
        val r = fixture("cropped_no_total.txt")
        assertEquals(listOf("ARDEI KAPIA", "CEAPA ROSIE"), r.names())
        assertEquals(listOf(740L, 219L), r.amounts())
        assertFalse(r.items[0].amountCertain)
        assertNull(r.total.value)
        assertNull(r.merchant.value)
        assertNull(r.date.value)
        assertTrue(r.warnings.containsAll(setOf(ReceiptWarning.NO_TOTAL, ReceiptWarning.NO_MERCHANT, ReceiptWarning.NO_DATE)))
    }

    @Test
    fun `petrom - fuel litres times price per litre`() {
        val r = fixture("petrom_fuel.txt")
        assertEquals("Petrom", r.merchant.value)
        assertEquals(listOf("MOTORINA STANDARD", "CAFEA ESPRESSO"), r.names())
        assertEquals(listOf(24080L, 800L), r.amounts())
        assertEquals(32150L, r.items[0].quantityMilli)
        assertEquals(749L, r.items[0].unitPriceBani)
        assertEquals(24880L, r.total.value)
        assertEquals(LocalDate.parse("2026-11-01"), r.date.value)
    }

    @Test
    fun `unknown shop - merchant from first line, total on the following line`() {
        val r = fixture("unknown_shop_total_on_next_line.txt")
        assertEquals(Parsed<String?>("Alimentara La Colt", false), r.merchant)
        assertEquals(listOf("Pâine de casă", "Brânză de vaci 500g", "Smântână 20%"), r.names())
        assertEquals(2420L, r.total.value)
        assertEquals(r.total.value, r.itemsTotalBani)
        assertEquals(LocalDate.parse("2026-10-31"), r.date.value)
    }

    @Test
    fun `VAT, payment and change lines are never items`() {
        val r = parser.parse(
            listOf(
                "PAINE 3,00 A",
                "TOTAL 3,00",
                "TVA A 19% 0,48",
                "CARD 3,00",
                "NUMERAR 10,00",
                "REST 7,00",
            ),
        )
        assertEquals(listOf("PAINE"), r.names())
        assertEquals(300L, r.total.value)
    }

    @Test
    fun `total read from card payment when TOTAL line is missing`() {
        val r = parser.parse(listOf("PAINE 3,00 A", "LAPTE 6,00 B", "CARD 9,00"))
        assertEquals(Parsed<Long?>(900, false), r.total)
        assertTrue(ReceiptWarning.TOTAL_FROM_PAYMENT in r.warnings)
    }

    @Test
    fun `mismatch between quantity and line total is flagged`() {
        val r = parser.parse(listOf("2 BUC x 5,50", "LAPTE 12,00 B", "TOTAL 12,00"))
        assertEquals(1200L, r.items.single().amountBani)
        assertFalse(r.items.single().amountCertain)
        assertFalse(r.items.single().quantityCertain)
    }

    @Test
    fun `empty and garbage input does not crash`() {
        assertTrue(ReceiptWarning.NO_ITEMS in parser.parse(emptyList()).warnings)
        assertTrue(ReceiptWarning.NO_ITEMS in parser.parse(listOf("", "   ", "~~~", "12 34 56")).warnings)
    }

    @Test
    fun `amount detection`() {
        fun amounts(s: String) = parser.findAmounts(s).map { it.bani }
        assertEquals(listOf(1250L), amounts("X 12,50 B"))
        assertEquals(listOf(123_456L), amounts("TOTAL 1.234,56"))
        assertEquals(listOf(1250L), amounts("12.50"))
        assertEquals(emptyList<Long>(), amounts("APA 0,50 L")) // a size, not a price
        assertEquals(emptyList<Long>(), amounts("TVA 19,00%"))
        assertEquals(emptyList<Long>(), amounts("DATA 02.11.2026"))
        assertEquals(emptyList<Long>(), amounts("32,150 L"))
        assertEquals(listOf(1050L), amounts("1O,5O"))
        assertTrue(parser.findAmounts("REDUCERE -2,00").single().negative)
    }

    @Test
    fun `discount allocation sums exactly`() {
        assertEquals(listOf(100L, 55L, 250L), parser.allocate(405, listOf(999, 549, 2499)))
        assertEquals(listOf(34L, 33L, 33L), parser.allocate(100, listOf(1, 1, 1)))
        assertEquals(listOf(7L, 0L), parser.allocate(7, listOf(0, 0)))
    }
}
