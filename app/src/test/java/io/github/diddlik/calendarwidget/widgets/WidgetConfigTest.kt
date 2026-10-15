package io.github.diddlik.calendarwidget.widgets

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `name, naming convention, reminders and appearance round-trip`() {
        val config = WidgetConfig(calendarIds = setOf(2), name = "Arzttermine", useNamingConvention = true, reminderMinutes = setOf(60, 1440),
            theme = WidgetTheme.DARK, backgroundOpacity = 50, layout = WidgetLayout.BY_DAY, density = WidgetDensity.COMPACT,
        )
        assertEquals(config, Json.decodeFromString<WidgetConfig>(Json.encodeToString(config)))
    }
}
