package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
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
import io.github.diddlik.calendarwidget.domain.groupByDay
import io.github.diddlik.calendarwidget.domain.parseConventionTitle
import io.github.diddlik.calendarwidget.domain.selectAgenda
import io.github.diddlik.calendarwidget.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Read window for occurrences; bounds provider work while covering appointments booked far ahead. */
private val LOOKAHEAD: Duration = Duration.ofDays(366)

private sealed interface AgendaState {
    data object NotConfigured : AgendaState
    data class PermissionMissing(val config: WidgetConfig) : AgendaState
    data class Failed(val config: WidgetConfig) : AgendaState
    data class Ready(val config: WidgetConfig, val events: List<EventOccurrence>, val zone: ZoneId) : AgendaState
}

private val AgendaState.config: WidgetConfig?
    get() = when (this) {
        AgendaState.NotConfigured -> null
        is AgendaState.PermissionMissing -> config
        is AgendaState.Failed -> config
        is AgendaState.Ready -> config
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
        // Without a calendar app that can create events the button would do nothing.
        val canCreate = context.packageManager.resolveActivity(newEventIntent(), 0) != null
        val newEventFromTemplate = actionStartActivity(
            Intent(context, NewEventActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData("calendarwidget://new-event/$appWidgetId".toUri())
        )
        provideContent {
            // update() on a running session only recomposes; the refresh token makes it reload data too.
            val token = currentState(REFRESH_TOKEN)
            var current by remember { mutableStateOf(state) }
            LaunchedEffect(token) {
                if (token != null) current = loadState(context, appWidgetId)
            }
            val (colors, background) = widgetColors(context, current.config)
            GlanceTheme(colors = colors) {
                val config = current.config
                val newEvent = when {
                    !canCreate -> null
                    config != null && (config.newEventKeyword.isNotBlank() || config.people.isNotEmpty()) -> newEventFromTemplate
                    else -> actionStartActivity(newEventIntent())
                }
                Content(context, current, configure, newEvent, background)
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
        } catch (_: RuntimeException) {
            // Calendar providers of some sync adapters fail transiently; offer a retry instead of crashing the widget.
            return AgendaState.Failed(config)
        }
        return AgendaState.Ready(config, selectAgenda(occurrences, config.filter.root, now, config.maxItems), zone)
    }
}

/** Reloads a widget whose last load failed. */
class RetryLoadAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AgendaWidget.refresh(context, glanceId)
    }
}

/**
 * Theme colors and the background with the configured opacity. Light and dark schemes follow the wallpaper on
 * Android 12+; forced themes use one scheme for day and night. A fixed accent replaces the primary color. Dynamic colors are resolved at render time,
 * so a wallpaper change shows on the next refresh.
 */
private fun widgetColors(context: Context, config: WidgetConfig?) = run {
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accent = config?.accent ?: WidgetAccent.SYSTEM
    val light = (if (dynamic) dynamicLightColorScheme(context) else lightColorScheme()).withPrimary(accent.light)
    val dark = (if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme()).withPrimary(accent.dark)
    val (day, night) = when (config?.theme ?: WidgetTheme.SYSTEM) {
        WidgetTheme.SYSTEM -> light to dark
        WidgetTheme.LIGHT -> light to light
        WidgetTheme.DARK -> dark to dark
    }
    val alpha = (config?.backgroundOpacity ?: 100).coerceIn(0, 100) / 100f
    // Matches GlanceTheme's widgetBackground, which maps to secondaryContainer.
    ColorProviders(light = day, dark = night) to DayNightColorProvider(
        day = day.secondaryContainer.copy(alpha = alpha),
        night = night.secondaryContainer.copy(alpha = alpha),
    )
}

@Composable
private fun accentColor(accent: WidgetAccent, theme: WidgetTheme): ColorProvider {
    val light = accent.light ?: return GlanceTheme.colors.primary
    val dark = accent.dark ?: return GlanceTheme.colors.primary
    return when (theme) {
        WidgetTheme.SYSTEM -> DayNightColorProvider(day = Color(light), night = Color(dark))
        WidgetTheme.LIGHT -> DayNightColorProvider(day = Color(light), night = Color(light))
        WidgetTheme.DARK -> DayNightColorProvider(day = Color(dark), night = Color(dark))
    }
}

private fun ColorScheme.withPrimary(argb: Long?) = if (argb == null) this else copy(primary = Color(argb))

