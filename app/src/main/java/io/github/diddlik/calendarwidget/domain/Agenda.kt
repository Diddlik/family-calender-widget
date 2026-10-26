package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.filter.FilterNode
import io.github.diddlik.calendarwidget.filter.matches
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Selects the agenda entries for a widget: drops cancelled, declined, finished and duplicate
 * occurrences, applies [filter], and returns at most [limit] upcoming entries in a stable chronological order.
 * Ongoing occurrences (begun but not ended) stay visible until they end. Occurrences that ended after
 * [pastFrom] are kept before them and do not count towards [limit], so past events never push out upcoming ones.
 */
fun selectAgenda(
    occurrences: List<EventOccurrence>,
    filter: FilterNode,
    now: Instant,
    limit: Int,
    pastFrom: Instant = now,
): List<EventOccurrence> {
    val (past, upcoming) = occurrences
        .asSequence()
        .filter { !it.cancelled && !it.declined }
        .filter { it.end > pastFrom || it.begin >= now }
        .distinctBy { it.eventId to it.begin }
        .filter { filter.matches(it, now) }
        .sortedWith(agendaOrder)
        .partition { it.isPast(now) }
    return past + upcoming.take(limit)
}

/** Zero-length occurrences at [now] have not happened yet. */
fun EventOccurrence.isPast(now: Instant): Boolean = end <= now && begin < now

private val agendaOrder = compareBy<EventOccurrence>(
    { it.begin },
    { !it.allDay },
    { it.end },
    { it.title.orEmpty() },
    { it.eventId },
)

/**
 * Groups an agenda by local day in [zone], keeping its order. Occurrences that began before [today]
 * and last into it appear under [today]; those that ended before it stay under their own day.
 */
fun groupByDay(agenda: List<EventOccurrence>, zone: ZoneId, today: LocalDate): List<Pair<LocalDate, List<EventOccurrence>>> {
    val startOfToday = today.atStartOfDay(zone).toInstant()
    return agenda.groupBy {
        val day = it.begin.atZone(zone).toLocalDate()
        if (it.end > startOfToday) maxOf(day, today) else day
    }.toList()
}
