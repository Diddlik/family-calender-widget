package io.github.diddlik.calendarwidget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.diddlik.calendarwidget.BuildConfig
import io.github.diddlik.calendarwidget.R
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.diddlik.calendarwidget.widgets.AgendaWidgetReceiver
import io.github.diddlik.calendarwidget.widgets.WidgetConfig
import io.github.diddlik.calendarwidget.widgets.WidgetConfigActivity
import io.github.diddlik.calendarwidget.widgets.WidgetConfigStore
import io.github.diddlik.calendarwidget.widgets.WidgetRefresh
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
                    Column(
                        modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // Widgets showing the "permission missing" state need a re-render after the grant.
                        CalendarPermissionGate(onGranted = { WidgetRefresh.refreshNow(this@MainActivity) }) {
                            WidgetList()
                        }
                        About()
                    }
                }
            }
        }
    }
}

/** Lists the placed widgets so each can be reconfigured from the app, not only from the home screen. */
@Composable
private fun WidgetList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var widgets by remember { mutableStateOf<List<Pair<Int, WidgetConfig?>>>(emptyList()) }
    // Names change in the configuration screen and widgets come and go on the home screen; reload on every return.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, AgendaWidgetReceiver::class.java))
            val configs = WidgetConfigStore.get(context).all()
            widgets = ids.map { it to configs[it] }
            // A force stop cancels refresh jobs and reminder alarms; opening the app re-arms them.
            if (ids.isNotEmpty()) WidgetRefresh.schedule(context)
        }
        onPauseOrDispose { job.cancel() }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (widgets.isNotEmpty()) {
            Text(stringResource(R.string.main_widgets_title), style = MaterialTheme.typography.titleMedium)
            widgets.forEach { (appWidgetId, config) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            config?.name?.ifBlank { null } ?: stringResource(R.string.widget_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (config == null) stringResource(R.string.main_widget_not_configured)
                            else pluralStringResource(R.plurals.main_widget_calendars, config.calendarIds.size, config.calendarIds.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(context, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                    }) { Text(stringResource(R.string.main_widget_edit)) }
                }
            }
        }
        Text(stringResource(R.string.main_add_widget), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun About() {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall)
    }
}
