package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.domain.templateTitle
import io.github.diddlik.calendarwidget.ui.AppTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Prepares a new event's title and optionally its start from the widget's template, then hands creation to the calendar app. */
class NewEventActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setContent {
            AppTheme {
                var config by remember { mutableStateOf<WidgetConfig?>(null) }
                LaunchedEffect(appWidgetId) {
                    config = WidgetConfigStore.get(this@NewEventActivity).get(appWidgetId)
                    if (config == null) finish()
                }
                config?.let { TemplateDialog(it) }
            }
        }
    }

    private enum class Picker { DATE, TIME }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun TemplateDialog(config: WidgetConfig) {
        var person by remember { mutableStateOf<String?>(null) }
        var detail by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }
        var start by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)) }
        var picker by remember { mutableStateOf<Picker?>(null) }
        val title = templateTitle(config.newEventKeyword, person, detail)
        AlertDialog(
            onDismissRequest = ::finish,
            title = { Text(stringResource(R.string.widget_new_event)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (config.people.isNotEmpty()) {
                        Text(stringResource(R.string.new_event_who), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            config.people.forEach { name ->
                                FilterChip(
                                    selected = person == name,
                                    onClick = { person = if (person == name) null else name },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                    if (config.newEventPickTime) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { picker = Picker.DATE }) {
                                Text(start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                            }
                            OutlinedButton(onClick = { picker = Picker.TIME }) {
                                Text(start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text(stringResource(R.string.new_event_detail)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    if (title.isNotEmpty()) {
                        Text(stringResource(R.string.new_event_preview, title), style = MaterialTheme.typography.bodySmall)
                    }
                    if (failed) {
                        Text(
                            stringResource(R.string.new_event_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // A suggestion only: calendar apps may ignore it and let the user choose.
                    val calendarId = config.calendarIds.singleOrNull()
                    try {
                        val begin = if (config.newEventPickTime) start.atZone(ZoneId.systemDefault()).toInstant() else null
                        startActivity(newEventIntent(title.ifEmpty { null }, calendarId, begin))
                        finish()
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                }) { Text(stringResource(R.string.new_event_open)) }
            },
            dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.config_cancel)) } },
        )
        when (picker) {
            Picker.DATE -> DateDialog(start, { start = it }, { picker = null })
            Picker.TIME -> TimeDialog(start, { start = it }, { picker = null })
            null -> {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DateDialog(start: LocalDateTime, onChange: (LocalDateTime) -> Unit, onClose: () -> Unit) {
        // The Material date picker works with UTC midnight of the selected day.
        val state = rememberDatePickerState(start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(start.with(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()))
                    }
                    onClose()
                }) { Text(stringResource(R.string.new_event_pick_ok)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.config_cancel)) } },
        ) { DatePicker(state) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TimeDialog(start: LocalDateTime, onChange: (LocalDateTime) -> Unit, onClose: () -> Unit) {
        val state = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(this))
        TimePickerDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.new_event_time)) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(start.withHour(state.hour).withMinute(state.minute))
                    onClose()
                }) { Text(stringResource(R.string.new_event_pick_ok)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.config_cancel)) } },
        ) { TimePicker(state) }
    }
}
