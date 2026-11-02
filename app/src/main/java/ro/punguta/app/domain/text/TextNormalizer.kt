package ro.punguta.app.domain.text

import java.text.Normalizer
import java.util.Locale

/** Text helpers shared by the receipt parser and category learning. */
object TextNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val ro = Locale("ro", "RO")

    /** Removes Romanian (and other) diacritics: "Mâncare ŞI ţuică" -> "Mancare SI tuica". */
    fun stripDiacritics(text: String): String =
        combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")

    private val unitTokens = setOf(
        "buc", "bucata", "bucati", "kg", "g", "gr", "ml", "l", "ltr", "cl", "x", "pet", "doz", "pz",
        "sgr", "set", "pach", "pack",
    )

    /**
     * Normalizes a purchased item name into a stable key for category learning.
     * Lowercase, no diacritics, typical OCR digit/letter swaps fixed inside words,
     * numbers, units and punctuation removed. "LAPTE ZUZU 1,5% 1L" -> "lapte zuzu".
     */
    fun normalizeItemName(raw: String): String {
        val lowered = stripDiacritics(raw).lowercase(ro)
        val tokens = lowered.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            // Quantities glued to units ("1kg", "500g", "2l") are not part of the name.
            .filterNot { t -> t.first().isDigit() && t.dropWhile(Char::isDigit) in unitTokens }
            .map(::fixOcrInWord)
            .filter { token -> token.any { it.isLetter() } }
            .map { token -> token.filter { it.isLetter() } }
            .filter { it.length >= 2 && it !in unitTokens }
        return tokens.joinToString(" ")
    }

    /** In a token that is mostly letters, digits are usually misread letters (C0LA, M1NERALA). */
    private fun fixOcrInWord(token: String): String {
        val letters = token.count { it.isLetter() }
        val digits = token.count { it.isDigit() }
        if (digits == 0 || letters <= digits) return token
        return token.map {
            when (it) {
                '0' -> 'o'
                '1' -> 'i'
                '5' -> 's'
                '8' -> 'b'
                else -> it
            }
        }.joinToString("")
    }
}
