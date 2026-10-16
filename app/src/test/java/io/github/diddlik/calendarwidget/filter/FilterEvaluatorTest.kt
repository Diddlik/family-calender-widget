package io.github.diddlik.calendarwidget.filter

import io.github.diddlik.calendarwidget.NOW
import io.github.diddlik.calendarwidget.event
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class FilterEvaluatorTest {

    private fun title(op: TextOperator, value: String) = TextMatch(TextField.TITLE, op, value)
    private val yes = All(emptyList())
    private val no = AnyOf(emptyList())

    @Test
    fun `empty groups follow neutral elements`() {
        assertTrue(yes.matches(event(), NOW))
        assertFalse(no.matches(event(), NOW))
    }

    @Test
    fun `groups combine children`() {
        assertTrue(All(listOf(yes, yes)).matches(event(), NOW))
        assertFalse(All(listOf(yes, no)).matches(event(), NOW))
        assertTrue(AnyOf(listOf(no, yes)).matches(event(), NOW))
        assertTrue(Not(no).matches(event(), NOW))
        assertFalse(Not(yes).matches(event(), NOW))
    }

    @Test
    fun `nested tree combines calendar and keyword rules`() {
        val filter = All(
            listOf(
                CalendarIn(setOf(1, 2, 3)),
                AnyOf(listOf(title(TextOperator.CONTAINS, "Arzt"), title(TextOperator.CONTAINS, "Praxis"))),
                Not(title(TextOperator.CONTAINS, "abgesagt")),
            )
        )
        assertTrue(filter.matches(event(calendarId = 2, title = "Zahnarzt Lena"), NOW))
        assertFalse(filter.matches(event(calendarId = 4, title = "Zahnarzt Lena"), NOW))
        assertFalse(filter.matches(event(calendarId = 1, title = "Fußball"), NOW))
        assertFalse(filter.matches(event(calendarId = 1, title = "Praxis abgesagt"), NOW))
    }

    @Test
    fun `text matching is case-insensitive and unicode-normalized`() {
        assertTrue(title(TextOperator.CONTAINS, "ARZT").matches(event(title = "Hausarzt"), NOW))
        assertTrue(title(TextOperator.CONTAINS, "ÄRZTIN").matches(event(title = "Frauenärztin"), NOW))
        // Decomposed "a + combining diaeresis" must match precomposed "ä".
        assertTrue(title(TextOperator.CONTAINS, "ärztin").matches(event(title = "Frauenärztin"), NOW))
        assertTrue(title(TextOperator.STARTS_WITH, "impf").matches(event(title = "Impfung Max"), NOW))
        assertTrue(title(TextOperator.EQUALS, "praxis").matches(event(title = " Praxis "), NOW))
        assertFalse(title(TextOperator.EQUALS, "praxis").matches(event(title = "Praxis Dr. X"), NOW))
    }

    @Test
    fun `missing fields never crash and count as empty`() {
        val noTitle = event(title = null)
        assertFalse(title(TextOperator.CONTAINS, "x").matches(noTitle, NOW))
        assertTrue(title(TextOperator.NOT_CONTAINS, "x").matches(noTitle, NOW))
        assertFalse(TextMatch(TextField.LOCATION, TextOperator.EQUALS, "x").matches(noTitle, NOW))
        assertTrue(TextMatch(TextField.DESCRIPTION, TextOperator.CONTAINS, "Befund").matches(event(description = "Befund holen"), NOW))
    }

    @Test
    fun `blank query matches nothing positively`() {
        assertFalse(title(TextOperator.CONTAINS, " ").matches(event(), NOW))
        assertFalse(title(TextOperator.EQUALS, "").matches(event(title = ""), NOW))
        assertTrue(title(TextOperator.NOT_CONTAINS, "").matches(event(), NOW))
    }

    @Test
    fun `time predicates use the injected now`() {
        val past = event(begin = NOW.minus(Duration.ofMinutes(1)))
        assertFalse(StartsAfterNow.matches(past, NOW))
        assertTrue(StartsAfterNow.matches(event(begin = NOW), NOW))
        val range = StartsBetween(NOW.toEpochMilli(), NOW.plus(Duration.ofDays(1)).toEpochMilli())
        assertTrue(range.matches(event(begin = NOW), NOW))
        assertFalse(range.matches(event(begin = NOW.plus(Duration.ofDays(1))), NOW))
        assertTrue(AllDay(true).matches(event(allDay = true), NOW))
        assertFalse(AllDay(true).matches(event(allDay = false), NOW))
    }

    @Test
    fun `definition round-trips through JSON`() {
        val definition = FilterDefinition(
            root = All(
                listOf(
                    Not(StartsAfterNow), CalendarIn(setOf(7)), title(TextOperator.CONTAINS, "Arzt"), AllDay(false),
                    StartsWithinDays(30), title(TextOperator.MATCHES, "^arzt"),
                )
            )
        )
        assertEquals(definition, Json.decodeFromString<FilterDefinition>(Json.encodeToString(definition)))
    }

    @Test
    fun `keyword helper builds an any-title group`() {
        assertEquals(
            AnyOf(listOf(title(TextOperator.CONTAINS, "Arzt"), title(TextOperator.CONTAINS, "Praxis"))),
            titleContainsAny(listOf(" Arzt", "", "Praxis", "Arzt")),
        )
        assertTrue(titleContainsAny(emptyList()).matches(event(title = null), NOW))
    }

    @Test
    fun `incomplete rules and empty groups are dropped`() {
        val tree = All(
            listOf(
                title(TextOperator.CONTAINS, "Arzt"),
                title(TextOperator.NOT_CONTAINS, " "),
                AnyOf(listOf(title(TextOperator.EQUALS, ""))),
                Not(AnyOf(listOf(title(TextOperator.CONTAINS, "abgesagt"), All(emptyList())))),
                AllDay(false),
            )
        )
        assertEquals(
            All(listOf(title(TextOperator.CONTAINS, "Arzt"), Not(AnyOf(listOf(title(TextOperator.CONTAINS, "abgesagt")))), AllDay(false))),
            tree.withoutIncompleteRules(),
        )
        assertNull(AnyOf(listOf(title(TextOperator.CONTAINS, ""))).withoutIncompleteRules())
        assertNull(Not(All(emptyList())).withoutIncompleteRules())
        assertNull(title(TextOperator.MATCHES, "(").withoutIncompleteRules())
    }

    @Test
    fun `starts within days covers ongoing and upcoming occurrences`() {
        val week = StartsWithinDays(7)
        assertTrue(week.matches(event(begin = NOW.minus(Duration.ofHours(1))), NOW))
        assertTrue(week.matches(event(begin = NOW.plus(Duration.ofDays(7)).minusMillis(1)), NOW))
        assertFalse(week.matches(event(begin = NOW.plus(Duration.ofDays(7))), NOW))
    }

    @Test
    fun `regex matches anywhere, ignores case and treats unusable patterns as no match`() {
        val rule = { pattern: String -> TextMatch(TextField.TITLE, TextOperator.MATCHES, pattern) }
        assertTrue(rule("^arzt\\b").matches(event(title = "ARZT - Anna"), NOW))
        assertTrue(rule("(zahn|haus)arzt").matches(event(title = "Termin Zahnarzt"), NOW))
        assertTrue(rule("ä").matches(event(title = "PRÄXIS"), NOW))
        assertFalse(rule("^Arzt$").matches(event(title = "Arzt Anna"), NOW))
        assertFalse(rule("(").matches(event(title = "("), NOW))
        assertFalse(rule(" ").matches(event(title = " "), NOW))
        assertFalse(rule("a".repeat(MAX_REGEX_LENGTH + 1)).matches(event(title = "a".repeat(300)), NOW))
        assertFalse(rule("x").matches(event(title = null), NOW))
    }

    @Test
    fun `pathological regex finishes in linear time`() {
        val title = "a".repeat(5_000) + "!"
        val started = System.nanoTime()
        assertFalse(TextMatch(TextField.TITLE, TextOperator.MATCHES, "^(a+)+$").matches(event(title = title), NOW))
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2))
    }

    @Test
    fun `regex problems are reported`() {
        assertNull(regexProblem("arzt|praxis"))
        assertEquals(RegexProblem.INVALID, regexProblem("(arzt"))
        assertEquals(RegexProblem.INVALID, regexProblem("(a)\\1"))
        assertEquals(RegexProblem.TOO_LONG, regexProblem("a".repeat(MAX_REGEX_LENGTH + 1)))
    }
}
