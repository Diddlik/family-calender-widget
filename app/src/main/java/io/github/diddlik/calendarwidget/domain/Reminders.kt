package io.github.diddlik.calendarwidget.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** All-day events have no start time; their reminders count back from this local time on the event day. */
val ALL_DAY_REMINDER_ANCHOR: LocalTime = LocalTime.of(9, 0)

data class Reminder(val event: EventOccurrence, val at: Instant)

/** Whole days count back in local time, so "1 day before" keeps the local clock time across DST changes. */
fun reminderTime(event: EventOccurrence, minutesBefore: Int, zone: ZoneId): Instant {
    val begin = event.begin.atZone(zone)
    val anchor = if (event.allDay) begin.toLocalDate().atTime(ALL_DAY_REMINDER_ANCHOR).atZone(zone) else begin
    return anchor.minusDays(minutesBefore / MINUTES_PER_DAY).minusMinutes(minutesBefore % MINUTES_PER_DAY).toInstant()
}

private const val MINUTES_PER_DAY = 24L * 60

/** Reminders of [events] that fall due in (after, until], skipping events that already ended. */
fun dueReminders(
    events: List<EventOccurrence>,
    minutesBefore: Collection<Int>,
    zone: ZoneId,
    after: Instant,
    until: Instant,
): List<Reminder> = events
    .filter { it.end > until }
    .flatMap { event -> minutesBefore.map { Reminder(event, reminderTime(event, it, zone)) } }
    .filter { it.at > after && it.at <= until }
    .sortedBy { it.at }

/** The earliest reminder time of [events] after [after], or null when none is pending. */
fun nextReminderTime(events: List<EventOccurrence>, minutesBefore: Collection<Int>, zone: ZoneId, after: Instant): Instant? =
    events.flatMap { event -> minutesBefore.map { reminderTime(event, it, zone) } }.filter { it > after }.minOrNull()
