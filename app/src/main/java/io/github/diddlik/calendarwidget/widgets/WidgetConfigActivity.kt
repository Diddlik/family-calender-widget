package io.github.diddlik.calendarwidget.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.net.toUri
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository
import io.github.diddlik.calendarwidget.domain.CalendarSource
import io.github.diddlik.calendarwidget.filter.All
import io.github.diddlik.calendarwidget.filter.FilterDefinition
import io.github.diddlik.calendarwidget.filter.FilterNode
import io.github.diddlik.calendarwidget.filter.KeywordPreset
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import io.github.diddlik.calendarwidget.filter.withoutIncompleteRules
import io.github.diddlik.calendarwidget.ui.AppTheme
import io.github.diddlik.calendarwidget.ui.CalendarPermissionGate
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Configures one widget instance: name, calendar sources and groups, filter rules and profiles, title display, item count, reminders and appearance. */
class WidgetConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without saving must cancel widget placement.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.config_title)) }) }) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        CalendarPermissionGate {
                            ConfigForm(appWidgetId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ConfigForm(appWidgetId: Int) {
        val store = remember { WidgetConfigStore.get(this) }
        val repository = remember { CalendarSourceRepository(this) }
        val scope = rememberCoroutineScope()
        var calendars by remember { mutableStateOf<List<CalendarSource>?>(null) }
        var name by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(emptySet<Long>()) }
        var filter by remember { mutableStateOf<FilterNode>(titleContainsAny(emptyList())) }
        var useNamingConvention by remember { mutableStateOf(false) }
        var maxItems by remember { mutableFloatStateOf(WidgetConfig.DEFAULT_MAX_ITEMS.toFloat()) }
        var pastDays by remember { mutableFloatStateOf(0f) }
        var reminderMinutes by remember { mutableStateOf(emptySet<Int>()) }
        var reminderStyle by remember { mutableStateOf(ReminderStyle.ALERT) }
        var newEventKeyword by remember { mutableStateOf("") }
        var people by remember { mutableStateOf("") }
        var personColors by remember { mutableStateOf(emptyMap<String, WidgetAccent>()) }
        var appearance by remember { mutableStateOf(Appearance()) }
        var profileId by remember { mutableStateOf<String?>(null) }
        var stored by remember { mutableStateOf(StoredConfig()) }
        var naming by remember { mutableStateOf<Naming?>(null) }

        LaunchedEffect(appWidgetId) {
            stored = store.stored()
            val existing = stored.widgets[appWidgetId]
            if (existing != null) {
                profileId = existing.profileId
                name = existing.name
                selected = existing.calendarIds
                filter = existing.filter.root
                useNamingConvention = existing.useNamingConvention
                maxItems = existing.maxItems.toFloat()
                pastDays = existing.pastDays.toFloat()
                reminderMinutes = existing.reminderMinutes
                reminderStyle = existing.reminderStyle
                newEventKeyword = existing.newEventKeyword
                people = existing.people.joinToString(", ")
                personColors = existing.personColors
                appearance = Appearance(
                    existing.theme, existing.backgroundOpacity, existing.layout, existing.density, existing.accent,
                    existing.showCountdown, existing.showEndTime, existing.showLocation,
                )
            }
            calendars = repository.calendars()
        }

        val available = calendars
        if (available == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        val availableIds = available.map { it.id }.toSet()
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.config_name_label)) },
                placeholder = { Text(stringResource(R.string.widget_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.config_calendars), style = MaterialTheme.typography.titleMedium)
            if (available.isEmpty()) {
                Text(stringResource(R.string.config_no_calendars), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stored.calendarGroups.isNotEmpty()) {
                    SavedMenu(
                        label = stringResource(R.string.config_group_apply),
                        items = stored.calendarGroups.mapValues { it.value.name },
                        noneLabel = null,
                        // Calendars removed from the device since the group was saved are skipped.
                        onSelect = { id -> stored.calendarGroups[id]?.let { selected = it.calendarIds intersect availableIds } },
                        onDelete = { id -> scope.launch { store.removeCalendarGroup(id); stored = store.stored() } },
                    )
                }
                TextButton(enabled = selected.isNotEmpty(), onClick = { naming = Naming.CALENDAR_GROUP }) {
                    Text(stringResource(R.string.config_group_save))
                }
            }
            CalendarTree(available, selected) { selected = it }

            Text(stringResource(R.string.config_filter), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.config_filter_hint), style = MaterialTheme.typography.bodySmall)
            if (stored.profiles.isNotEmpty()) {
                Text(stringResource(R.string.config_profile), style = MaterialTheme.typography.bodyMedium)
                SavedMenu(
                    label = profileId?.let { stored.profiles[it]?.name } ?: stringResource(R.string.config_profile_none),
                    items = stored.profiles.mapValues { it.value.name },
                    noneLabel = stringResource(R.string.config_profile_none),
                    onSelect = { id ->
                        profileId = id
                        id?.let { stored.profiles[it] }?.let { filter = it.filter.root }
                    },
                    onDelete = { id ->
                        if (profileId == id) profileId = null
                        scope.launch { store.removeProfile(id); stored = store.stored() }
                    },
                )
            }
            if (profileId != null) {
                Text(stringResource(R.string.config_profile_shared), style = MaterialTheme.typography.bodySmall)
            }
            FilterEditor(filter) { filter = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    filter = titleContainsAny(KeywordPreset.MEDICAL.keywords)
                    if (newEventKeyword.isBlank()) newEventKeyword = KeywordPreset.MEDICAL.keywords.first()
                }) {
                    Text(stringResource(R.string.config_preset_medical))
                }
                TextButton(onClick = { naming = Naming.PROFILE }) { Text(stringResource(R.string.config_profile_save)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(value = useNamingConvention, role = Role.Switch, onValueChange = { useNamingConvention = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_convention), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.config_convention_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = useNamingConvention, onCheckedChange = null)
            }

            val count = maxItems.roundToInt()
            Text(pluralStringResource(R.plurals.config_max_items, count, count), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = maxItems,
                onValueChange = { maxItems = it },
                valueRange = 1f..WidgetConfig.MAX_ITEMS_LIMIT.toFloat(),
                steps = WidgetConfig.MAX_ITEMS_LIMIT - 2,
            )

            val past = pastDays.roundToInt()
            Text(
                if (past == 0) stringResource(R.string.config_past_days_off)
                else pluralStringResource(R.plurals.config_past_days, past, past),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.config_past_days_hint), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = pastDays,
                onValueChange = { pastDays = it },
                valueRange = 0f..WidgetConfig.MAX_PAST_DAYS.toFloat(),
                steps = WidgetConfig.MAX_PAST_DAYS - 1,
            )

            Text(stringResource(R.string.config_new_events), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.config_new_events_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = newEventKeyword,
                onValueChange = { newEventKeyword = it },
                label = { Text(stringResource(R.string.config_new_event_keyword)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = people,
                onValueChange = { people = it },
                label = { Text(stringResource(R.string.config_people)) },
                supportingText = { Text(stringResource(R.string.config_people_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            val personNames = parsePeople(people)
            personNames.forEach { person ->
                AccentChoice(person, personColors[person] ?: WidgetAccent.SYSTEM) {
                    personColors = if (it == WidgetAccent.SYSTEM) personColors - person else personColors + (person to it)
                }
            }

            Reminders(reminderMinutes, reminderStyle, { reminderMinutes = it }, { reminderStyle = it })

            AppearanceSection(appearance) { appearance = it }

            if (selected.isEmpty()) {
                Text(stringResource(R.string.config_select_calendar), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val config = WidgetConfig(
                        // Drop calendars that no longer exist on the device.
                        calendarIds = selected intersect availableIds,
                        filter = cleaned(filter),
                        maxItems = count,
                        pastDays = past,
                        name = name.trim(),
                        useNamingConvention = useNamingConvention,
                        reminderMinutes = reminderMinutes,
                        reminderStyle = reminderStyle,
                        newEventKeyword = newEventKeyword.trim(),
                        people = personNames,
                        // Colors of removed names would otherwise linger in the stored configuration.
                        personColors = personColors.filterKeys { it in personNames },
                        theme = appearance.theme,
                        backgroundOpacity = appearance.backgroundOpacity,
                        layout = appearance.layout,
                        density = appearance.density,
                        accent = appearance.accent,
                        showCountdown = appearance.showCountdown,
                        showEndTime = appearance.showEndTime,
                        showLocation = appearance.showLocation,
                        profileId = profileId,
                    )
                    scope.launch { save(appWidgetId, config) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.config_save)) }
        }

        naming?.let { kind ->
            NameDialog(
                title = if (kind == Naming.PROFILE) R.string.config_profile_new else R.string.config_group_new,
                onDismiss = { naming = null },
                onConfirm = { newName ->
                    naming = null
                    val id = UUID.randomUUID().toString()
                    scope.launch {
                        if (kind == Naming.PROFILE) {
                            store.putProfile(id, FilterProfile(newName, cleaned(filter)))
                            profileId = id
                        } else {
                            store.putCalendarGroup(id, CalendarGroup(newName, selected intersect availableIds))
                        }
                        stored = store.stored()
                    }
                },
            )
        }
    }

    private fun cleaned(filter: FilterNode) = FilterDefinition(root = filter.withoutIncompleteRules() ?: All(emptyList()))

    private suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        WidgetConfigStore.get(this).put(appWidgetId, config)
        WidgetRefresh.schedule(this)
        // Other widgets sharing the profile received the same filter.
        if (config.profileId != null) AgendaWidget.refreshAll(this)
        else AgendaWidget.refresh(this, GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId))
        ReminderNotifier.check(this)
        setResult(RESULT_OK, resultIntent(appWidgetId))
        finish()
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

private enum class Naming { PROFILE, CALENDAR_GROUP }

/** Menu of saved items; each can be applied or deleted. [noneLabel] adds an entry that selects nothing. */
@Composable
private fun SavedMenu(
    label: String,
    items: Map<String, String>,
    noneLabel: String?,
    onSelect: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { expanded = false; onSelect(null) })
            }
            items.entries.sortedBy { it.value.lowercase() }.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { expanded = false; onSelect(id) },
                    trailingIcon = {
                        IconButton(onClick = { onDelete(id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.config_delete_named, name))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.config_name_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(stringResource(R.string.config_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.config_cancel)) } },
    )
}

