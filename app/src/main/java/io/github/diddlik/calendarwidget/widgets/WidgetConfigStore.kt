package io.github.diddlik.calendarwidget.widgets

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.core.DataStoreFactory
import io.github.diddlik.calendarwidget.filter.FilterDefinition
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Configuration of one home-screen widget instance. Event content is never persisted, only references.
 * New fields need defaults so configurations stored by older versions still decode.
 */
@Serializable
data class WidgetConfig(
    val calendarIds: Set<Long>,
    val filter: FilterDefinition = FilterDefinition(root = titleContainsAny(emptyList())),
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    /** Also shows events that ended today or on the previous [pastDays] days, greyed out; 0 hides finished events. */
    val pastDays: Int = 0,
    /** Shown as the widget header; blank uses the default title. */
    val name: String = "",
    /** Display titles written as "<keywords> - <person> <title>" as person and title; see `parseConventionTitle`. */
    val useNamingConvention: Boolean = false,
    /** Notifications before matching events, in minutes, independent of the reminders stored in the calendar. */
    val reminderMinutes: Set<Int> = emptySet(),
    val reminderStyle: ReminderStyle = ReminderStyle.ALERT,
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Background opacity in percent, 0 to 100. */
    val backgroundOpacity: Int = 100,
    val layout: WidgetLayout = WidgetLayout.LIST,
    val density: WidgetDensity = WidgetDensity.COMFORTABLE,
    /** Adds the time until each event starts, such as "in 3 days". */
    val showCountdown: Boolean = false,
    val showEndTime: Boolean = false,
    val showLocation: Boolean = false,
    val accent: WidgetAccent = WidgetAccent.SYSTEM,
    /** Template for the widget's new-event button: title keyword and the people offered as choices. */
    val newEventKeyword: String = "",
    val people: List<String> = emptyList(),
    /** Name color per person, keyed by the name as entered in [people]; missing names use [accent]. */
    val personColors: Map<String, WidgetAccent> = emptyMap(),
    /** Key in [StoredConfig.profiles] whose filter this widget shares; [filter] then holds a copy of it. */
    val profileId: String? = null,
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 10
        const val MAX_ITEMS_LIMIT = 25
        const val MAX_PAST_DAYS = 30
        val REMINDER_OPTIONS = listOf(15, 30, 60, 120, 24 * 60, 2 * 24 * 60, 7 * 24 * 60)
    }

    /** Names in titles may differ in case from the entered ones. */
    fun personAccent(person: String): WidgetAccent =
        personColors.entries.firstOrNull { it.key.equals(person, ignoreCase = true) }?.value ?: accent
}

/** [ALERT] pops up with sound; [SILENT] only appears in the notification shade. Each maps to its own channel. */
@Serializable
enum class ReminderStyle { ALERT, SILENT }

@Serializable
enum class WidgetTheme { SYSTEM, LIGHT, DARK }

/** [BY_DAY] shows a header per day and only the time on each row. */
@Serializable
enum class WidgetLayout { LIST, BY_DAY }

/** [COMPACT] puts time and title on one line. */
@Serializable
enum class WidgetDensity { COMFORTABLE, COMPACT }

/**
 * Color of day headers and highlighted names, as ARGB for light and dark backgrounds; [SYSTEM] keeps the theme's
 * primary color. Both tones keep at least 4.5:1 contrast on the matching default widget background.
 */
@Serializable
enum class WidgetAccent(val light: Long?, val dark: Long?) {
    SYSTEM(null, null),
    BLUE(0xFF0D47A1, 0xFF90CAF9),
    GREEN(0xFF1B5E20, 0xFFA5D6A7),
    ORANGE(0xFFA33000, 0xFFFFCC80),
    RED(0xFFB71C1C, 0xFFFFCDD2),
    PURPLE(0xFF6A1B9A, 0xFFE1BEE7),
}

