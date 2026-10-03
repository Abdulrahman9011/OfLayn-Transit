package com.oflayn.domain.transit

import com.oflayn.core.model.GeoPoint
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import java.util.PriorityQueue

sealed interface Leg {
    data class Walk(val fromName: String, val toName: String, val meters: Double, val seconds: Int) : Leg
    /** Ride time is ESTIMATED from distance and [PlannerConfig.rideSpeedMps]; waiting time is unknown (not invented). */
    data class Ride(val route: Route, val fromStop: Stop, val toStop: Stop, val stopCount: Int, val estimatedSeconds: Int) : Leg
}

data class TripPlan(
    val legs: List<Leg>,
    val walkMeters: Double,
    val transfers: Int,
    val estimatedMovingSeconds: Int,
    /** Always true: no schedule or live data feeds this planner. */
    val estimated: Boolean = true,
    val waitingKnown: Boolean = false,
)

/** Tunable design parameters, not facts about any operator. */
data class PlannerConfig(
    val walkSpeedMps: Double = 1.3,
    val rideSpeedMps: Double = 5.5,
    val maxWalkToStopMeters: Double = 800.0,
    val maxTransferWalkMeters: Double = 250.0,
    val boardPenaltySeconds: Double = 300.0,
)

/** Dijkstra over (stop, route-on-board) states. Without a schedule provider it ranks by walking + estimated ride + boarding penalty. */
class NetworkPlanner(
    private val stops: List<Stop>,
    private val routes: List<Route>,
    private val config: PlannerConfig = PlannerConfig(),
) {
    private val stopIndex: Map<String, Int> = stops.withIndex().associate { it.value.id to it.index }
    private val walkLinks: Array<List<Pair<Int, Double>>> by lazy { buildWalkLinks() }

    private data class Node(val stop: Int, val route: Int)
    private data class Back(val prev: Node?, val kind: Char, val meters: Double)

    private fun buildWalkLinks(): Array<List<Pair<Int, Double>>> {
        val out = Array(stops.size) { mutableListOf<Pair<Int, Double>>() }
        for (i in stops.indices) for (j in i + 1 until stops.size) {
            val d = Geo.distanceMeters(stops[i].lat, stops[i].lon, stops[j].lat, stops[j].lon)
            if (d <= config.maxTransferWalkMeters) { out[i].add(j to d); out[j].add(i to d) }
        }
        return Array(stops.size) { out[it].toList() }
    }

    private fun positionOf(route: Route, stop: Int): Int = route.stopIds.indexOfFirst { stopIndex[it] == stop }

    fun plan(origin: GeoPoint, destination: GeoPoint): TripPlan? {
        if (stops.isEmpty() || routes.isEmpty()) return null
        val dist = HashMap<Node, Double>()
        val back = HashMap<Node, Back>()
        val pq = PriorityQueue<Pair<Double, Node>>(compareBy { it.first })

        for ((i, s) in stops.withIndex()) {
            val d = Geo.distanceMeters(origin.lat, origin.lon, s.lat, s.lon)
            if (d <= config.maxWalkToStopMeters) {
                val n = Node(i, -1)
                val c = d / config.walkSpeedMps
                dist[n] = c
                back[n] = Back(null, 'S', d)
                pq.add(c to n)
            }
        }
        val finalWalk = HashMap<Int, Double>()
        for ((i, s) in stops.withIndex()) {
            val d = Geo.distanceMeters(destination.lat, destination.lon, s.lat, s.lon)
            if (d <= config.maxWalkToStopMeters) finalWalk[i] = d
        }
        var bestCost = Double.MAX_VALUE
        var bestNode: Node? = null
        val settled = HashSet<Node>()
        while (pq.isNotEmpty()) {
            val (cost, node) = pq.poll()
            if (!settled.add(node)) continue
            if (cost >= bestCost) break
            if (node.route == -1) {
                val fw = finalWalk[node.stop]
                if (fw != null) {
                    val total = cost + fw / config.walkSpeedMps
                    if (total < bestCost) { bestCost = total; bestNode = node }
                }
            }
            fun relax(next: Node, add: Double, b: Back) {
                val nc = cost + add
                if (nc < (dist[next] ?: Double.MAX_VALUE)) { dist[next] = nc; back[next] = b; pq.add(nc to next) }
            }
            if (node.route == -1) {
                for ((r, route) in routes.withIndex()) {
                    val pos = positionOf(route, node.stop)
                    if (pos >= 0 && pos < route.stopIds.size - 1) relax(Node(node.stop, r), config.boardPenaltySeconds, Back(node, 'B', 0.0))
                }
                for ((j, d) in walkLinks[node.stop]) relax(Node(j, -1), d / config.walkSpeedMps, Back(node, 'W', d))
            } else {
                val route = routes[node.route]
                val pos = positionOf(route, node.stop)
                if (pos >= 0 && pos < route.stopIds.size - 1) {
                    val nextIdx = stopIndex[route.stopIds[pos + 1]]
                    if (nextIdx != null) {
                        val d = Geo.distanceMeters(stops[node.stop].lat, stops[node.stop].lon, stops[nextIdx].lat, stops[nextIdx].lon)
                        relax(Node(nextIdx, node.route), d / config.rideSpeedMps, Back(node, 'R', d))
                    }
                }
                relax(Node(node.stop, -1), 0.0, Back(node, 'A', 0.0))
            }
        }
        val end = bestNode ?: return null
        return buildPlan(end, back, destination, finalWalk[end.stop] ?: 0.0)
    }

    private fun buildPlan(end: Node, back: Map<Node, Back>, destination: GeoPoint, finalWalkM: Double): TripPlan {
        val chain = ArrayList<Pair<Node, Back>>()
        var cur: Node? = end
        while (cur != null) {
            val b = back.getValue(cur)
            chain.add(cur to b)
            cur = b.prev
        }
        chain.reverse()
        val legs = ArrayList<Leg>()
        var walkM = 0.0
        var moving = 0.0
        var boards = 0
        var rideStart: Stop? = null
        var rideRoute: Route? = null
        var rideCount = 0
        var rideMeters = 0.0
        var lastStop: Stop? = null
        for ((node, b) in chain) {
            val stop = stops[node.stop]
            when (b.kind) {
                'S' -> { walkM += b.meters; moving += b.meters / config.walkSpeedMps; legs += Leg.Walk("Origin", stop.name, b.meters, (b.meters / config.walkSpeedMps).toInt()) }
                'W' -> { walkM += b.meters; moving += b.meters / config.walkSpeedMps; legs += Leg.Walk(lastStop?.name ?: "", stop.name, b.meters, (b.meters / config.walkSpeedMps).toInt()) }
                'B' -> { boards++; rideStart = stop; rideRoute = routes[node.route]; rideCount = 0; rideMeters = 0.0 }
                'R' -> { rideCount++; rideMeters += b.meters; moving += b.meters / config.rideSpeedMps }
                'A' -> {
                    val r = rideRoute
                    val s = rideStart
                    if (r != null && s != null) legs += Leg.Ride(r, s, stop, rideCount, (rideMeters / config.rideSpeedMps).toInt())
                    rideRoute = null
                    rideStart = null
                }
            }
            lastStop = stop
        }
        if (finalWalkM > 0.0) {
            walkM += finalWalkM
            moving += finalWalkM / config.walkSpeedMps
            legs += Leg.Walk(lastStop?.name ?: "", "Destination", finalWalkM, (finalWalkM / config.walkSpeedMps).toInt())
        }
        return TripPlan(legs, walkM, (boards - 1).coerceAtLeast(0), moving.toInt())
    }
}
