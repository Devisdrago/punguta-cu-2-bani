package ro.punguta.app.domain.category

import org.junit.Assert.assertEquals
import org.junit.Test
import ro.punguta.app.domain.category.CategorySuggester.Source
import ro.punguta.app.domain.text.TextNormalizer

class CategorySuggesterTest {
    private val ids = DefaultCategoryKeys.defaults.mapIndexed { i, (key, _) -> key to (i + 1L) }.toMap()
    private val food = ids.getValue(DefaultCategoryKeys.FOOD)
    private val drinks = ids.getValue(DefaultCategoryKeys.DRINKS)
    private val home = ids.getValue(DefaultCategoryKeys.HOME)
    private val other = ids.getValue(DefaultCategoryKeys.OTHER)

    @Test
    fun `normalizes item names for learning`() {
        assertEquals("lapte zuzu", TextNormalizer.normalizeItemName("LAPTE ZUZU 1,5% 1L"))
        assertEquals("paine alba", TextNormalizer.normalizeItemName("Pâine Albă 500g"))
        assertEquals("coca cola", TextNormalizer.normalizeItemName("C0CA C0LA 0.5L PET"))
        assertEquals("apa minerala", TextNormalizer.normalizeItemName("APA M1NERALA 2L"))
        assertEquals("branza telemea", TextNormalizer.normalizeItemName("BRÂNZĂ TELEMEA"))
        assertEquals("tuica", TextNormalizer.normalizeItemName("ŢUICĂ")) // cedilla variant
        assertEquals("", TextNormalizer.normalizeItemName("12,50 x 2"))
        assertEquals("cafea boabe", TextNormalizer.normalizeItemName("CAFEA BOABE 1KG"))
        assertEquals("faina", TextNormalizer.normalizeItemName("FAINA 1000G"))
    }

    @Test
    fun `keyword rules suggest default categories`() {
        val s = CategorySuggester(emptyMap(), ids)
        assertEquals(CategorySuggester.Suggestion(food, Source.RULE), s.suggest("PAINE FELIATA 500G"))
        assertEquals(food, s.suggest("Lapte Zuzu 1,5%").categoryId)
        assertEquals(drinks, s.suggest("APA MIN. BORSEC 2L").categoryId)
        assertEquals(drinks, s.suggest("BERE URSUS 0,5L").categoryId)
        assertEquals(home, s.suggest("DETERGENT ARIEL 2KG").categoryId)
        assertEquals(home, s.suggest("HARTIE IGIENICA 3 STR").categoryId)
    }

    @Test
    fun `longest keyword wins`() {
        val s = CategorySuggester(emptyMap(), ids)
        // "salata" (food) is longer than the subscriptions prefix "sala".
        assertEquals(food, s.suggest("SALATA VERDE").categoryId)
        assertEquals(food, s.suggest("SALAM SASESC").categoryId)
    }

    @Test
    fun `unknown items fall back to Altele`() {
        val s = CategorySuggester(emptyMap(), ids)
        assertEquals(CategorySuggester.Suggestion(other, Source.FALLBACK), s.suggest("XYZ QWERTY"))
        assertEquals(CategorySuggester.Suggestion(other, Source.FALLBACK), s.suggest("123"))
    }

    @Test
    fun `user corrections take priority over rules`() {
        val learned = mapOf(TextNormalizer.normalizeItemName("PAINE FELIATA") to home)
        val s = CategorySuggester(learned, ids)
        assertEquals(CategorySuggester.Suggestion(home, Source.LEARNED), s.suggest("Pâine feliată 500g"))
        // Other bread still follows the rule.
        assertEquals(food, s.suggest("PAINE INTEGRALA").categoryId)
    }

    @Test
    fun `rules for deleted default categories are skipped`() {
        val withoutDrinks = ids - DefaultCategoryKeys.DRINKS
        val s = CategorySuggester(emptyMap(), withoutDrinks)
        assertEquals(other, s.suggest("BERE URSUS").categoryId)
        val withoutOther = withoutDrinks - DefaultCategoryKeys.OTHER
        assertEquals(Source.NONE, CategorySuggester(emptyMap(), withoutOther).suggest("BERE").source)
    }
}
