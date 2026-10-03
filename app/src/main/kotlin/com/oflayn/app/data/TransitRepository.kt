package com.oflayn.app.data

import com.oflayn.core.model.Freshness
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.core.model.TransitAlert
import com.oflayn.core.model.VehicleType
import com.oflayn.core.model.Verification
import com.oflayn.domain.ai.TransitData
import com.oflayn.domain.ai.UserData
import com.oflayn.domain.transit.DatasetInfo
import com.oflayn.domain.transit.ImportCheck
import com.oflayn.domain.transit.OverpassParser
import com.oflayn.domain.transit.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface ImportResult {
    data class Success(val info: DatasetInfo) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * Room -> Repository -> UI. The network is touched only by [importFromOverpass], started by the user.
 * No official or unofficial GTFS feed for Bursa was found (see docs/SOURCE_MATRIX.md), so the dataset is
 * derived from OpenStreetMap and always labelled DERIVED / UNOFFICIAL.
 */
class TransitRepository(private val dao: TransitDao, private val settings: com.oflayn.app.Settings) : TransitData {
    private var stopCache: List<Stop>? = null
    private var routeCache: List<Route>? = null
    private var metaCache: DatasetInfo? = null
    private var metaLoaded = false

    override val sourceName: String get() = metaCache?.let { "${it.source} (${it.label})" } ?: "none"
    override val freshness: Freshness get() = metaCache?.freshness(System.currentTimeMillis()) ?: Freshness.UNAVAILABLE

    suspend fun info(): DatasetInfo? { ensureLoaded(); return metaCache }

    private suspend fun ensureLoaded() {
        if (metaLoaded) return
        withContext(Dispatchers.IO) {
            val m = dao.meta()
            metaCache = m?.let {
                DatasetInfo(it.source, SourceType.valueOf(it.sourceType), it.url, it.fetchedAtMs, Verification.valueOf(it.verification), it.stopCount, it.routeCount, it.attribution)
            }
            stopCache = dao.stops().map { Stop(it.id, it.name, it.lat, it.lon) }
            routeCache = dao.routes().map { Route(it.id, it.shortName, it.longName, VehicleType.valueOf(it.vehicleType), it.stopIds.split(",")) }
            metaLoaded = true
        }
    }

    override suspend fun stops(): List<Stop> { ensureLoaded(); return stopCache.orEmpty() }
    override suspend fun routes(): List<Route> { ensureLoaded(); return routeCache.orEmpty() }
    /** No alert source has been verified, so this is always empty (UNAVAILABLE), never invented. */
    override suspend fun alerts(): List<TransitAlert> = emptyList()

    suspend fun importFromOverpass(): ImportResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = settings.overpassUrl
            val q = OverpassParser.query(BBOX_SOUTH, BBOX_WEST, BBOX_NORTH, BBOX_EAST)
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            val body = try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 150_000
                conn.doOutput = true
                conn.setRequestProperty("User-Agent", "OfLayn/0.1 (Android transit app; user-initiated download)")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                if (conn.responseCode !in 200..299) return@withContext ImportResult.Failed("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
            val parsed = OverpassParser.parse(body)
            when (val check = OverpassParser.validate(parsed)) {
                is ImportCheck.Rejected -> return@withContext ImportResult.Failed("Dataset rejected: ${check.reason}")
                ImportCheck.Ok -> {}
            }
            val now = System.currentTimeMillis()
            val info = DatasetInfo(
                source = "OpenStreetMap via Overpass", sourceType = SourceType.DERIVED_OSM, url = endpoint, fetchedAtMs = now,
                verification = Verification.UNOFFICIAL, stopCount = parsed.stops.size, routeCount = parsed.routes.size,
                attribution = "© OpenStreetMap contributors (ODbL)",
            )
            dao.replaceNetwork(
                parsed.stops.map { StopEntity(it.id, it.name, it.lat, it.lon) },
                parsed.routes.map { RouteEntity(it.id, it.shortName, it.longName, it.vehicleType.name, it.stopIds.joinToString(",")) },
                DatasetMetaEntity("network", info.source, info.sourceType.name, info.url, info.fetchedAtMs, info.verification.name, info.stopCount, info.routeCount, info.attribution),
            )
            metaLoaded = false
            ensureLoaded()
            ImportResult.Success(info)
        } catch (e: Exception) {
            ImportResult.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
    }

    companion object {
        // Rough Bursa metropolitan bounding box (design parameter, adjustable here).
        const val BBOX_SOUTH = 40.05
        const val BBOX_WEST = 28.35
        const val BBOX_NORTH = 40.40
        const val BBOX_EAST = 29.45
        const val DEFAULT_OVERPASS = "https://overpass-api.de/api/interpreter"
    }
}

class RoomUserData(private val dao: TransitDao, private val settings: com.oflayn.app.Settings) : UserData {
    override suspend fun favoriteStopIds() = dao.favorites("stop")
    override suspend fun favoriteRouteIds() = dao.favorites("route")
    override suspend fun recentTrips() = dao.recentTrips()
    override suspend fun cardType() = com.oflayn.core.model.BursaCardType.entries.firstOrNull { it.name == settings.cardType } ?: com.oflayn.core.model.BursaCardType.UNKNOWN
    override suspend fun transactions() = emptyList<com.oflayn.core.model.Transaction>()
    override suspend fun settingsSummary() = settings.summary()
    suspend fun saveTrip(text: String) = dao.addRecentTrip(RecentTripEntity(text = text, atMs = System.currentTimeMillis()))
}
