package com.groundreference.converter

/**
 * ClipboardCoordinateParser.kt
 *
 * Parses the coordinate text that the Skydroid remote-controller app puts
 * on the system clipboard when you double-tap a rectangle drawn on its
 * map screen. Observed format:
 *
 *     Range: 10m ,Lat: 22.5726° ,Long 88.3639°
 *
 * The parser is deliberately tolerant of small formatting differences
 * (missing colon after "Long", extra/missing spaces, presence or absence
 * of the ° symbol, "Lat"/"Long" case) since we don't control Skydroid's
 * exact output and it could vary slightly between firmware versions.
 *
 * It does NOT depend on the "Range:" field at all -- it just looks for
 * "Lat" and "Long" labels anywhere in the text and grabs the number that
 * follows each one.
 */
object ClipboardCoordinateParser {

    // Matches "Lat" (any case) followed by optional ":" / spaces, then a
    // signed decimal number. Works whether or not a ° symbol follows.
    private val LAT_REGEX = Regex("""Lat\s*:?\s*([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    // Matches "Long" or "Lon" (any case) the same way.
    private val LON_REGEX = Regex("""Lon(?:g)?\s*:?\s*([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    /**
     * Result of a successful parse: latitude, longitude, and the range in
     * meters if it was present in the text (null if not found/parsable).
     */
    data class ParsedCoordinate(
        val latitude: Double,
        val longitude: Double,
        val rangeMeters: Double?
    )

    /**
     * Attempts to parse a Skydroid-style clipboard string.
     *
     * @param text the raw clipboard text
     * @return a ParsedCoordinate if both Lat and Long were found and are
     *         valid numbers within range, or null if parsing failed.
     */
    fun parse(text: String?): ParsedCoordinate? {
        if (text.isNullOrBlank()) return null

        val latMatch = LAT_REGEX.find(text) ?: return null
        val lonMatch = LON_REGEX.find(text) ?: return null

        val latitude = latMatch.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = lonMatch.groupValues[1].toDoubleOrNull() ?: return null

        if (latitude < -90 || latitude > 90) return null
        if (longitude < -180 || longitude > 180) return null

        val rangeMatch = Regex("""Range\s*:?\s*(\d+(?:\.\d+)?)\s*m""", RegexOption.IGNORE_CASE).find(text)
        val range = rangeMatch?.groupValues?.get(1)?.toDoubleOrNull()

        return ParsedCoordinate(latitude, longitude, range)
    }
}
