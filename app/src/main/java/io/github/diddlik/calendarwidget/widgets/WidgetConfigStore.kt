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
    /** Shown as the widget header; blank uses the default title. */
    val name: String = "",
    /** Display titles written as "<keywords> - <person> <title>" as person and title; see `parseConventionTitle`. */
    val useNamingConvention: Boolean = false,
    /** Notifications before matching events, in minutes, independent of the reminders stored in the calendar. */
    val reminderMinutes: Set<Int> = emptySet(),
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Background opacity in percent, one of [BACKGROUND_OPACITY_OPTIONS]. */
    val backgroundOpacity: Int = 100,
    val layout: WidgetLayout = WidgetLayout.LIST,
    val density: WidgetDensity = WidgetDensity.COMFORTABLE,
    val accent: WidgetAccent = WidgetAccent.SYSTEM,
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 10
        const val MAX_ITEMS_LIMIT = 25
        val REMINDER_OPTIONS = listOf(15, 30, 60, 120, 24 * 60, 2 * 24 * 60, 7 * 24 * 60)
        val BACKGROUND_OPACITY_OPTIONS = listOf(100, 75, 50, 25)
    }
}

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
)

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

    suspend fun put(appWidgetId: Int, config: WidgetConfig) {
        store.updateData { it.copy(widgets = it.widgets + (appWidgetId to config)) }
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
