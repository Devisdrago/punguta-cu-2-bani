package ro.punguta.app.domain.money

import kotlin.math.abs

/**
 * All money in the app is an integer number of bani (1 leu = 100 bani).
 * Floating point is never used for amounts.
 */
object Money {
    /** Upper bound for a single amount: 10 billion lei. Guards against overflow and OCR garbage. */
    const val MAX_BANI: Long = 1_000_000_000_000L

    /** Formats bani for display, e.g. 123450 -> "1.234,50 lei". */
    fun format(bani: Long, withCurrency: Boolean = true): String {
        val sign = if (bani < 0) "-" else ""
        val absolute = abs(bani)
        val lei = groupThousands(absolute / 100)
        val cents = (absolute % 100).toString().padStart(2, '0')
        val text = "$sign$lei,$cents"
        return if (withCurrency) "$text lei" else text
    }

    /** Formats bani for an editable text field, without grouping, e.g. 123450 -> "1234,50". */
    fun formatInput(bani: Long): String {
        val sign = if (bani < 0) "-" else ""
        val absolute = abs(bani)
        return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')}"
    }

    /**
     * Parses a user-typed amount into bani. Accepts "12", "12,5", "12,50", "12.50",
     * "1.234,50", "1 234,50" and an optional "lei"/"ron" suffix.
     * Returns null for anything ambiguous, negative, with more than 2 decimals, or too large.
     */
    fun parse(input: String): Long? {
        var s = input.trim().lowercase()
            .removeSuffix("lei").removeSuffix("ron").trim()
            .replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.startsWith("-")) return null
        if (s.any { !it.isDigit() && it != ',' && it != '.' }) return null

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val decimalIndex: Int = when {
            lastComma >= 0 && lastDot >= 0 -> maxOf(lastComma, lastDot)
            lastComma >= 0 -> {
                // A single comma is the Romanian decimal separator.
                if (s.count { it == ',' } > 1) return null
                lastComma
            }
            lastDot >= 0 -> {
                // Dots alone: "1.234" / "1.234.567" are grouping, "12.5" / "12.50" is a decimal.
                val decimals = s.length - lastDot - 1
                if (decimals == 3 || s.count { it == '.' } > 1) -1 else lastDot
            }
            else -> -1
        }

        val intPart: String
        val fracPart: String
        if (decimalIndex >= 0) {
            intPart = s.substring(0, decimalIndex)
            fracPart = s.substring(decimalIndex + 1)
            if (fracPart.isEmpty() || fracPart.length > 2 || fracPart.any { !it.isDigit() }) return null
        } else {
            intPart = s
            fracPart = ""
        }
        val groupSeparator = when {
            decimalIndex < 0 -> if (intPart.contains('.')) '.' else null
            s[decimalIndex] == ',' -> '.'
            else -> ','
        }
        if (intPart.isNotEmpty() && !isValidGrouping(intPart, groupSeparator)) return null
        val digits = intPart.filter { it.isDigit() }.ifEmpty { "0" }
        if (digits.length > 13) return null
        s = digits
        val lei = s.toLong()
        val bani = lei * 100 + fracPart.padEnd(2, '0').ifEmpty { "00" }.toLong()
        return if (bani > MAX_BANI) null else bani
    }

    private fun isValidGrouping(intPart: String, separator: Char?): Boolean {
        if (separator == null || !intPart.contains(separator)) return intPart.all { it.isDigit() }
        val groups = intPart.split(separator)
        if (groups.first().isEmpty() || groups.first().length > 3) return false
        return groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) } && groups.first().all(Char::isDigit)
    }

    private fun groupThousands(value: Long): String {
        val raw = value.toString()
        val sb = StringBuilder()
        raw.forEachIndexed { i, c ->
            if (i > 0 && (raw.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Line total for a quantity expressed in thousandths (1000 = 1 unit) times a unit price.
     * Rounds half up to the nearest ban, as Romanian cash registers do.
     */
    fun lineTotal(quantityMilli: Long, unitPriceBani: Long): Long {
        val product = quantityMilli * unitPriceBani
        return (product + 500) / 1000
    }
}

/**
 * Savings rate in basis points (1/100 of a percent), or null when income is zero or negative
 * and a percentage would be meaningless.
 */
fun savingsRateBasisPoints(incomeBani: Long, spendingBani: Long): Long? {
    if (incomeBani <= 0) return null
    return (incomeBani - spendingBani) * 10_000 / incomeBani
}

/** Formats basis points as a Romanian percentage, e.g. 1234 -> "12,3%". */
fun formatBasisPoints(bp: Long): String {
    val sign = if (bp < 0) "-" else ""
    val tenths = (abs(bp) + 5) / 10
    return "$sign${tenths / 10},${tenths % 10}%"
}
