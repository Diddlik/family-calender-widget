package io.github.diddlik.calendarwidget.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Widget refresh within Android background limits:
 * - a calendar content-URI trigger re-renders after calendar edits or syncs,
 * - a 15-minute periodic job (the WorkManager minimum) drops finished events,
 * - time, timezone and locale broadcasts re-render immediately.
 */
object WidgetRefresh {
    private const val PERIODIC = "agenda-periodic"
    private const val CALENDAR_CHANGE = "agenda-calendar-change"
    private const val NOW = "agenda-now"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
        watchCalendarChanges(context, ExistingWorkPolicy.KEEP)
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(CALENDAR_CHANGE)
        }
    }

    internal fun watchCalendarChanges(context: Context, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    // Coalesce bursts of changes during a sync into one refresh.
                    .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                    .build()
            )
            .addTag(CALENDAR_CHANGE)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CALENDAR_CHANGE, policy, request)
    }

    class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            AgendaWidget().updateAll(applicationContext)
            // A content-URI trigger fires once; re-arm it for the next calendar change.
            if (CALENDAR_CHANGE in tags) watchCalendarChanges(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            return Result.success()
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AgendaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetConfigStore.get(context).remove(appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Receives the time, timezone and locale broadcasts declared in the manifest. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
                WidgetRefresh.refreshNow(context)
        }
    }
}
