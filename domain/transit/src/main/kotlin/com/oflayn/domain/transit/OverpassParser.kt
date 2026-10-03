package com.oflayn.domain.transit

import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.core.model.VehicleType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ParsedNetwork(val stops: List<Stop>, val routes: List<Route>, val skippedRoutes: Int)

sealed interface ImportCheck {
    data object Ok : ImportCheck
    data class Rejected(val reason: String) : ImportCheck
}

/**
 * Turns an OpenStreetMap Overpass JSON answer into stops and routes (DERIVED / UNOFFICIAL data).
 * Nothing is invented: unresolved members drop the route, unnamed stops stay visibly unnamed.
 */
object OverpassParser {
    private val stopRoles = setOf("stop", "stop_entry_only", "stop_exit_only")
    private val platformRoles = setOf("platform", "platform_entry_only", "platform_exit_only")
    const val ROUTE_REGEX = "^(bus|trolleybus|minibus|tram|subway|light_rail|train)$"

    /** [south],[west],[north],[east] bounding box. */
    fun query(south: Double, west: Double, north: Double, east: Double): String =
        "[out:json][timeout:120];\n" +
            "relation[\"type\"=\"route\"][\"route\"~\"$ROUTE_REGEX\"]($south,$west,$north,$east)->.r;\n" +
            ".r out body;\nnode(r.r);\nout body;"

    fun parse(json: String): ParsedNetwork {
        val root = Json.parseToJsonElement(json).jsonObject
        val elements: JsonArray = root["elements"]?.jsonArray ?: return ParsedNetwork(emptyList(), emptyList(), 0)
        val nodes = HashMap<Long, Stop>()
        val relations = ArrayList<JsonObject>()
        for (el in elements) {
            val o = el.jsonObject
            when (o["type"]?.jsonPrimitive?.contentOrNull) {
                "node" -> {
                    val id = o["id"]?.jsonPrimitive?.longOrNull ?: continue
                    val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
                    val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                    val tags = o["tags"]?.jsonObject
                    val name = tags?.get("name")?.jsonPrimitive?.contentOrNull ?: tags?.get("ref")?.jsonPrimitive?.contentOrNull ?: "Unnamed stop"
                    nodes[id] = Stop("osm:node:$id", name, lat, lon)
                }
                "relation" -> relations += o
            }
        }
        val routes = ArrayList<Route>()
        val used = HashSet<String>()
        var skipped = 0
        for (r in relations) {
            val id = r["id"]?.jsonPrimitive?.longOrNull ?: continue
            val tags = r["tags"]?.jsonObject ?: JsonObject(emptyMap())
            val members = r["members"]?.jsonArray ?: JsonArray(emptyList())
            data class M(val ref: Long, val role: String)
            val ms = members.mapNotNull {
                val m = it.jsonObject
                if (m["type"]?.jsonPrimitive?.contentOrNull != "node") null
                else M(m["ref"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null, m["role"]?.jsonPrimitive?.contentOrNull ?: "")
            }
            val platforms = ms.filter { it.role in platformRoles }
            val chosen = if (platforms.isNotEmpty()) platforms else ms.filter { it.role in stopRoles }
            val ids = ArrayList<String>()
            var broken = false
            for (m in chosen) {
                val s = nodes[m.ref]
                if (s == null) { broken = true; break }
                if (ids.lastOrNull() != s.id) ids += s.id
            }
            if (broken || ids.size < 2) { skipped++; continue }
            val name = tags["name"]?.jsonPrimitive?.contentOrNull
            val ref = tags["ref"]?.jsonPrimitive?.contentOrNull
            routes += Route("osm:rel:$id", ref ?: name ?: "?", name, vehicleType(tags["route"]?.jsonPrimitive?.contentOrNull, name), ids)
            used += ids
        }
        val stops = nodes.values.filter { it.id in used }.sortedBy { it.id }
        return ParsedNetwork(stops, routes, skipped)
    }

    private fun vehicleType(route: String?, name: String?): VehicleType = when (route) {
        "bus", "trolleybus" -> VehicleType.BUS
        "minibus" -> VehicleType.MINIBUS
        "tram" -> VehicleType.TRAM
        "subway", "light_rail" -> if (name?.contains("bursaray", ignoreCase = true) == true) VehicleType.BURSARAY else VehicleType.OTHER
        else -> VehicleType.OTHER
    }

    /** Minimum size is a sanity floor (design parameter) to reject truncated or empty downloads. */
    fun validate(n: ParsedNetwork, minStops: Int = 20, minRoutes: Int = 3): ImportCheck = when {
        n.stops.size < minStops -> ImportCheck.Rejected("only ${n.stops.size} stops (min $minStops)")
        n.routes.size < minRoutes -> ImportCheck.Rejected("only ${n.routes.size} routes (min $minRoutes)")
        else -> ImportCheck.Ok
    }
}
