package io.github.diddlik.calendarwidget.domain

import io.github.diddlik.calendarwidget.NOW
import io.github.diddlik.calendarwidget.event
import io.github.diddlik.calendarwidget.filter.KeywordPreset
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    fun `keeps events ended after pastFrom without counting them towards the limit`() {
        val result = selectAgenda(
            listOf(
                event(eventId = 1, begin = hours(-50), end = hours(-49)),
                event(eventId = 2, begin = hours(-30), end = hours(-29)),
                event(eventId = 3, begin = hours(-3), end = hours(-2)),
                event(eventId = 4, begin = hours(2)),
                event(eventId = 5, begin = hours(3)),
            ),
            matchAll, NOW, 1, pastFrom = hours(-48),
        )
        assertEquals(listOf(2L, 3L, 4L), result.map { it.eventId })
        assertEquals(listOf(true, true, false), result.map { it.isPast(NOW) })
    }

    @Test
    fun `keeps only the latest past events`() {
        val past = (1L..5L).map { event(eventId = it, begin = hours(-10 + it), end = hours(-9 + it)) }
        val result = selectAgenda(past, matchAll, NOW, 10, pastFrom = hours(-48))
        assertEquals(listOf(3L, 4L, 5L), result.map { it.eventId })
    }

    @Test
    fun `counts down in minutes, hours and calendar days`() {
        val zone = ZoneId.of("UTC")
        assertEquals(null, countdown(event(begin = hours(-2), end = hours(-1)), NOW, zone))
        assertEquals(Countdown.Ongoing, countdown(event(begin = hours(-1), end = hours(1)), NOW, zone))
        assertEquals(Countdown.Minutes(1), countdown(event(begin = NOW.plusSeconds(20)), NOW, zone))
        assertEquals(Countdown.Minutes(45), countdown(event(begin = NOW.plus(Duration.ofMinutes(45))), NOW, zone))
        assertEquals(Countdown.Hours(23), countdown(event(begin = hours(23).plusSeconds(1800)), NOW, zone))
        // 36 hours from noon is the day after tomorrow.
        assertEquals(Countdown.Days(2), countdown(event(begin = hours(36)), NOW, zone))
        val allDayTomorrow = event(begin = Instant.parse("2026-03-11T00:00:00Z"), end = Instant.parse("2026-03-12T00:00:00Z"), allDay = true)
        assertEquals(Countdown.Days(1), countdown(allDayTomorrow, NOW, zone))
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

    @Test
    fun `groups by local day and moves ongoing events to today`() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 3, 10)
        val ongoing = event(eventId = 1, begin = Instant.parse("2026-03-09T20:00:00Z"), end = hours(2))
        val lateToday = event(eventId = 2, begin = Instant.parse("2026-03-10T22:30:00Z"))
        // 23:30 UTC is already the next day in Berlin.
        val tomorrow = event(eventId = 3, begin = Instant.parse("2026-03-10T23:30:00Z"))
        val result = groupByDay(listOf(ongoing, lateToday, tomorrow), zone, today)
        assertEquals(listOf(today, today.plusDays(1)), result.map { it.first })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), result.map { day -> day.second.map { it.eventId } })
    }

    @Test
    fun `repeats multi-day events on each day up to the last starting entry`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 3, 10)
        val trip = event(
            eventId = 1, allDay = true,
            begin = Instant.parse("2026-03-10T00:00:00Z"), end = Instant.parse("2026-03-20T00:00:00Z"),
        )
        val overnight = event(eventId = 2, begin = Instant.parse("2026-03-10T22:00:00Z"), end = Instant.parse("2026-03-11T02:00:00Z"))
        val later = event(eventId = 3, begin = Instant.parse("2026-03-12T09:00:00Z"))
        val result = groupByDay(listOf(trip, overnight, later), zone, today)
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), result.map { it.first })
        assertEquals(listOf(listOf(1L, 2L), listOf(1L), listOf(1L, 3L)), result.map { day -> day.second.map { it.eventId } })
        assertEquals(LocalDate.of(2026, 3, 19), trip.lastDay(zone))
    }

    @Test
    fun `groups events that ended before today under their own day`() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 3, 10)
        val yesterday = event(eventId = 1, begin = Instant.parse("2026-03-09T08:00:00Z"))
        val allDayYesterday = event(
            eventId = 2,
            begin = allDayToLocal(Instant.parse("2026-03-09T00:00:00Z"), zone),
            end = allDayToLocal(Instant.parse("2026-03-10T00:00:00Z"), zone),
            allDay = true,
        )
        val result = groupByDay(listOf(allDayYesterday, yesterday), zone, today)
        assertEquals(listOf(today.minusDays(1) to listOf(allDayYesterday, yesterday)), result)
    }
}