/** Reminder offsets as toggle chips; turning one on asks for the notification permission where needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reminders(minutes: Set<Int>, style: ReminderStyle, onChange: (Set<Int>) -> Unit, onStyleChange: (ReminderStyle) -> Unit) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var exactAlarms by remember { mutableStateOf(ReminderNotifier.canScheduleExactAlarms(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    // Both settings can change in the system settings screens opened below.
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactAlarms = ReminderNotifier.canScheduleExactAlarms(context)
        onPauseOrDispose {}
    }
    Text(stringResource(R.string.config_reminders), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.config_reminders_hint), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetConfig.REMINDER_OPTIONS.forEach { option ->
            val checked = option in minutes
            FilterChip(
                selected = checked,
                onClick = {
                    if (!checked && !notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onChange(if (checked) minutes - option else minutes + option)
                },
                label = { Text(reminderLabel(option)) },
            )
        }
    }
    if (minutes.isNotEmpty()) {
        Choice(
            R.string.config_reminder_style,
            ReminderStyle.entries,
            style,
            { stringResource(if (it == ReminderStyle.ALERT) R.string.config_reminder_style_alert else R.string.config_reminder_style_silent) },
            onStyleChange,
        )
        TextButton(onClick = {
            // The channel must exist before its settings screen can be opened.
            ReminderNotifier.ensureChannels(context)
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifier.CHANNEL_ALERT)
            )
        }) { Text(stringResource(R.string.config_reminder_sound)) }
    }
    if (minutes.isNotEmpty() && !notificationsEnabled) {
        Text(
            stringResource(R.string.config_reminders_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }) { Text(stringResource(R.string.permission_open_settings)) }
    } else if (minutes.isNotEmpty() && !exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Text(stringResource(R.string.config_reminders_inexact), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            )
        }) { Text(stringResource(R.string.config_reminders_allow_exact)) }
    }
}

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

@Composable
private fun reminderLabel(minutes: Int): String = when {
    minutes % MINUTES_PER_WEEK == 0 -> (minutes / MINUTES_PER_WEEK).let { pluralStringResource(R.plurals.reminder_weeks, it, it) }
    minutes % MINUTES_PER_DAY == 0 -> (minutes / MINUTES_PER_DAY).let { pluralStringResource(R.plurals.reminder_days, it, it) }
    minutes % 60 == 0 -> (minutes / 60).let { pluralStringResource(R.plurals.reminder_hours, it, it) }
    else -> pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
}

private data class Appearance(
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val backgroundOpacity: Int = 100,
    val layout: WidgetLayout = WidgetLayout.LIST,
    val density: WidgetDensity = WidgetDensity.COMFORTABLE,
    val accent: WidgetAccent = WidgetAccent.SYSTEM,
    val showCountdown: Boolean = false,
    val showEndTime: Boolean = false,
    val showLocation: Boolean = false,
)

@Composable
private fun AppearanceSection(appearance: Appearance, onChange: (Appearance) -> Unit) {
    Text(stringResource(R.string.config_appearance), style = MaterialTheme.typography.titleMedium)
    Choice(
        R.string.config_theme,
        WidgetTheme.entries,
        appearance.theme,
        {
            stringResource(
                when (it) {
                    WidgetTheme.SYSTEM -> R.string.config_theme_system
                    WidgetTheme.LIGHT -> R.string.config_theme_light
                    WidgetTheme.DARK -> R.string.config_theme_dark
                }
            )
        },
    ) { onChange(appearance.copy(theme = it)) }
    Text(
        stringResource(R.string.config_background) + ": " +
            stringResource(R.string.config_background_percent, appearance.backgroundOpacity),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = appearance.backgroundOpacity.toFloat(),
        onValueChange = { onChange(appearance.copy(backgroundOpacity = (it / 5).roundToInt() * 5)) },
        valueRange = 0f..100f,
        steps = 19,
    )
    Choice(
        R.string.config_layout,
        WidgetLayout.entries,
        appearance.layout,
        { stringResource(if (it == WidgetLayout.LIST) R.string.config_layout_list else R.string.config_layout_by_day) },
    ) { onChange(appearance.copy(layout = it)) }
    Choice(
        R.string.config_density,
        WidgetDensity.entries,
        appearance.density,
        { stringResource(if (it == WidgetDensity.COMFORTABLE) R.string.config_density_comfortable else R.string.config_density_compact) },
    ) { onChange(appearance.copy(density = it)) }
    AccentChoice(stringResource(R.string.config_accent), appearance.accent) { onChange(appearance.copy(accent = it)) }
    Toggle(R.string.config_show_countdown, appearance.showCountdown) { onChange(appearance.copy(showCountdown = it)) }
    Toggle(R.string.config_show_end_time, appearance.showEndTime) { onChange(appearance.copy(showEndTime = it)) }
    Toggle(R.string.config_show_location, appearance.showLocation) { onChange(appearance.copy(showLocation = it)) }
}

@Composable
private fun Toggle(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentChoice(title: String, selected: WidgetAccent, onSelect: (WidgetAccent) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetAccent.entries.forEach { accent ->
                val swatch = (if (dark) accent.dark else accent.light)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                FilterChip(
                    selected = accent == selected,
                    onClick = { onSelect(accent) },
                    label = { Text(accentLabel(accent)) },
                    leadingIcon = { Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(swatch)) },
                )
            }
        }
    }
}

@Composable
private fun accentLabel(accent: WidgetAccent) = stringResource(
    when (accent) {
        WidgetAccent.SYSTEM -> R.string.config_theme_system
        WidgetAccent.BLUE -> R.string.config_accent_blue
        WidgetAccent.GREEN -> R.string.config_accent_green
        WidgetAccent.ORANGE -> R.string.config_accent_orange
        WidgetAccent.RED -> R.string.config_accent_red
        WidgetAccent.PURPLE -> R.string.config_accent_purple
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(title: Int, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label(option), maxLines = 1) },
                )
            }
        }
    }
}

/** Calendars grouped by account; an account checkbox selects or clears all of its calendars. */
@Composable
private fun CalendarTree(calendars: List<CalendarSource>, selected: Set<Long>, onSelectedChange: (Set<Long>) -> Unit) {
    val accounts = remember(calendars) { calendars.groupBy { it.accountName } }
    // Open accounts that already contribute calendars; with nothing selected yet, open all so the choice is visible.
    var expanded by remember(calendars) {
        mutableStateOf(accounts.filterValues { list -> list.any { it.id in selected } }.keys.ifEmpty { accounts.keys })
    }
    Column {
        accounts.forEach { (account, accountCalendars) ->
            val ids = accountCalendars.map { it.id }.toSet()
            val state = when (ids.count { it in selected }) {
                0 -> ToggleableState.Off
                ids.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            val isExpanded = account in expanded
            Row(
                modifier = Modifier.fillMaxWidth()
                    .triStateToggleable(
                        state = state,
                        role = Role.Checkbox,
                        onClick = { onSelectedChange(if (state == ToggleableState.On) selected - ids else selected + ids) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TriStateCheckbox(state = state, onClick = null)
                Text(account, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = if (isExpanded) expanded - account else expanded + account }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.config_account_collapse else R.string.config_account_expand,
                            account,
                        ),
                    )
                }
            }
            if (isExpanded) {
                accountCalendars.forEach { calendar ->
                    CalendarRow(calendar, calendar.id in selected) { checked ->
                        onSelectedChange(if (checked) selected + calendar.id else selected - calendar.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(calendar: CalendarSource, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(calendar.color)))
        Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun parsePeople(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
