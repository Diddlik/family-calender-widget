package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.filter.FilterNode
import io.github.diddlik.calendarwidget.filter.matches
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Past events stay a short look back; more would push the upcoming ones out of the visible part of the widget. */
const val MAX_PAST_EVENTS = 3

/**
 * Selects the agenda entries for a widget: drops cancelled, declined, finished and duplicate
 * occurrences, applies [filter], and returns at most [limit] upcoming entries in a stable chronological order.
 * Ongoing occurrences (begun but not ended) stay visible until they end. The latest [MAX_PAST_EVENTS] occurrences
 * that ended after [pastFrom] are kept before them and do not count towards [limit].
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
    return past.takeLast(MAX_PAST_EVENTS) + upcoming.take(limit)
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

/** The local day on which the occurrence ends; an end at midnight belongs to the day before. */
fun EventOccurrence.lastDay(zone: ZoneId): LocalDate =
    maxOf(end.minusNanos(1).atZone(zone).toLocalDate(), begin.atZone(zone).toLocalDate())

/**
 * Groups an agenda by local day in [zone], in date order and keeping the agenda order within a day. Occurrences that
 * began before [today] and last into it appear under [today]; those that ended before it stay under their own day.
 * Multi-day occurrences appear under each day they cover, up to the last day on which another entry starts, so a
 * long event cannot fill the widget with days of its own. Timed occurrences of up to 24 hours stay on their first
 * day, so an evening event that ends after midnight is not repeated.
 */
fun groupByDay(agenda: List<EventOccurrence>, zone: ZoneId, today: LocalDate): List<Pair<LocalDate, List<EventOccurrence>>> {
    val startOfToday = today.atStartOfDay(zone).toInstant()
    fun firstDay(event: EventOccurrence): LocalDate {
        val day = event.begin.atZone(zone).toLocalDate()
        return if (event.end > startOfToday) maxOf(day, today) else day
    }
    val lastShown = agenda.maxOfOrNull(::firstDay) ?: return emptyList()
    return agenda.flatMap { event ->
        val first = firstDay(event)
        val spans = event.allDay || Duration.between(event.begin, event.end) > Duration.ofHours(24)
        val last = if (spans) minOf(event.lastDay(zone), lastShown) else first
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { it to event }.toList()
    }.groupBy({ it.first }, { it.second }).toSortedMap().toList()
}

/** Time until an occurrence starts, in the largest unit that keeps it readable. */
sealed interface Countdown {
    data object Ongoing : Countdown
    data class Minutes(val value: Int) : Countdown
    data class Hours(val value: Int) : Countdown
    data class Days(val value: Int) : Countdown
}

/** Null for past occurrences. All-day occurrences count in calendar days, as they have no start time. */
fun countdown(event: EventOccurrence, now: Instant, zone: ZoneId): Countdown? {
    if (event.isPast(now)) return null
    if (event.begin <= now) return Countdown.Ongoing
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), event.begin.atZone(zone).toLocalDate()).toInt()
    if (event.allDay) return Countdown.Days(days)
    val minutes = Duration.between(now, event.begin).toMinutes()
    return when {
        minutes < 60 -> Countdown.Minutes(maxOf(1, minutes.toInt()))
        minutes < 24 * 60 -> Countdown.Hours((minutes / 60).toInt())
        else -> Countdown.Days(days)
    }
}
