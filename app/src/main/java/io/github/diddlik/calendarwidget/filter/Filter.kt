package io.github.diddlik.calendarwidget.filter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versioned, JSON-serializable filter tree. Bump [CURRENT_VERSION] and migrate when the schema changes. */
@Serializable
data class FilterDefinition(
    val version: Int = CURRENT_VERSION,
    val root: FilterNode,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
sealed interface FilterNode

/** Matches when every child matches; an empty group matches everything. */
@Serializable
@SerialName("all")
data class All(val children: List<FilterNode>) : FilterNode

/** Matches when at least one child matches; an empty group matches nothing. */
@Serializable
@SerialName("any")
data class AnyOf(val children: List<FilterNode>) : FilterNode

/** NOT takes exactly one child by construction. */
@Serializable
@SerialName("not")
data class Not(val child: FilterNode) : FilterNode

@Serializable
@SerialName("calendar_in")
data class CalendarIn(val calendarIds: Set<Long>) : FilterNode

/**
 * Text predicate. Matching is case-insensitive and Unicode-normalized; a missing field counts as empty text.
 * A blank [value] never matches CONTAINS, STARTS_WITH, EQUALS or MATCHES, and NOT_CONTAINS is the negation of CONTAINS.
 * MATCHES treats [value] as a regular expression found anywhere in the text; see [regexProblem] for its guards.
 */
@Serializable
@SerialName("text")
data class TextMatch(val field: TextField, val operator: TextOperator, val value: String) : FilterNode

/** Occurrence begins at or after the evaluation time. */
@Serializable
@SerialName("starts_after_now")
data object StartsAfterNow : FilterNode

/** Occurrence begins within [fromEpochMillis] inclusive and [toEpochMillis] exclusive. */
@Serializable
@SerialName("starts_between")
data class StartsBetween(val fromEpochMillis: Long, val toEpochMillis: Long) : FilterNode

/** Occurrence begins before [days] times 24 hours from the evaluation time; ongoing occurrences match too. */
@Serializable
@SerialName("starts_within_days")
data class StartsWithinDays(val days: Int) : FilterNode

@Serializable
@SerialName("all_day")
data class AllDay(val value: Boolean) : FilterNode

@Serializable
enum class TextField { TITLE, DESCRIPTION, LOCATION }

@Serializable
enum class TextOperator { CONTAINS, NOT_CONTAINS, STARTS_WITH, EQUALS, MATCHES }
