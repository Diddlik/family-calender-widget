package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.filter.FilterNode
import io.github.diddlik.calendarwidget.filter.matches
import java.time.Instant

/**
 * Selects the agenda entries for a widget: drops cancelled, declined, finished and duplicate
 * occurrences, applies [filter], and returns at most [limit] entries in a stable chronological order.
 * Ongoing occurrences (begun but not ended) stay visible until they end.
 */
fun selectAgenda(
    occurrences: List<EventOccurrence>,
    filter: FilterNode,
    now: Instant,
    limit: Int,
): List<EventOccurrence> = occurrences
    .asSequence()
    .filter { !it.cancelled && !it.declined }
    .filter { it.end > now || it.begin >= now }
    .distinctBy { it.eventId to it.begin }
    .filter { filter.matches(it, now) }
    .sortedWith(agendaOrder)
    .take(limit)
    .toList()

private val agendaOrder = compareBy<EventOccurrence>(
    { it.begin },
    { !it.allDay },
    { it.end },
    { it.title.orEmpty() },
    { it.eventId },
)
