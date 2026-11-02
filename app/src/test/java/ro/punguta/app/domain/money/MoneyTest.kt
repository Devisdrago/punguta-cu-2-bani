package ro.punguta.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats bani with Romanian separators`() {
        assertEquals("0,00 lei", Money.format(0))
        assertEquals("0,05 lei", Money.format(5))
        assertEquals("12,50 lei", Money.format(1250))
        assertEquals("1.234,50 lei", Money.format(123_450))
        assertEquals("1.000.000,00 lei", Money.format(100_000_000))
        assertEquals("-3,40 lei", Money.format(-340))
        assertEquals("12,50", Money.format(1250, withCurrency = false))
    }

    @Test
    fun `formats input without grouping`() {
        assertEquals("1234,50", Money.formatInput(123_450))
        assertEquals("0,07", Money.formatInput(7))
    }

    @Test
    fun `parses common user inputs`() {
        assertEquals(1200L, Money.parse("12"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(1205L, Money.parse("12,05"))
        assertEquals(123_450L, Money.parse("1.234,50"))
        assertEquals(123_450L, Money.parse("1 234,50"))
        assertEquals(123_400L, Money.parse("1.234"))
        assertEquals(123_450L, Money.parse("1,234.50"))
        assertEquals(1250L, Money.parse(" 12,50 lei "))
        assertEquals(50L, Money.parse(",50"))
        assertEquals(0L, Money.parse("0"))
    }

    @Test
    fun `rejects invalid or ambiguous input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("12,505"))
        assertNull(Money.parse("1,2,3"))
        assertNull(Money.parse("12,"))
        assertNull(Money.parse("1234.567"))
        assertNull(Money.parse("99999999999999"))
    }

    @Test
    fun `parse and format round trip`() {
        listOf(0L, 1L, 99L, 100L, 1250L, 123_456L, 999_999_99L).forEach {
            assertEquals(it, Money.parse(Money.formatInput(it)))
            assertEquals(it, Money.parse(Money.format(it)))
        }
    }

    @Test
    fun `line total rounds half up from thousandths`() {
        assertEquals(1100L, Money.lineTotal(2000, 550)) // 2 BUC x 5,50
        assertEquals(1049L, Money.lineTotal(1_234, 850)) // 1,234 kg x 8,50 = 10,489
        assertEquals(1050L, Money.lineTotal(1_500, 700)) // 1,5 x 7,00
        assertEquals(1L, Money.lineTotal(5, 100)) // 0,005 x 1,00 = 0,005 -> 0,01
    }

    @Test
    fun `savings rate handles zero income safely`() {
        assertNull(savingsRateBasisPoints(0, 0))
        assertNull(savingsRateBasisPoints(0, 5000))
        assertEquals(2_500L, savingsRateBasisPoints(400_000, 300_000))
        assertEquals(-5_000L, savingsRateBasisPoints(100_000, 150_000))
        assertEquals(10_000L, savingsRateBasisPoints(100_000, 0))
    }

    @Test
    fun `formats basis points as percent`() {
        assertEquals("25,0%", formatBasisPoints(2_500))
        assertEquals("12,3%", formatBasisPoints(1_234))
        assertEquals("12,4%", formatBasisPoints(1_235))
        assertEquals("-50,0%", formatBasisPoints(-5_000))
    }
}
