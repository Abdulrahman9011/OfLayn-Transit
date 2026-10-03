package com.oflayn.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.oflayn.app.ai.GemmaManager
import com.oflayn.app.ai.Verdict
import com.oflayn.app.data.AppDb
import com.oflayn.app.data.RoomUserData
import com.oflayn.app.data.TransitRepository
import com.oflayn.core.model.GeoPoint
import com.oflayn.domain.ai.AiModelRouter
import com.oflayn.domain.ai.DeviceTier
import com.oflayn.domain.ai.GemmaProvider
import com.oflayn.domain.ai.GeminiProxyProvider
import com.oflayn.domain.ai.GroundedProvider
import com.oflayn.domain.ai.LocalDataAssistant
import com.oflayn.domain.ai.LocationSource
import com.oflayn.domain.ai.ToolRegistry
import com.oflayn.domain.ai.TransitTools
import com.oflayn.domain.fare.FareManifest
import com.oflayn.domain.fare.FareManifestLoader
import com.oflayn.domain.fare.LoadResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FusedLocationSource(private val ctx: Context) : LocationSource {
    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            try {
                // Balanced power while browsing; the GPS is only powered for this one-shot request.
                LocationServices.getFusedLocationProviderClient(ctx)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { l -> if (cont.isActive) cont.resume(l?.let { GeoPoint(it.latitude, it.longitude) }) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** Single place that wires storage, data, tools and AI. Created lazily; nothing heavy runs at start-up. */
class AppContainer(private val app: Application) {
    val settings = Settings(app.getSharedPreferences("oflayn", Context.MODE_PRIVATE))
    private val db = AppDb.create(app)
    val repo = TransitRepository(db.dao(), settings)
    val userData = RoomUserData(db.dao(), settings)
    val dao get() = db.dao()
    val location = FusedLocationSource(app)
    val gemma = GemmaManager(app, settings)

    /** Cross-screen map focus (set by the Stops/Routes lists, consumed by the Map screen). */
    var mapFocusRouteId by mutableStateOf<String?>(null)
    var mapFocusStopId by mutableStateOf<String?>(null)

    val fareManifest: FareManifest? = run {
        val bytes = runCatching { app.assets.open("official/fare-manifest-2026-10-01.json").use { it.readBytes() } }.getOrNull()
        (bytes?.let { FareManifestLoader.loadBundled(it) } as? LoadResult.Accepted)?.manifest
    }

    val tools = ToolRegistry(TransitTools.build(repo, location, userData, fareManifest, null))
    private val localAssistant = LocalDataAssistant(tools)

    fun isOnline(): Boolean {
        if (settings.offlineMode) return false
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val systemPrompt = "You are the assistant of a Bursa public-transit app. Never invent fares, stops, routes, ETAs, balances or live data. Use only the verified data you are given. Reply in the user's language."

    private val gemini = GeminiProxyProvider({ settings.proxyUrl.ifBlank { null } }, { settings.modelId }, systemPrompt)
    private val gemmaProvider = GemmaProvider("gemma-4-e2b", gemma, systemPrompt)

    /**
     * Gemini (online) -> Gemma 4 E2B (on device, only if installed and not failed by benchmark) -> Local Data Assistant.
     * E4B is deliberately absent. Both LLMs only rephrase tool output (GroundedProvider); they never supply facts.
     */
    val router = AiModelRouter(
        online = GroundedProvider("gemini-grounded", gemini, localAssistant),
        gemmaE4B = null,
        gemmaE2B = GroundedProvider("gemma-e2b-grounded", gemmaProvider, localAssistant),
        localAssistant = localAssistant,
        isOnline = ::isOnline,
        tier = { if (gemma.report().verdict == Verdict.UNSUPPORTED) DeviceTier.LOW else DeviceTier.MID },
    )
}
