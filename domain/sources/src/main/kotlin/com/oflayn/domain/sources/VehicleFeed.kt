package com.oflayn.domain.sources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class VehiclePosition(
    val id: String,
    val lat: Double,
    val lon: Double,
    val line: String?,
    val bearing: Double?,
    /** null => origin did not say when; the UI must NOT show LIVE. */
    val observedAt: Instant?,
)

@Serializable
enum class TimestampFormat { NONE, ISO_INSTANT, EPOCH_SECONDS, EPOCH_MILLIS, ISTANBUL_LOCAL_ISO }

/**
 * Configuration-driven mapping from a source's JSON to [VehiclePosition]. No field name is hard-coded:
 * after inspecting a REAL response (tools/probe_ulasimapi.sh) the operator fills this mapping in
 * server configuration. Pointers are RFC 6901 JSON Pointers (e.g. "/data/0/lat").
 */
@Serializable
data class VehicleFieldMapping(
    val listPointer: String? = null,
    val idPointer: String,
    val latPointer: String,
    val lonPointer: String,
    val linePointer: String? = null,
    val bearingPointer: String? = null,
    val timestampPointer: String? = null,
    val timestampFormat: TimestampFormat = TimestampFormat.NONE,
)

data class GeoBounds(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
    fun contains(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon
}

data class ParseOutcome(val vehicles: List<VehiclePosition>, val rejected: Int, val errors: List<String>)

object MappedVehicleParser {
    private val istanbul = ZoneId.of("Europe/Istanbul")

    fun parse(body: String, mapping: VehicleFieldMapping, bounds: GeoBounds? = null): ParseOutcome {
        val root = try { Json.parseToJsonElement(body) } catch (e: Exception) {
            return ParseOutcome(emptyList(), 0, listOf("Body is not valid JSON"))
        }
        val listEl = mapping.listPointer?.let { root.at(it) } ?: root
        val items = (listEl as? JsonArray) ?: return ParseOutcome(emptyList(), 0, listOf("Pointer ${mapping.listPointer ?: "(root)"} is not an array"))
        val out = ArrayList<VehiclePosition>(items.size)
        var rejected = 0
        for (item in items) {
            val id = item.at(mapping.idPointer).asText()
            val lat = item.at(mapping.latPointer).asDouble()
            val lon = item.at(mapping.lonPointer).asDouble()
            val valid = id != null && lat != null && lon != null &&
                lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0) &&
                (bounds == null || bounds.contains(lat, lon))
            if (!valid) { rejected++; continue }
            out += VehiclePosition(
                id = id!!, lat = lat!!, lon = lon!!,
                line = mapping.linePointer?.let { item.at(it).asText() },
                bearing = mapping.bearingPointer?.let { item.at(it).asDouble() },
                observedAt = mapping.timestampPointer?.let { parseTime(item.at(it), mapping.timestampFormat) },
            )
        }
        return ParseOutcome(out, rejected, emptyList())
    }

    private fun parseTime(el: JsonElement?, f: TimestampFormat): Instant? {
        val text = el.asText() ?: return null
        return try {
            when (f) {
                TimestampFormat.NONE -> null
                TimestampFormat.ISO_INSTANT -> Instant.parse(text)
                TimestampFormat.EPOCH_SECONDS -> Instant.ofEpochSecond(text.toLong())
                TimestampFormat.EPOCH_MILLIS -> Instant.ofEpochMilli(text.toLong())
                TimestampFormat.ISTANBUL_LOCAL_ISO -> LocalDateTime.parse(text).atZone(istanbul).toInstant()
            }
        } catch (e: Exception) { null }
    }

    internal fun JsonElement?.at(pointer: String): JsonElement? {
        if (this == null) return null
        if (pointer.isEmpty() || pointer == "/") return this
        var cur: JsonElement = this
        for (raw in pointer.removePrefix("/").split("/")) {
            val seg = raw.replace("~1", "/").replace("~0", "~")
            cur = when (cur) {
                is JsonObject -> cur[seg] ?: return null
                is JsonArray -> seg.toIntOrNull()?.let { cur.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return cur
    }

    internal fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

    /** Accepts JSON numbers and numeric strings; a lone comma is read as a decimal separator. */
    internal fun JsonElement?.asDouble(): Double? {
        val t = asText() ?: return null
        return (if (',' in t && '.' !in t) t.replace(',', '.') else t).toDoubleOrNull()
    }
}
