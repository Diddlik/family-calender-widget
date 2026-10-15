package io.github.diddlik.calendarwidget.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Shows [content] only while READ_CALENDAR is granted. Otherwise explains why access is needed and offers
 * the system prompt, or the app settings once the user has denied it permanently.
 */
@Composable
fun CalendarPermissionGate(onGranted: () -> Unit = {}, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CalendarSourceRepository(context) }
    var granted by remember { mutableStateOf(repository.hasPermission()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        if (result) onGranted()
        else permanentlyDenied = !(context as Activity).shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
    }
    // The user may grant or revoke access in system settings while the screen is in the background.
    LifecycleResumeEffect(Unit) {
        val now = repository.hasPermission()
        if (now && !granted) onGranted()
        granted = now
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.permission_rationale), style = MaterialTheme.typography.bodyLarge)
        if (permanentlyDenied) {
            Text(stringResource(R.string.permission_denied), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text(stringResource(R.string.permission_open_settings)) }
        } else {
            Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}
