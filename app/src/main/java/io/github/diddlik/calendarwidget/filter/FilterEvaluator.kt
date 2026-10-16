package io.github.diddlik.calendarwidget.filter

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import io.github.diddlik.calendarwidget.domain.EventOccurrence
import java.text.Normalizer
import java.time.Duration
import java.time.Instant

fun FilterNode.matches(event: EventOccurrence, now: Instant): Boolean = when (this) {
    is All -> children.all { it.matches(event, now) }
    is AnyOf -> children.any { it.matches(event, now) }
    is Not -> !child.matches(event, now)
    is CalendarIn -> event.calendarId in calendarIds
    is TextMatch -> matchesText(event)
    StartsAfterNow -> event.begin >= now
    is StartsBetween -> event.begin.toEpochMilli() in fromEpochMillis until toEpochMillis
    is StartsWithinDays -> event.begin < now.plus(Duration.ofDays(days.toLong()))
    is AllDay -> event.allDay == value
}

private fun TextMatch.matchesText(event: EventOccurrence): Boolean {
    val raw = when (field) {
        TextField.TITLE -> event.title
        TextField.DESCRIPTION -> event.description
        TextField.LOCATION -> event.location
    }.orEmpty()
    if (operator == TextOperator.MATCHES) return value.isNotBlank() && matchesRegex(value, raw)
    val text = normalize(raw)
    val query = normalize(value.trim())
    if (operator == TextOperator.NOT_CONTAINS) return query.isEmpty() || query !in text
    if (query.isEmpty()) return false
    return when (operator) {
        TextOperator.CONTAINS -> query in text
        TextOperator.STARTS_WITH -> text.trimStart().startsWith(query)
        TextOperator.EQUALS -> text.trim() == query
        TextOperator.NOT_CONTAINS, TextOperator.MATCHES -> error("handled above")
    }
}

private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

const val MAX_REGEX_LENGTH = 200

enum class RegexProblem { TOO_LONG, INVALID }

/** Why [pattern] cannot be used as a MATCHES value, or null if it can. */
fun regexProblem(pattern: String): RegexProblem? = when {
    pattern.length > MAX_REGEX_LENGTH -> RegexProblem.TOO_LONG
    compileRegex(pattern) == null -> RegexProblem.INVALID
    else -> null
}

/**
 * RE2/J runs in time linear in the input, so a pathological pattern cannot stall a widget refresh; the
 * platform engine (ICU on Android) backtracks and cannot be interrupted. RE2 syntax has no backreferences.
 */
private fun compileRegex(pattern: String): Pattern? =
    if (pattern.length > MAX_REGEX_LENGTH) null
    else try {
        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
    } catch (_: PatternSyntaxException) {
        null
    }

/** Unusable patterns count as no match. */
private fun matchesRegex(pattern: String, text: String): Boolean =
    compileRegex(pattern)?.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC))?.find() ?: false

/** Builds a filter matching titles that contain any of [keywords]; no keywords matches everything. */
fun titleContainsAny(keywords: List<String>): FilterNode {
    val terms = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return if (terms.isEmpty()) All(emptyList())
    else AnyOf(terms.map { TextMatch(TextField.TITLE, TextOperator.CONTAINS, it) })
}

/**
 * Drops what the rule editor leaves incomplete: text rules with a blank value or an unusable pattern, and groups
 * without remaining rules.
 * Returns null when nothing remains; callers then match everything.
 */
fun FilterNode.withoutIncompleteRules(): FilterNode? = when (this) {
    is All -> children.mapNotNull { it.withoutIncompleteRules() }.takeIf { it.isNotEmpty() }?.let(::All)
    is AnyOf -> children.mapNotNull { it.withoutIncompleteRules() }.takeIf { it.isNotEmpty() }?.let(::AnyOf)
    is Not -> child.withoutIncompleteRules()?.let(::Not)
    is TextMatch -> takeIf { value.isNotBlank() && (operator != TextOperator.MATCHES || regexProblem(value) == null) }
    else -> this
}
