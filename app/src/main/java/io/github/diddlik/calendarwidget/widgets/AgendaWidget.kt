package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository
import io.github.diddlik.calendarwidget.domain.EventOccurrence
import io.github.diddlik.calendarwidget.domain.parseConventionTitle
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
    data class PermissionMissing(val config: WidgetConfig) : AgendaState
    data class Ready(val config: WidgetConfig, val events: List<EventOccurrence>, val zone: ZoneId) : AgendaState
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
            // update() on a running session only recomposes; the refresh token makes it reload data too.
            val token = currentState(REFRESH_TOKEN)
            var current by remember { mutableStateOf(state) }
            LaunchedEffect(token) {
                if (token != null) current = loadState(context, appWidgetId)
            }
            GlanceTheme {
                Content(context, current, configure)
            }
        }
    }

    companion object {
        private val REFRESH_TOKEN = longPreferencesKey("refresh_token")

        /** Reloads calendar data and re-renders the given widget, whether or not its session is running. */
        suspend fun refresh(context: Context, id: GlanceId) {
            updateAppWidgetState(context, id) { it[REFRESH_TOKEN] = System.nanoTime() }
            AgendaWidget().update(context, id)
        }

        suspend fun refreshAll(context: Context) {
            GlanceAppWidgetManager(context).getGlanceIds(AgendaWidget::class.java).forEach { refresh(context, it) }
        }
    }

    private suspend fun loadState(context: Context, appWidgetId: Int): AgendaState {
        val config = WidgetConfigStore.get(context).get(appWidgetId) ?: return AgendaState.NotConfigured
        val repository = CalendarSourceRepository(context)
        if (!repository.hasPermission()) return AgendaState.PermissionMissing(config)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val occurrences = try {
            repository.occurrences(config.calendarIds, now.minus(Duration.ofDays(1)), now.plus(LOOKAHEAD), zone)
        } catch (_: SecurityException) {
            return AgendaState.PermissionMissing(config)
        }
        return AgendaState.Ready(config, selectAgenda(occurrences, config.filter.root, now, config.maxItems), zone)
    }
}

@Composable
private fun Content(context: Context, state: AgendaState, configure: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp),
    ) {
        val name = when (state) {
            is AgendaState.Ready -> state.config.name
            is AgendaState.PermissionMissing -> state.config.name
            AgendaState.NotConfigured -> ""
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name.ifBlank { context.getString(R.string.widget_title) },
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier.defaultWeight().clickable(configure),
            )
            // A visible edit action; tapping the name alone was not discoverable.
            Box(
                modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).clickable(configure),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_edit),
                    contentDescription = context.getString(R.string.widget_edit),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.size(20.dp),
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        when (state) {
            AgendaState.NotConfigured -> Message(context.getString(R.string.widget_not_configured), configure)
            is AgendaState.PermissionMissing -> Message(
                context.getString(R.string.widget_permission_missing),
                actionStartActivity(Intent(context, MainActivity::class.java)),
            )
            is AgendaState.Ready ->
                if (state.events.isEmpty()) Message(context.getString(R.string.widget_empty), configure)
                else LazyColumn {
                    items(state.events, itemId = { it.eventId * 31 + it.begin.toEpochMilli() }) { event ->
                        EventRow(context, event, state.zone, state.config.useNamingConvention)
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
private fun EventRow(context: Context, event: EventOccurrence, zone: ZoneId, useNamingConvention: Boolean) {
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
                text = formatWhen(context, event, zone),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            val convention = if (useNamingConvention) parseConventionTitle(event.title) else null
            if (convention != null) {
                Row {
                    Text(
                        text = convention.person,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        maxLines = 1,
                    )
                    if (convention.title.isNotEmpty()) {
                        Text(
                            text = "  " + convention.title,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(
                    text = event.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.event_no_title),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Formats in the app's language: a process started in the background keeps the system default
 * locale, while [context] resources follow the per-app language.
 */
internal fun formatWhen(context: Context, event: EventOccurrence, zone: ZoneId): String {
    val locale = context.resources.configuration.locales[0]
    val begin = event.begin.atZone(zone)
    val formatter = if (event.allDay) DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    return begin.format(formatter.withLocale(locale))
}

/** Opens the occurrence in the user's calendar app; begin/end select the specific recurrence. */
internal fun openEventIntent(event: EventOccurrence): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
