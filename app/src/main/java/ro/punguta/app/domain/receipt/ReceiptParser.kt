package ro.punguta.app.domain.receipt

import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.text.TextNormalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Parses the OCR text of a Romanian fiscal receipt (bon fiscal) into merchant, date, items,
 * discounts and total. Input is one string per printed row, top to bottom.
 *
 * The parser never throws on bad input; anything it is unsure about is returned with
 * `certain = false` (or as a warning) so the review screen can highlight it.
 */
class ReceiptParser(private val today: LocalDate) {

    internal data class AmountMatch(
        val bani: Long,
        val negative: Boolean,
        /** True when OCR look-alike letters (O, I, l) had to be turned into digits. */
        val fixed: Boolean,
        val start: Int,
        val end: Int,
    )

    internal data class QtyMatch(
        val milli: Long,
        val unitPrice: AmountMatch,
        val lineTotal: AmountMatch?,
        val nameText: String,
    )

    private enum class Kind { TOTAL, SUBTOTAL, VAT, PAYMENT, CHANGE, DISCOUNT, IGNORE, HEADER, OTHER }

    private class ItemBuilder(
        val name: String,
        val nameCertain: Boolean,
        var quantityMilli: Long?,
        var unitPriceBani: Long?,
        var quantityCertain: Boolean,
        val grossBani: Long,
        var amountCertain: Boolean,
    ) {
        var discountBani = 0L
        var discountCertain = true

        fun addDiscount(bani: Long, certain: Boolean) {
            discountBani += bani
            discountCertain = discountCertain && certain && discountBani <= grossBani
        }

        fun build() = ParsedItem(
            name = name,
            nameCertain = nameCertain,
            quantityMilli = quantityMilli,
            unitPriceBani = unitPriceBani,
            quantityCertain = quantityCertain,
            discountBani = discountBani.coerceAtMost(grossBani),
            discountCertain = discountCertain,
            amountBani = (grossBani - discountBani).coerceAtLeast(0),
            amountCertain = amountCertain && discountCertain,
        )
    }

