package ro.punguta.app.domain.category

import ro.punguta.app.domain.text.TextNormalizer

/** Stable keys for the default categories, so keyword rules survive a rename. */
object DefaultCategoryKeys {
    const val FOOD = "food"
    const val DRINKS = "drinks"
    const val HOME = "home"
    const val BILLS = "bills"
    const val TRANSPORT = "transport"
    const val HEALTH = "health"
    const val CLOTHING = "clothing"
    const val ENTERTAINMENT = "entertainment"
    const val SUBSCRIPTIONS = "subscriptions"
    const val OTHER = "other"

    /** Default categories in display order: key to Romanian name. */
    val defaults: List<Pair<String, String>> = listOf(
        FOOD to "Mâncare",
        DRINKS to "Băuturi",
        HOME to "Casă",
        BILLS to "Facturi",
        TRANSPORT to "Transport",
        HEALTH to "Sănătate",
        CLOTHING to "Îmbrăcăminte",
        ENTERTAINMENT to "Divertisment",
        SUBSCRIPTIONS to "Abonamente",
        OTHER to "Altele",
    )
}

/** Built-in keyword rules. Keywords are already normalized (lowercase, no diacritics). */
object KeywordRules {
    val rules: Map<String, List<String>> = mapOf(
        DefaultCategoryKeys.FOOD to listOf(
            "paine", "franzela", "bagheta", "chifl", "corn", "covrig", "lapte", "iaurt", "kefir", "sana",
            "branza", "telemea", "cascaval", "mozzarella", "smantana", "unt", "margarina", "oua", "ou",
            "carne", "pui", "piept", "pulpe", "porc", "vita", "vitel", "curcan", "miel", "peste", "ton",
            "somon", "salam", "sunca", "parizer", "crenvurst", "carnati", "mici", "bacon", "pate",
            "faina", "malai", "zahar", "sare", "ulei", "orez", "paste", "spaghete", "fasole", "linte",
            "naut", "mazare", "porumb", "rosii", "castraveti", "ardei", "cartofi", "ceapa", "usturoi",
            "morcovi", "varza", "salata", "dovlecei", "vinete", "ciuperci", "mere", "pere", "banane",
            "portocale", "lamai", "mandarine", "struguri", "capsuni", "kiwi", "legume", "fructe",
            "ciocolata", "biscuiti", "napolitane", "chips", "cereale", "fulgi", "miere", "gem", "dulceata",
            "ketchup", "maioneza", "mustar", "conserva", "pizza", "sandvis", "croissant", "prajitura",
            "tort", "inghetata", "snack", "alune", "nuci", "seminte", "condiment", "piper", "boia",
            "drojdie", "oregano", "supa", "ciorba", "shaorma", "burger", "meniu",
        ),
        DefaultCategoryKeys.DRINKS to listOf(
            "apa", "minerala", "plata", "suc", "nectar", "bere", "vin", "cola", "pepsi", "fanta", "sprite",
            "schweppes", "cafea", "espresso", "cappuccino", "latte", "ceai", "energizant", "redbull",
            "red bull", "monster", "whisky", "vodka", "rom", "gin", "lichior", "tuica", "palinca", "coniac",
            "sampanie", "prosecco", "borsec", "dorna", "bucovina", "aqua carpatica", "izvorul", "lipova",
            "ursus", "timisoreana", "ciuc", "heineken", "stella", "tuborg", "limonada", "fresh", "smoothie",
        ),
        DefaultCategoryKeys.HOME to listOf(
            "detergent", "balsam rufe", "hartie igienica", "servetele", "prosoape hartie", "sapun",
            "gel dus", "sampon", "pasta dinti", "periuta", "deodorant", "burete", "lavete", "saci menajeri",
            "saci", "clor", "domestos", "ariel", "persil", "fairy", "vase", "cif", "mr proper", "odorizant",
            "bec", "baterii", "folie", "staniol", "chibrituri", "lumanari", "scutece", "absorbante",
            "vim", "dero", "bref", "pronto", "spray", "pungi", "punga",
        ),
        DefaultCategoryKeys.BILLS to listOf(
            "factura", "enel", "engie", "electrica", "eon", "e on", "digi", "rcs", "rds", "orange",
            "vodafone", "telekom", "intretinere", "apa nova", "salubrizare", "gaze", "energie", "curent",
            "internet", "telefonie", "chirie", "impozit", "rata",
        ),
        DefaultCategoryKeys.TRANSPORT to listOf(
            "benzina", "motorina", "diesel", "carburant", "combustibil", "gpl", "petrom", "omv", "rompetrol",
            "lukoil", "mol", "parcare", "taxi", "uber", "bolt", "rovinieta", "bilet", "calatorie", "metrou",
            "stb", "cfr", "spalatorie", "vulcanizare", "service auto", "ulei motor", "adblue", "trotineta",
        ),
        DefaultCategoryKeys.HEALTH to listOf(
            "paracetamol", "ibuprofen", "nurofen", "algocalmin", "aspirina", "vitamina", "vitamine",
            "magneziu", "plasturi", "sirop", "pastile", "comprimate", "capsule", "unguent", "crema medicala",
            "antibiotic", "farmacie", "catena", "sensiblu", "help net", "dr max", "medicament", "picaturi",
            "masti", "bandaj", "termometru", "consultatie", "analize", "stomatolog",
        ),
        DefaultCategoryKeys.CLOTHING to listOf(
            "tricou", "pantaloni", "blugi", "camasa", "rochie", "fusta", "pantofi", "adidasi", "ghete",
            "cizme", "sandale", "papuci", "sosete", "ciorapi", "geaca", "jacheta", "palton", "bluza",
            "pulover", "hanorac", "lenjerie", "chiloti", "sutien", "pijama", "fular", "caciula", "manusi",
            "curea", "costum",
        ),
        DefaultCategoryKeys.ENTERTAINMENT to listOf(
            "cinema", "film", "concert", "teatru", "spectacol", "joc", "jocuri", "carte", "carti", "revista",
            "bowling", "biliard", "escape", "muzeu", "festival", "loto", "pariu", "jucarie", "jucarii",
        ),
        DefaultCategoryKeys.SUBSCRIPTIONS to listOf(
            "netflix", "spotify", "hbo", "hbo max", "youtube", "disney", "icloud", "google one", "apple",
            "amazon prime", "prime video", "voyo", "abonament", "sala", "fitness", "gym", "chatgpt",
            "microsoft", "office", "playstation plus", "xbox",
        ),
    )
}

