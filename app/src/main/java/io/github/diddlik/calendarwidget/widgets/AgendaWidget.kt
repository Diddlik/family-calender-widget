package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository
import io.github.diddlik.calendarwidget.domain.EventOccurrence
import io.github.diddlik.calendarwidget.domain.selectAgenda
import io.github.diddlik.calendarwidget.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Read window for occurrences; bounds provider work while covering appointments booked far ahead. */
private val LOOKAHEAD: Duration = Duration.ofDays(366)

private sealed interface AgendaState {
    data object NotConfigured : AgendaState
    data object PermissionMissing : AgendaState
    data class Ready(val events: List<EventOccurrence>, val zone: ZoneId) : AgendaState
}

class AgendaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadState(context, appWidgetId)
        val configure = actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Distinct data keeps one PendingIntent per widget instance.
                .setData("calendarwidget://configure/$appWidgetId".toUri())
        )
        provideContent {
            GlanceTheme {
                Content(context, state, configure)
            }
        }
    }

    private suspend fun loadState(context: Context, appWidgetId: Int): AgendaState {
        val config = WidgetConfigStore.get(context).get(appWidgetId) ?: return AgendaState.NotConfigured
        val repository = CalendarSourceRepository(context)
        if (!repository.hasPermission()) return AgendaState.PermissionMissing
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val occurrences = try {
            repository.occurrences(config.calendarIds, now.minus(Duration.ofDays(1)), now.plus(LOOKAHEAD), zone)
        } catch (_: SecurityException) {
            return AgendaState.PermissionMissing
        }
        return AgendaState.Ready(selectAgenda(occurrences, config.filter.root, now, config.maxItems), zone)
    }
}

@Composable
private fun Content(context: Context, state: AgendaState, configure: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp),
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            modifier = GlanceModifier.fillMaxWidth().clickable(configure),
        )
        Spacer(GlanceModifier.height(8.dp))
        when (state) {
            AgendaState.NotConfigured -> Message(context.getString(R.string.widget_not_configured), configure)
            AgendaState.PermissionMissing -> Message(
                context.getString(R.string.widget_permission_missing),
                actionStartActivity(Intent(context, MainActivity::class.java)),
            )
            is AgendaState.Ready ->
                if (state.events.isEmpty()) Message(context.getString(R.string.widget_empty), configure)
                else LazyColumn {
                    items(state.events, itemId = { it.eventId * 31 + it.begin.toEpochMilli() }) { event ->
                        EventRow(context, event, state.zone)
                    }
                }
        }
    }
}

@Composable
private fun Message(text: String, onClick: Action) {
    Box(modifier = GlanceModifier.fillMaxSize().clickable(onClick), contentAlignment = Alignment.Center) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

@Composable
private fun EventRow(context: Context, event: EventOccurrence, zone: ZoneId) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(openEventIntent(event))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.width(4.dp).height(36.dp).cornerRadius(2.dp)
                .background(event.color?.let { Color(it) } ?: Color.Gray),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.fillMaxHeight()) {
            Text(
                text = formatWhen(event, zone),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                text = event.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.event_no_title),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}

private fun formatWhen(event: EventOccurrence, zone: ZoneId): String {
    val begin = event.begin.atZone(zone)
    return if (event.allDay) begin.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    else begin.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}

/** Opens the occurrence in the user's calendar app; begin/end select the specific recurrence. */
private fun openEventIntent(event: EventOccurrence): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
