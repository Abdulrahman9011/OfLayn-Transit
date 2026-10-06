package com.oflayn.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.oflayn.domain.trips.DaySnapshot
import com.oflayn.domain.trips.Gz
import com.oflayn.domain.trips.Json
import com.oflayn.domain.trips.LineInfo
import com.oflayn.domain.trips.Network
import com.oflayn.domain.trips.OfflineAssistant
import com.oflayn.domain.trips.Trip
import com.oflayn.domain.trips.TripStore
import com.oflayn.domain.trips.TripsEngine
import java.io.File

/**
 * Wires the offline core to Android.
 *
 * Start-up order matters: the bundled network and the bundled day snapshot are read from the APK
 * first, so the very first screen already has content with no internet and no download.
 */
class AppContainer(private val app: Context) {
    /** Exposed for the small helpers (location) that live outside this class. */
    fun appContext(): Context = app

    val settings = Settings(app.getSharedPreferences("oflayn", Context.MODE_PRIVATE))

    private val storeFile = File(app.filesDir, "day_trips.json")
    private val store = TripStore(
        write = { text -> runCatching { storeFile.writeText(text) } },
        read = { runCatching { storeFile.readText() }.getOrNull() },
    )

    /** The whole official Bursa bus network, inside the APK. */
    val network: Network by lazy {
        val bytes = runCatching { app.assets.open("offline/network.json.gz").use { it.readBytes() } }.getOrNull()
            ?: ByteArray(0)
        if (bytes.isEmpty()) Network(1, "", "", emptyList(), emptyList())
        else runCatching { Network.fromJson(Json.parse(Gz.read(bytes))) }.getOrElse { Network(1, "", "", emptyList(), emptyList()) }
    }

    private val bundledSnapshot: DaySnapshot? by lazy {
        val bytes = runCatching { app.assets.open("offline/day_snapshot.json.gz").use { it.readBytes() } }.getOrNull()
            ?: return@lazy null
        runCatching { DaySnapshot.fromJson(Json.parse(Gz.read(bytes))) }.getOrNull()
    }

    val engine: TripsEngine by lazy {
        val e = TripsEngine(
            network = network,
            store = store,
            ttlMs = settings.ttlHours * 3_600_000L,
            online = { isOnline() },
            live = LiveTrips(app),
        )
        // First launch: seed the phone with the snapshot shipped in the APK so trips exist offline at once.
        if (e.stored() == null) bundledSnapshot?.let { runCatching { store.save(it) } }
        e
    }

    val assistant: OfflineAssistant by lazy { OfflineAssistant(network, engine) }

    fun isOnline(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifi(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun line(code: String): LineInfo? = network.line(code)
    fun tripsForLine(code: String): List<Trip> = engine.forLine(code)
    fun linesAt(stopId: Int): List<LineInfo> = network.linesAtStop(stopId)
}
