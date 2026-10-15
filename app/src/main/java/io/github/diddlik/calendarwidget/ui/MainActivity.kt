package io.github.diddlik.calendarwidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.diddlik.calendarwidget.BuildConfig
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.widgets.WidgetRefresh

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
                            Text(
                                stringResource(R.string.main_add_widget),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                        About()
                    }
                }
            }
        }
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
