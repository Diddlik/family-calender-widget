package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.NOW
import io.github.diddlik.calendarwidget.event
import io.github.diddlik.calendarwidget.filter.KeywordPreset
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class AgendaTest {

    private val matchAll = titleContainsAny(emptyList())
    private fun hours(h: Long) = NOW.plus(Duration.ofHours(h))

    @Test
    fun `drops finished, cancelled and declined but keeps ongoing`() {
        val result = selectAgenda(
            listOf(
                event(eventId = 1, begin = hours(-3), end = hours(-1)),
                event(eventId = 2, begin = hours(-1), end = hours(1)),
                event(eventId = 3, cancelled = true),
                event(eventId = 4, declined = true),
                event(eventId = 5, begin = hours(2)),
            ),
            matchAll, NOW, 10,
        )
        assertEquals(listOf(2L, 5L), result.map { it.eventId })
    }

    @Test
    fun `removes duplicate occurrences and keeps distinct recurrences`() {
        val result = selectAgenda(
            listOf(
                event(eventId = 1, begin = hours(1)),
                event(eventId = 1, begin = hours(1)),
                event(eventId = 1, begin = hours(25)),
            ),
            matchAll, NOW, 10,
        )
        assertEquals(listOf(hours(1), hours(25)), result.map { it.begin })
    }

    @Test
    fun `sorts chronologically with stable tie-breakers and limits`() {
        val result = selectAgenda(
            listOf(
                event(eventId = 3, begin = hours(5), title = "B"),
                event(eventId = 2, begin = hours(5), title = "A"),
                event(eventId = 9, begin = hours(5), end = hours(29), allDay = true, title = "Z"),
                event(eventId = 1, begin = hours(1)),
                event(eventId = 4, begin = hours(9)),
            ),
            matchAll, NOW, 4,
        )
        assertEquals(listOf(1L, 9L, 2L, 3L), result.map { it.eventId })
    }

    @Test
    fun `medical preset selects only matching titles across calendars`() {
        val filter = titleContainsAny(KeywordPreset.MEDICAL.keywords)
        val result = selectAgenda(
            listOf(
                event(eventId = 1, calendarId = 1, title = "Kinderarzt Max", begin = hours(3)),
                event(eventId = 2, calendarId = 2, title = "Elternabend", begin = hours(2)),
                event(eventId = 3, calendarId = 3, title = "IMPFUNG Lena", begin = hours(1)),
                event(eventId = 4, calendarId = 2, title = null, begin = hours(4)),
                event(eventId = 5, calendarId = 2, title = "Praxis Dr. Weber", begin = hours(5)),
            ),
            filter, NOW, 10,
        )
        assertEquals(listOf(3L, 1L, 5L), result.map { it.eventId })
    }

    @Test
    fun `all-day boundaries map to local midnight across DST`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 is the spring-forward day in Berlin (23 hours long).
        val begin = allDayToLocal(Instant.parse("2026-03-29T00:00:00Z"), berlin)
        val end = allDayToLocal(Instant.parse("2026-03-30T00:00:00Z"), berlin)
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), begin)
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), end)
        assertEquals(Duration.ofHours(23), Duration.between(begin, end))

        val newYork = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-29T04:00:00Z"), allDayToLocal(Instant.parse("2026-03-29T00:00:00Z"), newYork))
    }

    @Test
    fun `all-day event stays visible during its local day`() {
        val zone = ZoneId.of("Europe/Berlin")
        val begin = allDayToLocal(Instant.parse("2026-03-10T00:00:00Z"), zone)
        val end = allDayToLocal(Instant.parse("2026-03-11T00:00:00Z"), zone)
        val allDay = event(begin = begin, end = end, allDay = true)
        assertEquals(1, selectAgenda(listOf(allDay), matchAll, Instant.parse("2026-03-10T22:30:00Z"), 10).size)
        assertEquals(0, selectAgenda(listOf(allDay), matchAll, Instant.parse("2026-03-10T23:00:00Z"), 10).size)
    }
}
