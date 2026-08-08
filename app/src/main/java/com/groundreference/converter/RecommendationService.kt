package com.groundreference.converter

/**
 * RecommendationService.kt
 *
 * Direct Kotlin port of the desktop RecommendationService.java.
 * Same logic, unchanged: compares current vs target lat/lon and reports
 * an absolute compass direction (increasing latitude = North, increasing
 * longitude = East).
 */
class RecommendationService {

    companion object {
        // Treat differences smaller than this as "no movement needed" in that axis.
        private const val EPSILON_DEGREES = 0.0001
    }

    /**
     * Calculates a movement recommendation based on the current coordinate
     * and a target coordinate.
     *
     * @param currentLatitude  latitude of the current position, in decimal degrees
     * @param currentLongitude longitude of the current position, in decimal degrees
     * @param targetLatitude   latitude of the target position, in decimal degrees
     * @param targetLongitude  longitude of the target position, in decimal degrees
     * @return a human-readable recommendation string, e.g. "Move North-East"
     */
    fun calculateRecommendation(
        currentLatitude: Double,
        currentLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): String {

        val deltaLat = targetLatitude - currentLatitude
        val deltaLon = targetLongitude - currentLongitude

        val northSouth = when {
            deltaLat > EPSILON_DEGREES -> "North"
            deltaLat < -EPSILON_DEGREES -> "South"
            else -> ""
        }

        val eastWest = when {
            deltaLon > EPSILON_DEGREES -> "East"
            deltaLon < -EPSILON_DEGREES -> "West"
            else -> ""
        }

        return when {
            northSouth.isEmpty() && eastWest.isEmpty() -> "Target Reached - No Movement Needed"
            northSouth.isEmpty() -> "Move $eastWest"
            eastWest.isEmpty() -> "Move $northSouth"
            else -> "Move $northSouth-$eastWest"
        }
    }
}
