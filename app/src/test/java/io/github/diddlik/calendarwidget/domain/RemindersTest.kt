package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.NOW
import io.github.diddlik.calendarwidget.event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class RemindersTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `timed events count back from their start`() {
        val e = event(begin = Instant.parse("2026-03-10T15:00:00Z"))
        assertEquals(Instant.parse("2026-03-10T14:00:00Z"), reminderTime(e, 60, berlin))
        assertEquals(Instant.parse("2026-03-09T15:00:00Z"), reminderTime(e, 24 * 60, berlin))
    }

    @Test
    fun `all-day events count back from nine o'clock local time, also across DST`() {
        // 29 March 2026 is the DST switch in Berlin; local midnight is 23:00 UTC the day before.
        val e = event(begin = Instant.parse("2026-03-28T23:00:00Z"), end = Instant.parse("2026-03-29T22:00:00Z"), allDay = true)
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), reminderTime(e, 0, berlin))
        assertEquals(Instant.parse("2026-03-28T08:00:00Z"), reminderTime(e, 24 * 60, berlin)) // 09:00 CET
    }

    @Test
    fun `due reminders fall into the half-open window in time order`() {
        val soon = event(eventId = 1, begin = NOW.plus(Duration.ofMinutes(50)))
        val later = event(eventId = 2, begin = NOW.plus(Duration.ofHours(3)))
        val due = dueReminders(listOf(later, soon), listOf(60, 15), berlin, after = NOW.minus(Duration.ofMinutes(20)), until = NOW)
        assertEquals(listOf(Reminder(soon, NOW.minus(Duration.ofMinutes(10)))), due)
        // The window start is exclusive, so a reminder exactly at "after" was delivered by the previous check.
        assertEquals(emptyList<Reminder>(), dueReminders(listOf(soon), listOf(60), berlin, after = NOW.minus(Duration.ofMinutes(10)), until = NOW))
    }

    @Test
    fun `ended events are not reminded after a long gap`() {
        val past = event(begin = NOW.minus(Duration.ofHours(3)))
        assertEquals(emptyList<Reminder>(), dueReminders(listOf(past), listOf(60), berlin, after = NOW.minus(Duration.ofDays(1)), until = NOW))
    }

    @Test
    fun `next reminder is the earliest one still ahead`() {
        val e = event(begin = NOW.plus(Duration.ofHours(2)))
        assertEquals(NOW.plus(Duration.ofMinutes(105)), nextReminderTime(listOf(e), listOf(60, 15), berlin, NOW.plus(Duration.ofMinutes(61))))
        assertNull(nextReminderTime(listOf(e), listOf(60), berlin, NOW.plus(Duration.ofHours(2))))
    }
}