@Composable
private fun Content(context: Context, state: AgendaState, configure: Action, newEvent: Action?, background: ColorProvider) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(background).cornerRadius(16.dp).padding(12.dp),
    ) {
        val name = state.config?.name.orEmpty()
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name.ifBlank { context.getString(R.string.widget_title) },
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier.defaultWeight().clickable(configure),
            )
            if (newEvent != null) HeaderButton(R.drawable.ic_widget_add, context.getString(R.string.widget_new_event), newEvent)
            // A visible edit action; tapping the name alone was not discoverable.
            HeaderButton(R.drawable.ic_widget_edit, context.getString(R.string.widget_edit), configure)
        }
        Spacer(GlanceModifier.height(4.dp))
        when (state) {
            AgendaState.NotConfigured -> Message(context.getString(R.string.widget_not_configured), configure)
            is AgendaState.PermissionMissing -> Message(
                context.getString(R.string.widget_permission_missing),
                actionStartActivity(Intent(context, MainActivity::class.java)),
            )
            is AgendaState.Failed -> Message(context.getString(R.string.widget_load_failed), actionRunCallback<RetryLoadAction>())
            is AgendaState.Ready ->
                if (state.events.isEmpty()) Message(context.getString(R.string.widget_empty), configure)
                else Agenda(context, state)
        }
    }
}

@Composable
private fun HeaderButton(icon: Int, description: String, onClick: Action) {
    Box(
        modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(20.dp),
        )
    }
}

@Composable
private fun Message(text: String, onClick: Action) {
    Box(modifier = GlanceModifier.fillMaxSize().clickable(onClick), contentAlignment = Alignment.Center) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

private sealed interface AgendaRow {
    data class Day(val date: LocalDate) : AgendaRow
    data class Event(val event: EventOccurrence) : AgendaRow
}

@Composable
private fun Agenda(context: Context, state: AgendaState.Ready) {
    val config = state.config
    val byDay = config.layout == WidgetLayout.BY_DAY
    val today = LocalDate.now(state.zone)
    val rows = if (byDay) groupByDay(state.events, state.zone, today).flatMap { (date, events) ->
        listOf(AgendaRow.Day(date)) + events.map(AgendaRow::Event)
    } else state.events.map(AgendaRow::Event)
    LazyColumn {
        items(rows, itemId = { row ->
            when (row) {
                // Negative ids cannot collide with event ids, which are derived from positive ids and timestamps.
                is AgendaRow.Day -> -1 - row.date.toEpochDay()
                is AgendaRow.Event -> row.event.eventId * 31 + row.event.begin.toEpochMilli()
            }
        }) { row ->
            when (row) {
                is AgendaRow.Day -> DayHeader(formatDay(context, row.date, today))
                is AgendaRow.Event -> EventRow(
                    context = context,
                    event = row.event,
                    time = if (byDay) formatTime(context, row.event, state.zone) else formatWhen(context, row.event, state.zone),
                    config = config,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EventRow(context: Context, event: EventOccurrence, time: String, config: WidgetConfig) {
    val compact = config.density == WidgetDensity.COMPACT
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp)
            .clickable(actionStartActivity(openEventIntent(event))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.width(4.dp).height(if (compact) 16.dp else 36.dp).cornerRadius(2.dp)
                .background(event.color?.let { Color(it) } ?: Color.Gray),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        if (compact) {
            EventTime(time)
            Spacer(GlanceModifier.width(8.dp))
            EventTitle(context, event, config)
        } else {
            Column(modifier = GlanceModifier.fillMaxHeight()) {
                EventTime(time)
                EventTitle(context, event, config)
            }
        }
    }
}

@Composable
private fun EventTime(text: String) {
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun EventTitle(context: Context, event: EventOccurrence, config: WidgetConfig) {
    val convention = if (config.useNamingConvention) parseConventionTitle(event.title) else null
    if (convention != null) {
        Row {
            Text(
                text = convention.person,
                style = TextStyle(color = accentColor(config.personAccent(convention.person), config.theme), fontWeight = FontWeight.Bold, fontSize = 14.sp),
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

private fun appLocale(context: Context) = context.resources.configuration.locales[0]

/**
 * Formats in the app's language: a process started in the background keeps the system default
 * locale, while [context] resources follow the per-app language.
 */
internal fun formatWhen(context: Context, event: EventOccurrence, zone: ZoneId): String {
    val begin = event.begin.atZone(zone)
    val formatter = if (event.allDay) DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    return begin.format(formatter.withLocale(appLocale(context)))
}

private fun formatTime(context: Context, event: EventOccurrence, zone: ZoneId): String =
    if (event.allDay) context.getString(R.string.widget_all_day)
    else event.begin.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale(context)))

private fun formatDay(context: Context, date: LocalDate, today: LocalDate): String = when (date) {
    today -> context.getString(R.string.widget_today)
    today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
    else -> {
        val locale = appLocale(context)
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale))
    }
}

/**
 * Hands event creation to the user's calendar app, which picks the calendar and the start time.
 * The app itself never writes to calendars, so it needs no WRITE_CALENDAR permission.
 */
internal fun newEventIntent(title: String? = null, calendarId: Long? = null): Intent =
    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        title?.let { putExtra(CalendarContract.Events.TITLE, it) }
        calendarId?.let { putExtra(CalendarContract.Events.CALENDAR_ID, it) }
    }

/** Opens the occurrence in the user's calendar app; begin/end select the specific recurrence. */
internal fun openEventIntent(event: EventOccurrence): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
