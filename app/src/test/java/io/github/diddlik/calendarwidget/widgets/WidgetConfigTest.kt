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
    }

    @Test
    fun `name and naming convention round-trip`() {
        val config = WidgetConfig(calendarIds = setOf(2), name = "Arzttermine", useNamingConvention = true)
        assertEquals(config, Json.decodeFromString<WidgetConfig>(Json.encodeToString(config)))
    }
}
