package com.groundreference.converter

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * GroundReferenceService.kt
 *
 * Direct Kotlin port of the desktop (Swing) GroundReferenceService.java.
 * Same WGS84 / UTM / MGRS formulas, verified earlier against known
 * reference points (e.g. Eiffel Tower -> 31U DQ ...). No logic changed
 * from the desktop version -- only the language.
 *
 * Converts a latitude/longitude pair into a Ground Reference string in the
 * format:  ZONE+BAND  GRID-SQUARE  EASTING  NORTHING
 * e.g.     43R         GM           15980    67204
 */
class GroundReferenceService {

    companion object {
        // WGS84 ellipsoid constants
        private const val A = 6378137.0                 // semi-major axis (meters)
        private const val F = 1.0 / 298.257223563        // flattening
        private const val E2 = F * (2 - F)                // first eccentricity squared
        private const val K0 = 0.9996                    // UTM scale factor

        // MGRS lookup strings (skip I and O, per the MGRS spec)
        private const val LAT_BANDS = "CDEFGHJKLMNPQRSTUVWXX"
        private const val ROW_LETTERS_ODD_ZONE = "ABCDEFGHJKLMNPQRSTUV"
        private const val ROW_LETTERS_EVEN_ZONE = "FGHJKLMNPQRSTUVABCDE"
    }

    /**
     * Converts a latitude/longitude pair into a ground reference string.
     *
     * @param latitude  latitude in decimal degrees, range [-90, 90]
     * @param longitude longitude in decimal degrees, range [-180, 180]
     * @return a ground reference string, e.g. "43R GM 15980 67204"
     */
    fun convertLatLonToGroundReference(latitude: Double, longitude: Double): String {

        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        // ---- 1. UTM zone number and central meridian ----
        val zoneNumber = floor((longitude + 180) / 6).toInt() + 1
        val lonOriginDeg = (zoneNumber - 1) * 6 - 180 + 3
        val lonOriginRad = Math.toRadians(lonOriginDeg.toDouble())

        // ---- 2. Lat/Lon -> UTM easting/northing (Snyder transverse mercator series) ----
        val ePrime2 = E2 / (1 - E2)
        val sinLat = sin(latRad)
        val cosLat = Math.cos(latRad)
        val tanLat = tan(latRad)

        val n = A / sqrt(1 - E2 * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = ePrime2 * cosLat * cosLat
        val aTerm = cosLat * (lonRad - lonOriginRad)

        val m = A * (
                (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2.pow(3) / 256) * latRad
                        - (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2.pow(3) / 1024) * sin(2 * latRad)
                        + (15 * E2 * E2 / 256 + 45 * E2.pow(3) / 1024) * sin(4 * latRad)
                        - (35 * E2.pow(3) / 3072) * sin(6 * latRad)
                )

        var easting = K0 * n * (
                aTerm
                        + (1 - t + c) * aTerm.pow(3) / 6
                        + (5 - 18 * t + t * t + 72 * c - 58 * ePrime2) * aTerm.pow(5) / 120
                ) + 500000.0

        var northing = K0 * (
                m + n * tanLat * (
                        aTerm.pow(2) / 2
                                + (5 - t + 9 * c + 4 * c * c) * aTerm.pow(4) / 24
                                + (61 - 58 * t + t * t + 600 * c - 330 * ePrime2) * aTerm.pow(6) / 720
                        )
                )

        if (latitude < 0) {
            northing += 10000000.0 // southern hemisphere false northing
        }

        // ---- 3. Latitude band letter (C..X, skipping I and O) ----
        var bandIndex = floor((latitude + 80) / 8).toInt()
        bandIndex = max(0, min(LAT_BANDS.length - 1, bandIndex))
        val bandLetter = LAT_BANDS[bandIndex]

        // ---- 4. 100km grid-square letters ----
        val colLetters = when (zoneNumber % 3) {
            1 -> "ABCDEFGH"
            2 -> "JKLMNPQR"
            else -> "STUVWXYZ"
        }
        val rowLetters = if (zoneNumber % 2 == 1) ROW_LETTERS_ODD_ZONE else ROW_LETTERS_EVEN_ZONE

        var colIndex = (easting / 100000).toInt() - 1
        colIndex = ((colIndex % 8) + 8) % 8
        val colChar = colLetters[colIndex]

        var rowIndex = (northing / 100000).toInt()
        rowIndex = ((rowIndex % 20) + 20) % 20
        val rowChar = rowLetters[rowIndex]

        // ---- 5. Easting/Northing within the 100km square (5-digit, meter precision) ----
        val eastingWithinSquare = Math.round(easting % 100000).toInt()
        val northingWithinSquare = Math.round(northing % 100000).toInt()

        return "%d%c %c%c %05d %05d".format(
            zoneNumber, bandLetter, colChar, rowChar, eastingWithinSquare, northingWithinSquare
        )
    }
}