    fun parse(rawLines: List<String>): ParsedReceipt {
        val lines = rawLines.map(::normalizeLine).filter { it.isNotEmpty() }
        val warnings = mutableSetOf<ReceiptWarning>()
        val items = mutableListOf<ItemBuilder>()
        val unused = mutableListOf<String>()
        var total: AmountMatch? = null
        var cardPayment: AmountMatch? = null
        var subtotal: Long? = null
        var afterSubtotal = false
        var receiptDiscount = 0L
        var pendingName: String? = null
        var pendingQty: QtyMatch? = null
        var ended = false

        fun addItem(name: String, qty: QtyMatch?, priceBani: Long, priceCertain: Boolean) {
            var amountCertain = priceCertain
            var qtyCertain = qty == null || !qty.unitPrice.fixed
            if (qty != null && abs(Money.lineTotal(qty.milli, qty.unitPrice.bani) - priceBani) > 1) {
                amountCertain = false
                qtyCertain = false
            }
            items += ItemBuilder(
                name, isNameCertain(name), qty?.milli, qty?.unitPrice?.bani, qtyCertain, priceBani, amountCertain,
            )
        }

        fun addItemFromQty(name: String, qty: QtyMatch) {
            val printed = qty.lineTotal
            if (printed != null) {
                addItem(name, qty, printed.bani, !printed.fixed)
            } else {
                // No printed line total: compute it, but let the user confirm.
                addItem(name, qty, Money.lineTotal(qty.milli, qty.unitPrice.bani), false)
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            i++
            val tokens = tokensOf(keywordForm(line))
            val kind = classify(tokens)
            val amounts = findAmounts(line)

            if (ended) {
                // After TOTAL only payment details, VAT and footer follow.
                if (kind == Kind.PAYMENT && cardPayment == null && isCardPayment(tokens)) cardPayment = amounts.lastOrNull()
                continue
            }

            when (kind) {
                Kind.TOTAL -> {
                    var a = amounts.lastOrNull()
                    if (a == null && i < lines.size) {
                        val next = findAmounts(lines[i])
                        if (next.isNotEmpty() && letterCount(stripAmounts(lines[i], next)) < 2) {
                            a = next.last()
                            i++
                        }
                    }
                    if (a != null) {
                        total = a
                        ended = true
                    }
                }
                Kind.SUBTOTAL -> {
                    subtotal = amounts.lastOrNull()?.bani ?: subtotal
                    afterSubtotal = true
                    pendingName = null
                    pendingQty = null
                }
                Kind.DISCOUNT -> {
                    val a = amounts.lastOrNull()
                    when {
                        a == null -> Unit
                        afterSubtotal -> receiptDiscount += a.bani
                        items.isNotEmpty() -> items.last().addDiscount(a.bani, !a.fixed)
                        else -> warnings += ReceiptWarning.ORPHAN_DISCOUNT
                    }
                }
                Kind.PAYMENT -> if (cardPayment == null && isCardPayment(tokens)) cardPayment = amounts.lastOrNull()
                Kind.VAT, Kind.CHANGE, Kind.IGNORE -> Unit
                Kind.HEADER, Kind.OTHER -> {
                    if (kind == Kind.HEADER && items.isEmpty()) {
                        // Company details, address, fiscal code: never items, never item names.
                        pendingName = null
                        continue
                    }
                    val qty = findQuantity(line, amounts)
                    if (qty != null) {
                        val ownName = cleanName(qty.nameText)
                        val nextLine = lines.getOrNull(i)
                        val nextIsName = nextLine != null && findAmounts(nextLine).isEmpty() &&
                            letterCount(nextLine) >= 2 && classify(tokensOf(keywordForm(nextLine))) == Kind.OTHER
                        when {
                            letterCount(ownName) >= 2 -> addItemFromQty(ownName, qty)
                            // "2.000 BUC x 4.99 = 9.98" followed by the name on the next row.
                            qty.lineTotal != null && nextIsName -> {
                                addItemFromQty(cleanName(nextLine!!), qty)
                                i++
                            }
                            // Name on the row above, quantity and total below it.
                            pendingName != null -> addItemFromQty(pendingName!!, qty)
                            // Name and price on the row above, quantity details below.
                            qty.lineTotal != null && items.isNotEmpty() && items.last().quantityMilli == null &&
                                items.last().grossBani == qty.lineTotal.bani -> {
                                items.last().quantityMilli = qty.milli
                                items.last().unitPriceBani = qty.unitPrice.bani
                            }
                            else -> {
                                pendingQty = qty
                                continue
                            }
                        }
                        pendingName = null
                        pendingQty = null
                        continue
                    }

                    val last = amounts.lastOrNull()
                    val name = cleanName(if (amounts.isEmpty()) line else line.substring(0, amounts.first().start))
                    when {
                        last != null && last.negative -> {
                            // A bare negative amount right after an item is a discount on it.
                            if (items.isNotEmpty()) items.last().addDiscount(last.bani, false)
                            else warnings += ReceiptWarning.ORPHAN_DISCOUNT
                        }
                        last != null && letterCount(name) >= 2 -> {
                            val qtyForItem = pendingQty
                            addItem(name, qtyForItem, last.bani, !last.fixed)
                            pendingName = null
                            pendingQty = null
                        }
                        last != null && pendingName != null -> {
                            addItem(pendingName!!, pendingQty, last.bani, !last.fixed)
                            pendingName = null
                            pendingQty = null
                        }
                        last != null -> unused += line
                        letterCount(name) >= 2 -> {
                            val pq = pendingQty
                            if (pq?.lineTotal != null) {
                                addItemFromQty(name, pq)
                                pendingQty = null
                            } else {
                                pendingName = name
                            }
                        }
                    }
                }
            }
        }

        if (receiptDiscount > 0) {
            if (items.isNotEmpty()) {
                val shares = allocate(receiptDiscount, items.map { it.grossBani - it.discountBani })
                items.forEachIndexed { index, item -> item.addDiscount(shares[index], false) }
                warnings += ReceiptWarning.RECEIPT_DISCOUNT_SPREAD
            } else {
                warnings += ReceiptWarning.ORPHAN_DISCOUNT
            }
        }

        val totalParsed: Parsed<Long?> = when {
            total != null -> Parsed(total.bani, !total.fixed)
            cardPayment != null -> {
                warnings += ReceiptWarning.TOTAL_FROM_PAYMENT
                Parsed(cardPayment.bani, false)
            }
            else -> {
                warnings += ReceiptWarning.NO_TOTAL
                Parsed(null, false)
            }
        }
        val merchant = detectMerchant(lines)
        val date = detectDate(lines)
        if (items.isEmpty()) warnings += ReceiptWarning.NO_ITEMS
        if (merchant.value == null) warnings += ReceiptWarning.NO_MERCHANT
        if (date.value == null) warnings += ReceiptWarning.NO_DATE
        if (unused.isNotEmpty()) warnings += ReceiptWarning.UNUSED_LINES

        return ParsedReceipt(
            merchant = merchant,
            date = date,
            items = items.map { it.build() },
            total = totalParsed,
            subtotalBani = subtotal,
            unusedLines = unused,
            warnings = warnings,
        )
    }

    // region Amounts

    /** Every money amount on the line, left to right. Amounts on receipts always have 2 decimals. */
    internal fun findAmounts(line: String): List<AmountMatch> {
        val result = mutableListOf<AmountMatch>()
        for (m in AMOUNT.findAll(line)) {
            val comma = m.groups[2] != null
            val sign = (if (comma) m.groups[1] else m.groups[4])?.value
            val intRaw = (if (comma) m.groups[2] else m.groups[5])!!.value
            val fracRaw = (if (comma) m.groups[3] else m.groups[6])!!.value
            val raw = intRaw + fracRaw
            if (raw.none { it.isDigit() }) continue
            // "0,50 L" or "19,00%" is a size or a rate, not a price.
            val after = line.substring(m.range.last + 1).trimStart()
            if (UNIT_AFTER.containsMatchIn(after)) continue
            val fixed = raw.any { it in OCR_DIGITS }
            val lei = fixDigits(intRaw.replace(".", "")).toLongOrNull() ?: continue
            val bani = lei * 100 + (fixDigits(fracRaw).toLongOrNull() ?: continue)
            if (bani > Money.MAX_BANI) continue
            val trailingMinus = after.startsWith("-")
            result += AmountMatch(bani, sign != null || trailingMinus, fixed, m.range.first, m.range.last + 1)
        }
        return result
    }

    private fun stripAmounts(line: String, amounts: List<AmountMatch>): String {
        val sb = StringBuilder(line)
        amounts.sortedByDescending { it.start }.forEach { sb.delete(it.start, it.end) }
        return sb.toString()
    }

    /** "2 BUC x 5,50", "1,234 kg x 6,49 LEI/kg 8,01", "IAURT 2 x 4,29 8,58", "2.000 x 4.99 = 9.98". */
    internal fun findQuantity(line: String, amounts: List<AmountMatch>): QtyMatch? {
        for (m in QUANTITY.findAll(line)) {
            val after = amounts.filter { it.start > m.range.last }
            val unitPrice = after.firstOrNull() ?: continue
            // The unit price must follow the "x" directly; "2X400G 5,99" is a name, not a quantity.
            if (unitPrice.start != m.range.last + 1 || unitPrice.negative) continue
            val milli = parseQuantity(m.groupValues[1]) ?: continue
            val lineTotal = after.drop(1).lastOrNull()
            return QtyMatch(milli, unitPrice, lineTotal, line.substring(0, m.range.first))
        }
        return null
    }

    private fun parseQuantity(text: String): Long? {
        val parts = text.split('.', ',')
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = parts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0
        val milli = whole * 1000 + frac
        return if (milli in 1..10_000_000) milli else null
    }

    private fun fixDigits(s: String): String = s.map {
        when (it) {
            'O', 'o' -> '0'
            'I', 'l', '|' -> '1'
            else -> it
        }
    }.joinToString("")

    /** Splits a receipt-level discount over items in proportion to their amounts; sums exactly. */
    internal fun allocate(totalBani: Long, weights: List<Long>): List<Long> {
        val sum = weights.sumOf { it.coerceAtLeast(0) }
        if (sum <= 0) return weights.mapIndexed { i, _ -> if (i == 0) totalBani else 0 }
        val floors = weights.map { totalBani * it.coerceAtLeast(0) / sum }.toMutableList()
        var remaining = totalBani - floors.sum()
        val order = weights.indices.sortedByDescending { (totalBani * weights[it].coerceAtLeast(0)) % sum }
        for (index in order) {
            if (remaining <= 0) break
            floors[index] = floors[index] + 1
            remaining--
        }
        return floors
    }

    // endregion

    // region Classification

    /** Uppercase, no diacritics, digits inside words turned back into letters (T0TAL -> TOTAL). */
    private fun keywordForm(line: String): String =
        TextNormalizer.stripDiacritics(line).uppercase(Locale.ROOT).split(' ').joinToString(" ") { token ->
            val letters = token.count { it.isLetter() }
            val digits = token.count { it.isDigit() }
            if (digits > 0 && letters > digits) {
                token.map {
                    when (it) {
                        '0' -> 'O'
                        '1' -> 'I'
                        '5' -> 'S'
                        else -> it
                    }
                }.joinToString("")
            } else {
                token
            }
        }

    private fun tokensOf(kw: String): List<String> = kw.split(Regex("[^A-Z0-9%]+")).filter { it.isNotEmpty() }

    private fun classify(tokens: List<String>): Kind {
        fun has(word: String) = tokens.any { fuzzyEquals(it, word) }
        val hasTotal = has("TOTAL")
        return when {
            has("SUBTOTAL") || (has("SUB") && hasTotal) -> Kind.SUBTOTAL
            hasTotal && (has("TVA") || tokens.any { it == "TV" }) -> Kind.VAT
            hasTotal && (has("REDUCERI") || has("REDUCERE") || has("DISCOUNT") || has("ARTICOLE") ||
                tokens.any { it.startsWith("ECONOM") }) -> Kind.IGNORE
            hasTotal -> Kind.TOTAL
            tokens.any { it == "TVA" } || tokens.firstOrNull() == "COTA" -> Kind.VAT
            has("REDUCERE") || has("REDUCERI") || has("DISCOUNT") ||
                tokens.any { it == "RED" || it == "PROMO" || it.startsWith("STORNO") || it == "ANULARE" } -> Kind.DISCOUNT
            tokens.any { it == "REST" } -> Kind.CHANGE
            has("NUMERAR") || tokens.any { it in PAYMENT_WORDS } -> Kind.PAYMENT
            tokens == listOf("LEI") || tokens.any { it in IGNORE_WORDS } ||
                tokens.any { it.startsWith("ECONOM") || it.startsWith("MULTUM") } -> Kind.IGNORE
            tokens.any { it in HEADER_WORDS } -> Kind.HEADER
            else -> Kind.OTHER
        }
    }

    private fun isCardPayment(tokens: List<String>) = tokens.any { it in CARD_WORDS }

    private fun fuzzyEquals(token: String, word: String): Boolean =
        token == word || (word.length >= 5 && abs(token.length - word.length) <= 1 && levenshtein(token, word) <= 1)

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // endregion

    // region Names, merchant, date

    private fun letterCount(s: String) = s.count { it.isLetter() }

    private fun cleanName(raw: String): String = raw
        .replace(Regex("[*#=_|~;]+"), " ")
        .replace(Regex("^(?:\\d{4,}\\s+)+"), "") // leading article codes / barcodes
        .trim(' ', '-', '.', ':', ',', '\'', '"')
        .replace(Regex("\\s+"), " ")

    private fun isNameCertain(name: String): Boolean =
        letterCount(name) >= 3 && !MIXED_TOKEN.containsMatchIn(name) && name.none { it in "~;'\"^`" }

    private fun detectMerchant(lines: List<String>): Parsed<String?> {
        val head = lines.take(10).map { " " + tokensOf(keywordForm(it)).joinToString(" ") + " " }
        for (row in head) {
            for ((key, display) in BRANDS) if (row.contains(" $key ")) return Parsed(display, true)
        }
        // Fallback: the first plausible company line before anything with a price.
        val firstPriced = lines.indexOfFirst { findAmounts(it).isNotEmpty() }.let { if (it < 0) lines.size else it }
        val candidate = lines.take(minOf(6, firstPriced)).firstOrNull { line ->
            val tokens = tokensOf(keywordForm(line))
            letterCount(line) >= 3 && tokens.none { it in HEADER_WORDS && it !in LEGAL_FORMS }
        } ?: return Parsed(null, false)
        val words = candidate.split(' ').filter { word ->
            val letters = word.filter { it.isLetter() }.uppercase(Locale.ROOT)
            letters.isNotEmpty() && letters !in LEGAL_FORMS
        }
        val name = words.joinToString(" ") { w ->
            w.lowercase(RO).replaceFirstChar { it.titlecase(RO) }
        }.trim(' ', ',', '.', '-')
        return if (name.isEmpty()) Parsed(null, false) else Parsed(name, false)
    }

    private fun detectDate(lines: List<String>): Parsed<LocalDate?> {
        for (line in lines) {
            ISO_DATE.find(line)?.let { m ->
                val date = runCatching {
                    LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                }.getOrNull()
                if (date != null) return Parsed(date, isPlausible(date))
            }
            for (m in DMY_DATE.findAll(line)) {
                val raw = m.groupValues.drop(1)
                val fixed = raw.any { part -> part.any { it in OCR_DIGITS } }
                val (d, mo, y) = raw.map { fixDigits(it).toIntOrNull() ?: -1 }
                val year = if (y in 0..99) 2000 + y else y
                val date = runCatching { LocalDate.of(year, mo, d) }.getOrNull() ?: continue
                return Parsed(date, !fixed && isPlausible(date))
            }
        }
        return Parsed(null, false)
    }

    private fun isPlausible(date: LocalDate): Boolean {
        val days = ChronoUnit.DAYS.between(date, today)
        return days in -1..400
    }

    // endregion

    private fun normalizeLine(s: String): String = s
        .replace('\t', ' ')
        .replace(Regex("(\\d)\\s*([.,])\\s+(\\d{2})(?!\\d)"), "$1$2$3") // "12, 50"
        .replace(Regex("(\\d)\\s+([.,])(\\d{2})(?!\\d)"), "$1$2$3") // "12 ,50"
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        val RO = Locale("ro", "RO")
        const val OCR_DIGITS = "OoIl|"
        private const val D = "[0-9OoIl|]"
        private const val BEFORE = "(?<![\\p{L}0-9.,&&[^xX×]])"
        private const val AFTER = "(?![0-9]|[.,][0-9])"

        /** Comma decimals with optional dot grouping (1.234,56), or dot decimals (12.50). */
        val AMOUNT = Regex(
            "$BEFORE(-\\s?)?((?:$D{1,3}(?:\\.$D{3})+)|$D{1,7}),($D{2})$AFTER" +
                "|$BEFORE(-\\s?)?($D{1,7})\\.($D{2})$AFTER",
        )
        val UNIT_AFTER = Regex("^(?:L|ML|CL|KG|G|GR|MG|%|CPR)(?![\\p{L}])", RegexOption.IGNORE_CASE)
        val QUANTITY = Regex(
            "(?<![0-9.,])([0-9]{1,5}(?:[.,][0-9]{1,3})?)\\s*(?:BUC\\.?|BUCATI|BC|KG|GR|G|L|ML|PCS|PZ|UM)?\\s*[xX×*]\\s*(?=-?$D)",
            RegexOption.IGNORE_CASE,
        )
        val MIXED_TOKEN = Regex("\\p{L}[0-9]+\\p{L}")
        val ISO_DATE = Regex("(?<![0-9])(20[0-9]{2})-([01][0-9])-([0-3][0-9])(?![0-9])")
        val DMY_DATE = Regex(
            "(?<![0-9])([0-3OoIl]?[0-9OoIl])\\s?[./-]\\s?([01OoIl]?[0-9OoIl])\\s?[./-]\\s?((?:20)?[0-9OoIl]{2})(?![0-9])",
        )

        val PAYMENT_WORDS = setOf(
            "CARD", "CASH", "PLATIT", "BANCAR", "VOUCHER", "TICHET", "TICHETE", "VISA", "MASTERCARD", "MAESTRO",
            "CONTACTLESS", "POS", "MODALITATE",
        )
        val CARD_WORDS = setOf("CARD", "BANCAR", "VISA", "MASTERCARD", "MAESTRO", "CONTACTLESS", "POS")
        val IGNORE_WORDS = setOf("DATA", "ORA", "UNIC", "BF", "SERIE", "PUNCTE", "ARTICOLE", "CASIER", "OPERATOR", "CHITANTA")
        val HEADER_WORDS = setOf(
            "CIF", "CUI", "COD", "FISCAL", "FISCALA", "IDENTIFICARE", "REG", "CAPITAL", "SOCIAL", "STR", "STRADA",
            "NR", "JUD", "JUDETUL", "TEL", "TELEFON", "FAX", "BON", "ADRESA", "LOC", "MUN", "SECTOR", "BL", "SC",
        )
        val LEGAL_FORMS = setOf("SC", "SRL", "SA", "SCS", "SNC", "PFA", "II", "SPRL", "S", "C", "R", "L", "A")

        /** Known Romanian chains: keyword (normalized tokens) to display name. Order matters. */
        val BRANDS = listOf(
            "KAUFLAND" to "Kaufland", "LIDL" to "Lidl", "CARREFOUR" to "Carrefour", "MEGA IMAGE" to "Mega Image",
            "PROFI" to "Profi", "PENNY" to "Penny", "AUCHAN" to "Auchan", "CORA" to "Cora", "SELGROS" to "Selgros",
            "METRO" to "Metro", "LA DOI PASI" to "La Doi Pași", "ANNABELLA" to "Annabella", "DEDEMAN" to "Dedeman",
            "LEROY MERLIN" to "Leroy Merlin", "HORNBACH" to "Hornbach", "BRICO DEPOT" to "Brico Dépôt",
            "ALTEX" to "Altex", "FLANCO" to "Flanco", "EMAG" to "eMAG", "IKEA" to "IKEA", "PEPCO" to "Pepco",
            "DECATHLON" to "Decathlon", "CATENA" to "Catena", "SENSIBLU" to "Sensiblu", "HELP NET" to "Help Net",
            "DR MAX" to "Dr. Max", "DONA" to "Dona", "BENU" to "Benu", "PETROM" to "Petrom", "OMV" to "OMV",
            "ROMPETROL" to "Rompetrol", "LUKOIL" to "Lukoil", "MOL" to "MOL", "SOCAR" to "Socar",
            "MCDONALD" to "McDonald's", "MCDONALDS" to "McDonald's", "KFC" to "KFC", "STARBUCKS" to "Starbucks",
        )
    }
}
