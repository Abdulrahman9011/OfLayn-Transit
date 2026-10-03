package com.oflayn.domain.transit

import com.oflayn.core.model.GeoPoint
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.core.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic coordinates for unit tests only (never shipped as data). */
class NetworkPlannerTest {
    private val a = Stop("A", "A", 40.0000, 29.0000)
    private val b = Stop("B", "B", 40.0100, 29.0000)
    private val c = Stop("C", "C", 40.0200, 29.0000)
    private val d = Stop("D", "D", 40.0200, 29.0100)
    private val r1 = Route("r1", "1", null, VehicleType.BUS, listOf("A", "B", "C"))
    private val r2 = Route("r2", "2", null, VehicleType.TRAM, listOf("C", "D"))

    @Test fun haversineKnownDistance() {
        val m = Geo.distanceMeters(40.0, 29.0, 40.01, 29.0)
        assertTrue(m in 1100.0..1120.0)
    }

    @Test fun nearbyStopsSortedAndLimited() {
        val res = Geo.nearbyStops(listOf(a, b, c), 40.0001, 29.0, radiusMeters = 1500.0, limit = 2)
        assertEquals(listOf("A", "B"), res.map { it.stop.id })
    }

    @Test fun singleRoutePlan() {
        val plan = NetworkPlanner(listOf(a, b, c, d), listOf(r1, r2)).plan(GeoPoint(40.0, 29.0), GeoPoint(40.0200, 29.0))
        assertNotNull(plan)
        assertEquals(0, plan.transfers)
        assertTrue(plan.estimated && !plan.waitingKnown)
        assertEquals(1, plan.legs.count { it is Leg.Ride })
    }

    @Test fun planWithTransfer() {
        val plan = NetworkPlanner(listOf(a, b, c, d), listOf(r1, r2)).plan(GeoPoint(40.0, 29.0), GeoPoint(40.0200, 29.0100))
        assertNotNull(plan)
        assertEquals(1, plan.transfers)
        assertEquals(2, plan.legs.count { it is Leg.Ride })
    }

    @Test fun noPlanWhenEmpty() {
        assertNull(NetworkPlanner(emptyList(), emptyList()).plan(GeoPoint(40.0, 29.0), GeoPoint(40.1, 29.1)))
    }
}
