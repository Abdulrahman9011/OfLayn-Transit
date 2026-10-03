package com.oflayn.domain.transit

import com.oflayn.core.model.Freshness
import com.oflayn.core.model.VehicleType
import com.oflayn.core.model.Verification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic Overpass-shaped JSON for tests only. */
class OverpassParserTest {
    private val json = """
    {"elements":[
      {"type":"relation","id":1,"tags":{"route":"bus","ref":"X1","name":"X1 test"},
       "members":[{"type":"node","ref":10,"role":"stop"},{"type":"node","ref":11,"role":"stop"},{"type":"way","ref":99,"role":""}]},
      {"type":"relation","id":2,"tags":{"route":"bus","ref":"BROKEN"},
       "members":[{"type":"node","ref":10,"role":"stop"},{"type":"node","ref":404,"role":"stop"}]},
      {"type":"relation","id":3,"tags":{"route":"subway","name":"Bursaray test"},
       "members":[{"type":"node","ref":10,"role":"platform"},{"type":"node","ref":11,"role":"platform"},{"type":"node","ref":10,"role":"stop"}]},
      {"type":"node","id":10,"lat":40.0,"lon":29.0,"tags":{"name":"A"}},
      {"type":"node","id":11,"lat":40.01,"lon":29.0},
      {"type":"node","id":12,"lat":95.0,"lon":29.0,"tags":{"name":"bad"}}
    ]}
    """.trimIndent()

    @Test fun parsesRoutesAndDropsBrokenOnes() {
        val n = OverpassParser.parse(json)
        assertEquals(2, n.routes.size)
        assertEquals(1, n.skippedRoutes)
        assertEquals(listOf("osm:node:10", "osm:node:11"), n.routes.first { it.shortName == "X1" }.stopIds)
    }

    @Test fun platformsPreferredAndTypeMapped() {
        val r = OverpassParser.parse(json).routes.first { it.id == "osm:rel:3" }
        assertEquals(VehicleType.BURSARAY, r.vehicleType)
        assertEquals(2, r.stopIds.size)
    }

    @Test fun unnamedStopStaysVisiblyUnnamedAndInvalidCoordsDropped() {
        val n = OverpassParser.parse(json)
        assertEquals("Unnamed stop", n.stops.first { it.id == "osm:node:11" }.name)
        assertTrue(n.stops.none { it.id == "osm:node:12" })
    }

    @Test fun smallDatasetIsRejected() {
        assertTrue(OverpassParser.validate(OverpassParser.parse(json)) is ImportCheck.Rejected)
    }

    @Test fun emptyAnswerIsEmptyNetwork() {
        assertEquals(0, OverpassParser.parse("""{"elements":[]}""").stops.size)
    }

    @Test fun freshnessThresholds() {
        val day = 86_400_000L
        val info = DatasetInfo("s", SourceType.DERIVED_OSM, "u", 0, Verification.UNOFFICIAL, 1, 1, "a")
        assertEquals(Freshness.RECENT, info.freshness(3 * day))
        assertEquals(Freshness.CACHED, info.freshness(30 * day))
        assertEquals(Freshness.STALE, info.freshness(90 * day))
        assertEquals("DERIVED / UNOFFICIAL", info.label)
    }

    @Test fun queryContainsBoundingBox() {
        assertTrue(OverpassParser.query(1.0, 2.0, 3.0, 4.0).contains("(1.0,2.0,3.0,4.0)"))
    }
}
