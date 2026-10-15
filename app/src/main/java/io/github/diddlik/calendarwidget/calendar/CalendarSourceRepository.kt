package io.github.diddlik.calendarwidget.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import io.github.diddlik.calendarwidget.domain.CalendarSource
import io.github.diddlik.calendarwidget.domain.EventOccurrence
import io.github.diddlik.calendarwidget.domain.allDayToLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Read-only access to calendars that are already synchronized onto the device. */
class CalendarSourceRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<CalendarSource> = withContext(Dispatchers.IO) {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR)
        context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(CalendarSource(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
                    }
                }
            }.orEmpty()
    }

    /**
     * Expanded occurrences overlapping [from, to) from [calendarIds]. Instances expands recurring events,
     * which a plain Events query would not.
     */
    suspend fun occurrences(calendarIds: Set<Long>, from: Instant, to: Instant, zone: ZoneId): List<EventOccurrence> =
        withContext(Dispatchers.IO) {
            if (calendarIds.isEmpty()) return@withContext emptyList()
            val uri = Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from.toEpochMilli())
                ContentUris.appendId(it, to.toEpochMilli())
            }.build()
            val projection = arrayOf(
                Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
                Instances.TITLE, Instances.DESCRIPTION, Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR,
                Instances.STATUS, Instances.SELF_ATTENDEE_STATUS,
            )
            val selection = "${Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})"
            val args = calendarIds.map { it.toString() }.toTypedArray()
            context.contentResolver.query(uri, projection, selection, args, "${Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val allDay = c.getInt(4) != 0
                        val begin = Instant.ofEpochMilli(c.getLong(2))
                        val end = Instant.ofEpochMilli(c.getLong(3))
                        add(
                            EventOccurrence(
                                eventId = c.getLong(0),
                                calendarId = c.getLong(1),
                                begin = if (allDay) allDayToLocal(begin, zone) else begin,
                                end = if (allDay) allDayToLocal(end, zone) else end,
                                allDay = allDay,
                                title = c.getString(5),
                                description = c.getString(6),
                                location = c.getString(7),
                                color = if (c.isNull(8)) null else c.getInt(8),
                                cancelled = !c.isNull(9) && c.getInt(9) == Events.STATUS_CANCELED,
                                declined = !c.isNull(10) && c.getInt(10) == Attendees.ATTENDEE_STATUS_DECLINED,
                            )
                        )
                    }
                }
            }.orEmpty()
        }
}