/** Versioned root of all persisted settings. Bump [version] and migrate in [ConfigSerializer] on schema changes. */
@Serializable
data class StoredConfig(
    val version: Int = 1,
    val widgets: Map<Int, WidgetConfig> = emptyMap(),
    val profiles: Map<String, FilterProfile> = emptyMap(),
    val calendarGroups: Map<String, CalendarGroup> = emptyMap(),
) {
    /**
     * Saves [config]; a linked profile takes over its filter and passes it on to every widget sharing it.
     * Copying the filter into each widget keeps rendering and reminders independent of profiles.
     */
    fun withWidget(appWidgetId: Int, config: WidgetConfig): StoredConfig {
        val id = config.profileId
        val profile = id?.let { profiles[it] }
        // The profile may have been deleted while this widget was being edited.
        if (id == null || profile == null) return copy(widgets = widgets + (appWidgetId to config.copy(profileId = null)))
        return copy(
            widgets = (widgets + (appWidgetId to config)).mapValues { (_, widget) ->
                if (widget.profileId == id) widget.copy(filter = config.filter) else widget
            },
            profiles = profiles + (id to profile.copy(filter = config.filter)),
        )
    }

    /** Widgets that used the profile keep its filter as their own. */
    fun withoutProfile(id: String) = copy(
        profiles = profiles - id,
        widgets = widgets.mapValues { (_, widget) -> if (widget.profileId == id) widget.copy(profileId = null) else widget },
    )
}

/** A named filter that several widgets can share. */
@Serializable
data class FilterProfile(val name: String, val filter: FilterDefinition)

/** A named selection of calendars, applied to a widget as a shortcut and not linked afterwards. */
@Serializable
data class CalendarGroup(val name: String, val calendarIds: Set<Long>)

private val json = Json { ignoreUnknownKeys = true }

private object ConfigSerializer : Serializer<StoredConfig> {
    override val defaultValue = StoredConfig()

    override suspend fun readFrom(input: InputStream): StoredConfig = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable widget configuration", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid widget configuration", e)
    }

    override suspend fun writeTo(t: StoredConfig, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

/** Widget configurations keyed by appWidgetId, stored in app-private storage. */
class WidgetConfigStore private constructor(private val store: DataStore<StoredConfig>) {

    suspend fun get(appWidgetId: Int): WidgetConfig? = store.data.first().widgets[appWidgetId]

    suspend fun all(): Map<Int, WidgetConfig> = store.data.first().widgets

    suspend fun stored(): StoredConfig = store.data.first()

    suspend fun put(appWidgetId: Int, config: WidgetConfig) {
        store.updateData { it.withWidget(appWidgetId, config) }
    }

    suspend fun putProfile(id: String, profile: FilterProfile) {
        store.updateData { it.copy(profiles = it.profiles + (id to profile)) }
    }

    suspend fun removeProfile(id: String) {
        store.updateData { it.withoutProfile(id) }
    }

    suspend fun putCalendarGroup(id: String, group: CalendarGroup) {
        store.updateData { it.copy(calendarGroups = it.calendarGroups + (id to group)) }
    }

    suspend fun removeCalendarGroup(id: String) {
        store.updateData { it.copy(calendarGroups = it.calendarGroups - id) }
    }

    suspend fun remove(appWidgetIds: IntArray) {
        store.updateData { it.copy(widgets = it.widgets - appWidgetIds.toSet()) }
    }

    companion object {
        @Volatile private var instance: WidgetConfigStore? = null

        // DataStore requires a single instance per file within the process.
        fun get(context: Context): WidgetConfigStore = instance ?: synchronized(this) {
            instance ?: WidgetConfigStore(
                DataStoreFactory.create(
                    serializer = ConfigSerializer,
                    // A corrupt file would otherwise break every widget; start over with defaults instead.
                    corruptionHandler = ReplaceFileCorruptionHandler { StoredConfig() },
                    produceFile = { context.applicationContext.dataStoreFile("widget_config.json") },
                )
            ).also { instance = it }
        }
    }
}
