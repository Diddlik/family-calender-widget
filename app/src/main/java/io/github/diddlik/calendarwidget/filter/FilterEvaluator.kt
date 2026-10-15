package io.github.diddlik.calendarwidget.filter

import io.github.diddlik.calendarwidget.domain.EventOccurrence
import java.text.Normalizer
import java.time.Instant

fun FilterNode.matches(event: EventOccurrence, now: Instant): Boolean = when (this) {
    is All -> children.all { it.matches(event, now) }
    is AnyOf -> children.any { it.matches(event, now) }
    is Not -> !child.matches(event, now)
    is CalendarIn -> event.calendarId in calendarIds
    is TextMatch -> matchesText(event)
    StartsAfterNow -> event.begin >= now
    is StartsBetween -> event.begin.toEpochMilli() in fromEpochMillis until toEpochMillis
    is AllDay -> event.allDay == value
}

private fun TextMatch.matchesText(event: EventOccurrence): Boolean {
    val text = normalize(
        when (field) {
            TextField.TITLE -> event.title
            TextField.DESCRIPTION -> event.description
            TextField.LOCATION -> event.location
        }.orEmpty()
    )
    val query = normalize(value.trim())
    if (operator == TextOperator.NOT_CONTAINS) return query.isEmpty() || query !in text
    if (query.isEmpty()) return false
    return when (operator) {
        TextOperator.CONTAINS -> query in text
        TextOperator.STARTS_WITH -> text.trimStart().startsWith(query)
        TextOperator.EQUALS -> text.trim() == query
        TextOperator.NOT_CONTAINS -> error("handled above")
    }
}

private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

/** Builds a filter matching titles that contain any of [keywords]; no keywords matches everything. */
fun titleContainsAny(keywords: List<String>): FilterNode {
    val terms = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return if (terms.isEmpty()) All(emptyList())
    else AnyOf(terms.map { TextMatch(TextField.TITLE, TextOperator.CONTAINS, it) })
}

/** Inverse of [titleContainsAny], used to show a stored filter in the keyword editor. Null if the tree has another shape. */
fun titleKeywordsOf(node: FilterNode): List<String>? = when {
    node is All && node.children.isEmpty() -> emptyList()
    node is AnyOf && node.children.all { it is TextMatch && it.field == TextField.TITLE && it.operator == TextOperator.CONTAINS } ->
        node.children.map { (it as TextMatch).value }
    else -> null
}
