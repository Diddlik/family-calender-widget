package io.github.diddlik.calendarwidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConventionTitleTest {

    @Test
    fun `splits keyword, person and title`() {
        assertEquals(ConventionTitle("Anna", "Vorsorge U9"), parseConventionTitle("Arzt - Anna Vorsorge U9"))
        assertEquals(ConventionTitle("Ben", "Kontrolle"), parseConventionTitle("  Zahnarzt Praxis – Ben  Kontrolle "))
        assertEquals(ConventionTitle("Clara", "Impfung"), parseConventionTitle("Impfung — Clara Impfung"))
    }

    @Test
    fun `person without title keeps an empty title`() {
        assertEquals(ConventionTitle("Anna", ""), parseConventionTitle("Arzt - Anna"))
    }

    @Test
    fun `titles outside the convention are not parsed`() {
        assertNull(parseConventionTitle("Kinderarzt Anna"))
        assertNull(parseConventionTitle("Hals-Nasen-Ohren Arzt"))
        assertNull(parseConventionTitle("Arzt - "))
        assertNull(parseConventionTitle(""))
        assertNull(parseConventionTitle(null))
    }

    @Test
    fun `only the first separator splits`() {
        assertEquals(ConventionTitle("Anna", "Befund - Besprechung"), parseConventionTitle("Arzt - Anna Befund - Besprechung"))
    }

    @Test
    fun `template titles follow the convention when a person is chosen`() {
        assertEquals("Arzt - Anna Vorsorge", templateTitle(" Arzt ", "Anna", " Vorsorge "))
        assertEquals(ConventionTitle("Anna", "Vorsorge"), parseConventionTitle(templateTitle("Arzt", "Anna", "Vorsorge")))
        assertEquals("Arzt - Anna", templateTitle("Arzt", "Anna", ""))
        assertEquals("Arzt Kontrolle", templateTitle("Arzt", null, "Kontrolle"))
        assertEquals("Arzt", templateTitle("Arzt", " ", ""))
        assertEquals("Anna Vorsorge", templateTitle("", "Anna", "Vorsorge"))
        assertEquals("", templateTitle("", null, ""))
    }
}
