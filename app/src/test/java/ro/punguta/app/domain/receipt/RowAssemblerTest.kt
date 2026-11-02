package ro.punguta.app.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RowAssemblerTest {
    private fun line(text: String, left: Float, top: Float, width: Float = 200f, height: Float = 20f, angle: Float = 0f) =
        OcrLine(text, left, top, left + width, top + height, angle)

    @Test
    fun `name and price columns returned as separate blocks are joined per row`() {
        // OCR order: whole name column first, then whole price column.
        val lines = listOf(
            line("PAINE ALBA", 10f, 100f),
            line("LAPTE 1L", 10f, 130f),
            line("TOTAL", 10f, 160f),
            line("3,49 B", 400f, 102f, width = 80f),
            line("6,49 B", 400f, 131f, width = 80f),
            line("9,98", 400f, 158f, width = 80f),
        )
        assertEquals(listOf("PAINE ALBA  3,49 B", "LAPTE 1L  6,49 B", "TOTAL  9,98"), RowAssembler.assemble(lines))
    }

    @Test
    fun `tilted photo rows are recovered`() {
        // Rotate a clean two-column layout by 4 degrees clockwise around the origin.
        val angle = 4.0
        val rad = Math.toRadians(angle)
        fun rotated(text: String, cx: Float, cy: Float, w: Float): OcrLine {
            val x = (cx * cos(rad) - cy * sin(rad)).toFloat()
            val y = (cx * sin(rad) + cy * cos(rad)).toFloat()
            return OcrLine(text, x - w / 2, y - 10, x + w / 2, y + 10, angle.toFloat())
        }
        val lines = listOf(
            rotated("PAINE", 100f, 100f, 120f),
            rotated("3,49", 600f, 100f, 60f),
            rotated("LAPTE", 100f, 125f, 120f),
            rotated("6,49", 600f, 125f, 60f),
        )
        assertEquals(listOf("PAINE  3,49", "LAPTE  6,49"), RowAssembler.assemble(lines))
    }

    @Test
    fun `stacked lines in the same column are not merged`() {
        val lines = listOf(line("A", 10f, 100f), line("B", 10f, 108f))
        assertEquals(listOf("A", "B"), RowAssembler.assemble(lines))
    }

    @Test
    fun `empty input`() {
        assertEquals(emptyList<String>(), RowAssembler.assemble(emptyList()))
    }
}
