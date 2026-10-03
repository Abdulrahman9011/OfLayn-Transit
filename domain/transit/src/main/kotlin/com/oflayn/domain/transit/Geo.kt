package com.oflayn.domain.transit

import com.oflayn.core.model.Stop
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class StopDistance(val stop: Stop, val meters: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val p1 = Math.toRadians(aLat)
        val p2 = Math.toRadians(bLat)
        val dp = p2 - p1
        val dl = Math.toRadians(bLon - aLon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun nearbyStops(stops: List<Stop>, lat: Double, lon: Double, radiusMeters: Double = 500.0, limit: Int = 10): List<StopDistance> =
        stops.asSequence()
            .map { StopDistance(it, distanceMeters(lat, lon, it.lat, it.lon)) }
            .filter { it.meters <= radiusMeters }
            .sortedBy { it.meters }
            .take(limit)
            .toList()
}
