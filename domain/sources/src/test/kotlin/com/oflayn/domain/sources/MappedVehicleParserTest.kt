package com.oflayn.domain.sources

import kotlin.test.*

/** SYNTHETIC fixtures with invented field names — they exercise the mapping engine only and say nothing about any real API. */
class MappedVehicleParserTest {
    private val mapping = VehicleFieldMapping(
        listPointer = "/items", idPointer = "/vid", latPointer = "/pos/y", lonPointer = "/pos/x",
        linePointer = "/ln", timestampPointer = "/ts", timestampFormat = TimestampFormat.EPOCH_SECONDS,
    )

    @Test fun `maps numbers, numeric strings and timestamps`() {
        val body = """{"items":[{"vid":"A1","pos":{"y":40.19,"x":"29,06"},"ln":"31-A","ts":1790000000}]}"""
        val out = MappedVehicleParser.parse(body, mapping)
        assertEquals(1, out.vehicles.size)
        val v = out.vehicles.single()
        assertEquals(29.06, v.lon, 1e-9); assertEquals("31-A", v.line); assertNotNull(v.observedAt)
    }

    @Test fun `rejects invalid coordinates and keeps the rest`() {
        val body = """{"items":[{"vid":"A","pos":{"y":95,"x":29}},{"vid":"B","pos":{"y":0,"x":0}},{"vid":"C","pos":{"y":40,"x":29}}]}"""
        val out = MappedVehicleParser.parse(body, mapping)
        assertEquals(listOf("C"), out.vehicles.map { it.id }); assertEquals(2, out.rejected)
    }

    @Test fun `bounds filter and missing timestamp yields null observedAt`() {
        val body = """{"items":[{"vid":"A","pos":{"y":10,"x":10}},{"vid":"B","pos":{"y":40,"x":29}}]}"""
        val out = MappedVehicleParser.parse(body, mapping, GeoBounds(39.0, 41.0, 28.0, 30.0))
        assertEquals(listOf("B"), out.vehicles.map { it.id }); assertNull(out.vehicles.single().observedAt)
    }

    @Test fun `wrong shape and invalid json produce errors, not exceptions`() {
        assertTrue(MappedVehicleParser.parse("""{"items":5}""", mapping).errors.isNotEmpty())
        assertTrue(MappedVehicleParser.parse("<html>", mapping).errors.isNotEmpty())
    }
}
