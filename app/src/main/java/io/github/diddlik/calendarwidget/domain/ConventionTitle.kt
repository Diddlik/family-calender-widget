package io.github.diddlik.calendarwidget.domain

/**
 * Event title written with the naming convention "<keywords> - <person> <title>",
 * for example "Arzt - Anna Vorsorge": the keywords drive filtering, the person and title are displayed.
 */
data class ConventionTitle(val person: String, val title: String)

// Spaces around the dash keep hyphenated words such as "Hals-Nasen-Ohren" intact.
private val separator = Regex("""\s+[-–—]\s+""")

/** Splits [raw] by the naming convention, or returns null when it does not follow it. */
fun parseConventionTitle(raw: String?): ConventionTitle? {
    val text = raw?.trim().orEmpty()
    val match = separator.find(text) ?: return null
    val rest = text.substring(match.range.last + 1).trim()
    if (rest.isEmpty()) return null
    val parts = rest.split(Regex("""\s+"""), limit = 2)
    return ConventionTitle(person = parts[0], title = parts.getOrElse(1) { "" })
}
