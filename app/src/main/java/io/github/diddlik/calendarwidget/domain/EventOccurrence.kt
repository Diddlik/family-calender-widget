package io.github.diddlik.calendarwidget.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One expanded occurrence of a calendar event. [eventId] identifies the series;
 * an occurrence is identified by [eventId] together with [begin].
 *
 * For all-day occurrences [begin] and [end] are local midnights in the device zone (see [allDayToLocal]).
 */
data class EventOccurrence(
    val eventId: Long,
    val calendarId: Long,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean,
    val title: String?,
    val description: String?,
    val location: String?,
    val color: Int?,
    val cancelled: Boolean = false,
    val declined: Boolean = false,
)

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)

/**
 * The calendar provider stores all-day boundaries as UTC midnights. Re-anchor them to the same
 * calendar date in [zone], so an all-day event covers the user's local day, also across DST changes.
 */
fun allDayToLocal(utcMidnight: Instant, zone: ZoneId): Instant =
    utcMidnight.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
