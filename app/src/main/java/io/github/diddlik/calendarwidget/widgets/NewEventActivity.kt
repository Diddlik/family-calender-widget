package io.github.diddlik.calendarwidget.widgets

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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

/** Prepares a new event's title from the widget's template, then hands creation to the calendar app. */
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

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun TemplateDialog(config: WidgetConfig) {
        var person by remember { mutableStateOf<String?>(null) }
        var detail by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }
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
                        startActivity(newEventIntent(title.ifEmpty { null }, calendarId))
                        finish()
                    } catch (_: ActivityNotFoundException) {
                        failed = true
                    }
                }) { Text(stringResource(R.string.new_event_open)) }
            },
            dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.config_cancel)) } },
        )
    }
}
