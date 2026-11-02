package ro.punguta.app.domain.receipt

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One recognized text line with its bounding box in image pixels. */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Clockwise rotation of the text in degrees, as reported by the OCR engine. */
    val angleDegrees: Float = 0f,
)

/**
 * OCR engines often return a receipt's name column and price column as separate blocks.
 * This rebuilds printed rows by grouping lines whose vertical centers align (after undoing the
 * photo's tilt), then joining each row left to right.
 */
object RowAssembler {
    fun assemble(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() && it.bottom > it.top }
        if (usable.isEmpty()) return emptyList()

        val angle = Math.toRadians(median(usable.map { it.angleDegrees.toDouble() }))
        val cosA = cos(angle)
        val sinA = sin(angle)
        val medianHeight = median(usable.map { (it.bottom - it.top).toDouble() }).coerceAtLeast(1.0)
        val tolerance = medianHeight * 0.55

        class Placed(val line: OcrLine, val x: Double, val y: Double)

        val placed = usable.map { l ->
            val cx = (l.left + l.right) / 2.0
            val cy = (l.top + l.bottom) / 2.0
            // Rotate back by the text angle so that printed rows become horizontal.
            Placed(l, cx * cosA + cy * sinA, -cx * sinA + cy * cosA)
        }.sortedBy { it.y }

        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            val rowY = row?.map { it.y }?.average()
            val overlaps = row?.any { horizontalOverlap(it.line, p.line) > 0.5 } ?: false
            if (row != null && rowY != null && abs(p.y - rowY) <= tolerance && !overlaps) {
                row += p
            } else {
                rows += mutableListOf(p)
            }
        }
        return rows.map { row -> row.sortedBy { it.x }.joinToString("  ") { it.line.text.trim() } }
    }

    /** Overlap of the two lines' horizontal extents, relative to the narrower one. */
    private fun horizontalOverlap(a: OcrLine, b: OcrLine): Double {
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val narrower = minOf(a.right - a.left, b.right - b.left)
        return if (overlap <= 0 || narrower <= 0) 0.0 else overlap.toDouble() / narrower
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
