package com.oflayn.domain.ai

import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Freshness
import com.oflayn.core.model.GeoPoint
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.core.model.Transaction
import com.oflayn.core.model.TransitAlert
import com.oflayn.domain.fare.FareEngine
import com.oflayn.domain.fare.FareManifest
import com.oflayn.domain.fare.FareResult
import com.oflayn.domain.fare.RideLimit
import com.oflayn.domain.fare.UnknownReason
import com.oflayn.domain.sources.VehiclePosition
import com.oflayn.domain.transit.Geo
import com.oflayn.domain.transit.Leg
import com.oflayn.domain.transit.NetworkPlanner

/** Data ports. The app module supplies real implementations; unit tests supply synthetic ones. */
interface TransitData {
    val sourceName: String
    val freshness: Freshness
    suspend fun stops(): List<Stop>
    suspend fun routes(): List<Route>
    suspend fun alerts(): List<TransitAlert>
}

interface LocationSource { suspend fun current(): GeoPoint? }

interface VehicleLookup { suspend fun find(vehicleId: String): Pair<VehiclePosition, Freshness>? }

interface UserData {
    suspend fun favoriteStopIds(): List<String>
    suspend fun favoriteRouteIds(): List<String>
    suspend fun recentTrips(): List<String>
    /** Card type chosen by the user or confirmed by an official source; UNKNOWN otherwise. Never inferred from UID. */
    suspend fun cardType(): BursaCardType
    suspend fun transactions(): List<Transaction>
    suspend fun settingsSummary(): String
}

fun norm(s: String): String = buildString {
    for (ch in s.lowercase()) append(
        when (ch) {
            'ç' -> 'c'; 'ğ' -> 'g'; 'ı', 'İ' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
            '\u0623', '\u0625', '\u0622' -> '\u0627' // أ إ آ -> ا
            '\u0649' -> '\u064A' // ى -> ي
            '\u0629' -> '\u0647' // ة -> ه
            else -> ch
        }
    )
}.replace("i\u0307", "i")

class SimpleTool(
    override val name: String,
    override val description: String,
    override val inputSchema: String,
    private val required: List<String> = emptyList(),
    private val block: suspend (Map<String, String>) -> ToolResult,
) : AiTool {
    override fun validate(args: Map<String, String>): String? =
        required.firstOrNull { args[it].isNullOrBlank() }?.let { "Missing argument: $it" }
    override suspend fun execute(args: Map<String, String>) = block(args)
}

