package io.github.diddlik.calendarwidget.widgets

import kotlinx.serialization.json.Json
import io.github.diddlik.calendarwidget.filter.FilterDefinition
import io.github.diddlik.calendarwidget.filter.titleContainsAny
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {

    @Test
    fun `configuration stored before name and naming convention still decodes`() {
        val stored = """{"calendarIds":[1,3],"maxItems":5}"""
        val config = Json.decodeFromString<WidgetConfig>(stored)
        assertEquals(setOf(1L, 3L), config.calendarIds)
        assertEquals(5, config.maxItems)
        assertEquals("", config.name)
        assertEquals(false, config.useNamingConvention)
        assertEquals(emptySet<Int>(), config.reminderMinutes)
        assertEquals(WidgetTheme.SYSTEM, config.theme)
        assertEquals(100, config.backgroundOpacity)
        assertEquals(WidgetLayout.LIST, config.layout)
        assertEquals(WidgetDensity.COMFORTABLE, config.density)
        assertEquals(WidgetAccent.SYSTEM, config.accent)
    }

    @Test
    fun `name, naming convention, reminders and appearance round-trip`() {
        val config = WidgetConfig(calendarIds = setOf(2), name = "Arzttermine", useNamingConvention = true, reminderMinutes = setOf(60, 1440),
            theme = WidgetTheme.DARK, backgroundOpacity = 50, layout = WidgetLayout.BY_DAY, density = WidgetDensity.COMPACT,
            accent = WidgetAccent.GREEN,
        )
        assertEquals(config, Json.decodeFromString<WidgetConfig>(Json.encodeToString(config)))
    }

    private val arzt = FilterDefinition(root = titleContainsAny(listOf("Arzt")))
    private val praxis = FilterDefinition(root = titleContainsAny(listOf("Praxis")))

    @Test
    fun `saving a linked widget updates the profile and every widget sharing it`() {
        val stored = StoredConfig(
            widgets = mapOf(
                1 to WidgetConfig(setOf(1), filter = arzt, profileId = "p"),
                2 to WidgetConfig(setOf(2), filter = arzt, profileId = "p"),
                3 to WidgetConfig(setOf(3), filter = arzt),
            ),
            profiles = mapOf("p" to FilterProfile("Ärzte", arzt)),
        )
        val saved = stored.withWidget(1, WidgetConfig(setOf(1), filter = praxis, profileId = "p"))
        assertEquals(FilterProfile("Ärzte", praxis), saved.profiles["p"])
        assertEquals(praxis, saved.widgets.getValue(2).filter)
        assertEquals(arzt, saved.widgets.getValue(3).filter)
    }

    @Test
    fun `a link to a deleted profile is dropped on save`() {
        val saved = StoredConfig().withWidget(1, WidgetConfig(setOf(1), filter = praxis, profileId = "gone"))
        assertEquals(WidgetConfig(setOf(1), filter = praxis), saved.widgets.getValue(1))
        assertTrue(saved.profiles.isEmpty())
    }

    @Test
    fun `deleting a profile keeps its filter in the widgets that used it`() {
        val stored = StoredConfig(
            widgets = mapOf(1 to WidgetConfig(setOf(1), filter = arzt, profileId = "p")),
            profiles = mapOf("p" to FilterProfile("Ärzte", arzt)),
        )
        val removed = stored.withoutProfile("p")
        assertEquals(WidgetConfig(setOf(1), filter = arzt), removed.widgets.getValue(1))
        assertTrue(removed.profiles.isEmpty())
    }

    @Test
    fun `stored configuration without profiles and groups still decodes`() {
        val stored = Json.decodeFromString<StoredConfig>("""{"version":1,"widgets":{"4":{"calendarIds":[1]}}}""")
        assertEquals(setOf(1L), stored.widgets.getValue(4).calendarIds)
        assertTrue(stored.profiles.isEmpty())
        assertTrue(stored.calendarGroups.isEmpty())
    }
}
