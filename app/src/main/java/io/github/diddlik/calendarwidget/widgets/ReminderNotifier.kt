package io.github.diddlik.calendarwidget.widgets

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.calendar.CalendarSourceRepository
import io.github.diddlik.calendarwidget.domain.EventOccurrence
import io.github.diddlik.calendarwidget.domain.dueReminders
import io.github.diddlik.calendarwidget.domain.nextReminderTime
import io.github.diddlik.calendarwidget.domain.parseConventionTitle
import io.github.diddlik.calendarwidget.domain.selectAgenda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Per-widget reminder notifications. Each check posts the reminders that fell due since the previous
 * check and sets an alarm for the next one. The alarm is exact when the user allows exact alarms;
 * otherwise Android may deliver it up to 10 minutes late. The periodic widget refresh also runs a
 * check, which catches up after reboots and app updates that clear alarms.
 */
object ReminderNotifier {
    /** Channel IDs stay fixed: users may have customized sound and vibration in the system settings. */
    const val CHANNEL_ALERT = "reminders"
    private const val CHANNEL_SILENT = "reminders_silent"
    private const val PREFS = "reminders"
    private const val KEY_LAST_CHECK = "last_check"

    /** Covers the largest reminder offset (one week) plus the all-day anchor and zone shifts. */
    private val LOOKBACK: Duration = Duration.ofDays(2)
    private val LOOKAHEAD: Duration = Duration.ofDays(10)
    private val INEXACT_WINDOW: Duration = Duration.ofMinutes(10)

    private val lock = Mutex()

    suspend fun check(context: Context) = lock.withLock {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = Instant.now()
        // First run or a long gap: start from now (or at most a day back) instead of replaying old reminders.
        val after = prefs.getLong(KEY_LAST_CHECK, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
            ?.coerceIn(now.minus(Duration.ofDays(1)), now) ?: now
        val repository = CalendarSourceRepository(context)
        val zone = ZoneId.systemDefault()
        var next: Instant? = null
        val widgets = WidgetConfigStore.get(context).all().values.filter { it.reminderMinutes.isNotEmpty() }
        if (repository.hasPermission()) {
            for (config in widgets) {
                val occurrences = try {
                    repository.occurrences(config.calendarIds, now.minus(LOOKBACK), now.plus(LOOKAHEAD), zone)
                } catch (_: SecurityException) {
                    break
                }
                val events = selectAgenda(occurrences, config.filter.root, after, Int.MAX_VALUE)
                dueReminders(events, config.reminderMinutes, zone, after, now).forEach { notify(context, it.event, config, zone) }
                nextReminderTime(events, config.reminderMinutes, zone, now)?.let { if (next == null || it < next) next = it }
            }
        }
        prefs.edit { putLong(KEY_LAST_CHECK, now.toEpochMilli()) }
        scheduleAlarm(context, next)
    }

    private fun scheduleAlarm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when {
            at == null -> alarms.cancel(intent)
            canScheduleExactAlarms(context) -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
            // Plain inexact alarms may be deferred by up to an hour; a window bounds the delay.
            else -> alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), INEXACT_WINDOW.toMillis(), intent)
        }
    }

    /** Exact alarms need user consent from Android 12 on, and are denied by default for new installs on Android 14+. */
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun notify(context: Context, event: EventOccurrence, config: WidgetConfig, zone: ZoneId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val alert = config.reminderStyle == ReminderStyle.ALERT
        val channel = if (alert) CHANNEL_ALERT else CHANNEL_SILENT
        val convention = if (config.useNamingConvention) parseConventionTitle(event.title) else null
        val title = when {
            convention == null -> event.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.event_no_title)
            convention.title.isEmpty() -> convention.person
            else -> "${convention.person}: ${convention.title}"
        }
        val open = PendingIntent.getActivity(
            context, notificationId(event), openEventIntent(event),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Event titles are private; the lock screen shows only a generic text.
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(formatWhen(context, event, zone))
            .setSubText(config.name.ifBlank { null })
            .setWhen(event.begin.toEpochMilli())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(event), notification)
    }

    // One notification per occurrence: a later reminder for the same occurrence replaces the earlier one.
    private fun notificationId(event: EventOccurrence) = (event.eventId * 31 + event.begin.toEpochMilli()).hashCode()

    /** Creating an existing channel only updates its name and description; user changes to importance and sound stay. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notification_channel_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, context.getString(R.string.notification_channel_silent_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.notification_channel_silent_description) }
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotifier.check(context)
            } finally {
                pending.finish()
            }
        }
    }
}