object TransitTools {
    fun build(
        transit: TransitData,
        location: LocationSource,
        user: UserData,
        fareManifest: FareManifest?,
        vehicles: VehicleLookup?,
    ): List<AiTool> {
        val engine = fareManifest?.let { FareEngine(it) }
        fun ok(data: String, source: String = transit.sourceName, f: Freshness = transit.freshness) = ToolResult(true, data, source, f)
        fun noData(what: String) = ToolResult(true, "NO_DATA: $what", "none", Freshness.UNAVAILABLE)
        fun stopLine(s: Stop) = "${s.id}|${s.name}|${s.lat},${s.lon}"
        suspend fun resolveStop(q: String): Stop? {
            val all = transit.stops()
            all.firstOrNull { it.id == q }?.let { return it }
            val n = norm(q)
            return all.firstOrNull { norm(it.name) == n } ?: all.firstOrNull { norm(it.name).contains(n) }
        }
        suspend fun point(arg: String): GeoPoint? {
            if (arg == "current") return location.current()
            return resolveStop(arg)?.let { GeoPoint(it.lat, it.lon) }
        }

        return listOf(
            SimpleTool("getCurrentLocation", "Current device location", "{}") {
                val p = location.current()
                if (p == null) ToolResult(true, "NO_DATA: location unavailable or permission denied", "device", Freshness.UNAVAILABLE)
                else ToolResult(true, "${p.lat},${p.lon}", "device-gps", Freshness.LIVE)
            },
            SimpleTool("findNearbyStops", "Stops near the current location", "{radiusMeters?: int}") { a ->
                val p = location.current() ?: return@SimpleTool noData("location unavailable")
                val stops = transit.stops()
                if (stops.isEmpty()) return@SimpleTool noData("no stop data installed")
                val r = a["radiusMeters"]?.toDoubleOrNull() ?: 600.0
                val res = Geo.nearbyStops(stops, p.lat, p.lon, r, 8)
                if (res.isEmpty()) noData("no stops within ${r.toInt()} m")
                else ok(res.joinToString("\n") { "${stopLine(it.stop)}|${it.meters.toInt()}m" })
            },
            SimpleTool("searchStops", "Search stops by name", "{query: string}", listOf("query")) { a ->
                val n = norm(a.getValue("query"))
                val res = transit.stops().filter { norm(it.name).contains(n) }.take(10)
                if (res.isEmpty()) noData("no stop matches") else ok(res.joinToString("\n") { stopLine(it) })
            },
            SimpleTool("getStopDetails", "Stop details and serving routes", "{stopId: string}", listOf("stopId")) { a ->
                val s = resolveStop(a.getValue("stopId")) ?: return@SimpleTool noData("stop not found")
                val serving = transit.routes().filter { s.id in it.stopIds }.joinToString(",") { it.shortName }
                ok("${stopLine(s)}\nroutes: ${serving.ifBlank { "none known" }}")
            },
            SimpleTool("searchRoutes", "Search routes by number or name", "{query: string}", listOf("query")) { a ->
                val n = norm(a.getValue("query"))
                val res = transit.routes().filter { norm(it.shortName) == n || norm(it.shortName + " " + (it.longName ?: "")).contains(n) }.take(10)
                if (res.isEmpty()) noData("no route matches") else ok(res.joinToString("\n") { "${it.id}|${it.shortName}|${it.longName ?: ""}|${it.vehicleType}" })
            },
            SimpleTool("getRouteDetails", "Route details with ordered stops", "{routeId: string}", listOf("routeId")) { a ->
                val r = transit.routes().firstOrNull { it.id == a["routeId"] || it.shortName == a["routeId"] } ?: return@SimpleTool noData("route not found")
                val names = transit.stops().associateBy { it.id }
                ok("${r.shortName} ${r.longName ?: ""} ${r.vehicleType}\n" + r.stopIds.joinToString(" > ") { names[it]?.name ?: it })
            },
            SimpleTool("planTrip", "Plan a trip; origin/destination are stop names or 'current'", "{origin: string, destination: string}", listOf("origin", "destination")) { a ->
                val o = point(a.getValue("origin")) ?: return@SimpleTool noData("origin not resolved")
                val d = point(a.getValue("destination")) ?: return@SimpleTool noData("destination not resolved")
                val plan = NetworkPlanner(transit.stops(), transit.routes()).plan(o, d) ?: return@SimpleTool noData("no route found in installed data")
                val text = buildString {
                    plan.legs.forEach {
                        when (it) {
                            is Leg.Walk -> append("WALK ${it.meters.toInt()}m ${it.fromName}->${it.toName}\n")
                            is Leg.Ride -> append("RIDE ${it.route.shortName} ${it.fromStop.name}->${it.toStop.name} (${it.stopCount} stops)\n")
                        }
                    }
                    append("transfers=${plan.transfers} walk=${plan.walkMeters.toInt()}m ESTIMATED moving=${plan.estimatedMovingSeconds / 60}min waiting=UNKNOWN")
                }
                ToolResult(true, text, "local-planner", Freshness.CACHED)
            },
            SimpleTool("calculateFare", "Fare from the signed manifest", "{cardType: BursaCardType, tariffNo: int}", listOf("tariffNo")) { a ->
                val eng = engine ?: return@SimpleTool ToolResult(true, "NO_DATA: fare manifest unavailable", "none", Freshness.UNAVAILABLE)
                val card = a["cardType"]?.let { c -> BursaCardType.entries.firstOrNull { it.name == c } } ?: user.cardType()
                val tariff = a.getValue("tariffNo").toIntOrNull() ?: return@SimpleTool ToolResult.failure("tariffNo must be an integer")
                val text = when (val r = eng.quote(card, tariff)) {
                    is FareResult.Paid -> "PAID ${r.amount.toPlainString()} TRY (card=${card.name}, tariff=${r.tariffNo}, rule=${r.provenance.ruleStatus}, manifest=${r.provenance.manifestId}, verification=${r.provenance.manifestVerification})"
                    is FareResult.Free -> "FREE limit=" + when (val l = r.limit) { RideLimit.Unlimited -> "unlimited"; is RideLimit.PerDay -> "${l.rides}/day"; RideLimit.Unspecified -> "not stated by sources" }
                    is FareResult.Subscription -> "SUBSCRIPTION deducts ${r.ridesDeducted} ride(s)"
                    is FareResult.Unknown -> "UNKNOWN reason=${r.reason}" + if (r.reason == UnknownReason.CARD_TYPE_UNKNOWN) " (ask the user to choose the card type)" else ""
                }
                ToolResult(true, text, "fare-manifest:${eng.manifestId}", Freshness.CACHED)
            },
            SimpleTool("getBursaKartStatus", "Card type and balance availability", "{}") {
                val t = user.cardType()
                ToolResult(true, "cardType=${t.name}; balance=UNAVAILABLE (no official balance API integrated)", "local", Freshness.CACHED)
            },
            SimpleTool("getBursaKartTransactions", "Locally stored transactions", "{}") {
                val tx = user.transactions().take(10)
                if (tx.isEmpty()) noData("no transactions stored") else ToolResult(true, tx.joinToString("\n") { "${it.dateTime}|${it.type}|${it.amount.toPlainString()}|verified=${it.verified}|${it.source}" }, "local-db", Freshness.CACHED)
            },
            SimpleTool("getServiceAlerts", "Active service alerts", "{}") {
                val al = transit.alerts()
                if (al.isEmpty()) noData("no alerts available from any connected source") else ok(al.joinToString("\n") { "${it.title}${it.body?.let { b -> ": $b" } ?: ""}" })
            },
            SimpleTool("getVehiclePosition", "Last known vehicle position", "{vehicleId: string}", listOf("vehicleId")) { a ->
                val hit = vehicles?.find(a.getValue("vehicleId")) ?: return@SimpleTool ToolResult(true, "NO_DATA: LIVE DATA UNAVAILABLE", "none", Freshness.UNAVAILABLE)
                val (v, f) = hit
                ToolResult(true, "${v.id} ${v.lat},${v.lon} line=${v.line ?: "?"} observedAt=${v.observedAt ?: "unknown"}", "vehicle-feed", f)
            },
            SimpleTool("searchLocalTransitData", "Search stops and routes", "{query: string}", listOf("query")) { a ->
                val n = norm(a.getValue("query"))
                val s = transit.stops().filter { norm(it.name).contains(n) }.take(5).joinToString("\n") { "STOP ${stopLine(it)}" }
                val r = transit.routes().filter { norm(it.shortName + " " + (it.longName ?: "")).contains(n) }.take(5).joinToString("\n") { "ROUTE ${it.shortName} ${it.longName ?: ""}" }
                val all = listOf(s, r).filter { it.isNotBlank() }.joinToString("\n")
                if (all.isBlank()) noData("nothing matched") else ok(all)
            },
            SimpleTool("getAppSettings", "Current app settings", "{}") { ToolResult(true, user.settingsSummary(), "local", Freshness.LIVE) },
            SimpleTool("getFavoriteStops", "Favorite stops", "{}") {
                val ids = user.favoriteStopIds()
                if (ids.isEmpty()) noData("no favorite stops") else ToolResult(true, ids.joinToString(","), "local", Freshness.LIVE)
            },
            SimpleTool("getFavoriteRoutes", "Favorite routes", "{}") {
                val ids = user.favoriteRouteIds()
                if (ids.isEmpty()) noData("no favorite routes") else ToolResult(true, ids.joinToString(","), "local", Freshness.LIVE)
            },
            SimpleTool("getRecentTrips", "Recent trips", "{}") {
                val t = user.recentTrips()
                if (t.isEmpty()) noData("no recent trips") else ToolResult(true, t.joinToString("\n"), "local", Freshness.LIVE)
            },
        )
    }
}
