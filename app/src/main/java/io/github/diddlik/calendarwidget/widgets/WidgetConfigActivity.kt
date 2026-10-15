package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository
import io.github.diddlik.calendarwidget.domain.CalendarSource
import io.github.diddlik.calendarwidget.filter.FilterDefinition
import io.github.diddlik.calendarwidget.filter.KeywordPreset
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import io.github.diddlik.calendarwidget.filter.titleKeywordsOf
import io.github.diddlik.calendarwidget.ui.AppTheme
import io.github.diddlik.calendarwidget.ui.CalendarPermissionGate
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Configures one widget instance: name, calendar sources, title keywords, title display and item count. */
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
        var keywords by remember { mutableStateOf("") }
        var useNamingConvention by remember { mutableStateOf(false) }
        var maxItems by remember { mutableFloatStateOf(WidgetConfig.DEFAULT_MAX_ITEMS.toFloat()) }

        LaunchedEffect(appWidgetId) {
            val existing = store.get(appWidgetId)
            if (existing != null) {
                name = existing.name
                selected = existing.calendarIds
                keywords = titleKeywordsOf(existing.filter.root).orEmpty().joinToString(", ")
                useNamingConvention = existing.useNamingConvention
                maxItems = existing.maxItems.toFloat()
            }
            calendars = repository.calendars()
        }

        val available = calendars
        if (available == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
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
            CalendarTree(available, selected) { selected = it }

            Text(stringResource(R.string.config_keywords), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = { Text(stringResource(R.string.config_keywords_label)) },
                supportingText = { Text(stringResource(R.string.config_keywords_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { keywords = KeywordPreset.MEDICAL.keywords.joinToString(", ") }) {
                Text(stringResource(R.string.config_preset_medical))
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

            if (selected.isEmpty()) {
                Text(stringResource(R.string.config_select_calendar), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val config = WidgetConfig(
                        // Drop calendars that no longer exist on the device.
                        calendarIds = selected intersect available.map { it.id }.toSet(),
                        filter = FilterDefinition(root = titleContainsAny(keywords.split(','))),
                        maxItems = count,
                        name = name.trim(),
                        useNamingConvention = useNamingConvention,
                    )
                    scope.launch { save(appWidgetId, config) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.config_save)) }
        }
    }

    private suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        WidgetConfigStore.get(this).put(appWidgetId, config)
        WidgetRefresh.schedule(this)
        AgendaWidget.refresh(this, GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId))
        setResult(RESULT_OK, resultIntent(appWidgetId))
        finish()
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