/**
 * Suggests a category for an item name. Order of precedence:
 * 1. The user's own past corrections (exact normalized name).
 * 2. Built-in keyword rules, mapped through the categories that still exist.
 * 3. The "Altele" category, if it still exists.
 */
class CategorySuggester(
    /** normalized item name -> category id, from the user's corrections. */
    private val learned: Map<String, Long>,
    /** default category key -> category id, only for default categories that still exist. */
    private val idsByDefaultKey: Map<String, Long>,
) {
    data class Suggestion(val categoryId: Long?, val source: Source)

    enum class Source { LEARNED, RULE, FALLBACK, NONE }

    fun suggest(itemName: String): Suggestion {
        val normalized = TextNormalizer.normalizeItemName(itemName)
        if (normalized.isNotEmpty()) {
            learned[normalized]?.let { return Suggestion(it, Source.LEARNED) }
            matchRule(normalized)?.let { key ->
                idsByDefaultKey[key]?.let { return Suggestion(it, Source.RULE) }
            }
        }
        val other = idsByDefaultKey[DefaultCategoryKeys.OTHER]
        return if (other != null) Suggestion(other, Source.FALLBACK) else Suggestion(null, Source.NONE)
    }

    /** Returns the default key whose keyword gives the longest match, or null. */
    internal fun matchRule(normalized: String): String? {
        val tokens = normalized.split(' ')
        val padded = " $normalized "
        var bestKey: String? = null
        var bestLength = 0
        for ((key, keywords) in KeywordRules.rules) {
            if (key !in idsByDefaultKey) continue
            for (keyword in keywords) {
                val matches = if (keyword.contains(' ')) {
                    padded.contains(" $keyword ")
                } else {
                    tokens.any { it == keyword || (keyword.length >= 4 && it.startsWith(keyword)) }
                }
                if (matches && keyword.length > bestLength) {
                    bestKey = key
                    bestLength = keyword.length
                }
            }
        }
        return bestKey
    }
}
