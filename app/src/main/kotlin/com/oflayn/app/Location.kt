package com.oflayn.app

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot location, no Google Play Services needed. Returns null when the permission is missing
 * or no provider answers, so every caller can fall back to text search.
 */
suspend fun AppContainer.location(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    val ctx = appContext()
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return@withContext null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    var best: Location? = null
    for (p in providers) {
        val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (best == null || l.time > best!!.time) best = l
    }
    best?.let { it.latitude to it.longitude }
}
