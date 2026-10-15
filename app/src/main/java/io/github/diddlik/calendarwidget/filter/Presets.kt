package io.github.diddlik.calendarwidget.filter

/**
 * Optional starter keyword sets offered in the configuration screen. The core never depends on them.
 * Keywords stay in German because they match the event titles of the first family use case.
 */
enum class KeywordPreset(val keywords: List<String>) {
    MEDICAL(listOf("Arzt", "Praxis", "Impfung")),
}
