package io.github.diddlik.calendarwidget

import io.github.diddlik.calendarwidget.domain.EventOccurrence
import java.time.Duration
import java.time.Instant

val NOW: Instant = Instant.parse("2026-03-10T12:00:00Z")

fun event(
    eventId: Long = 1,
    calendarId: Long = 1,
    begin: Instant = NOW.plus(Duration.ofHours(1)),
    end: Instant = begin.plus(Duration.ofHours(1)),
    allDay: Boolean = false,
    title: String? = "Event",
    description: String? = null,
    location: String? = null,
    cancelled: Boolean = false,
    declined: Boolean = false,
) = EventOccurrence(eventId, calendarId, begin, end, allDay, title, description, location, null, cancelled, declined)
