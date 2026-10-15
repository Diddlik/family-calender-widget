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
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 10
        const val MAX_ITEMS_LIMIT = 25
    }
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
