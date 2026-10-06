package com.oflayn.app

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI-observable settings. Nothing secret is stored here: the app has no API key at all. */
class Settings(private val prefs: SharedPreferences) {
    var language by mutableStateOf(prefs.getString("lang", "ar") ?: "ar"); private set
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system"); private set
    /** "حسب المدة": how long a saved day stays fresh before the app refreshes it. */
    var ttlHours by mutableStateOf(prefs.getInt("ttlHours", 6)); private set
    var autoRefresh by mutableStateOf(prefs.getBoolean("autoRefresh", true)); private set
    var wifiOnlyRefresh by mutableStateOf(prefs.getBoolean("wifiOnlyRefresh", false)); private set
    var liveVehicles by mutableStateOf(prefs.getBoolean("liveVehicles", false)); private set
    var showEstimates by mutableStateOf(prefs.getBoolean("showEstimates", true)); private set
    var lastRefreshMs by mutableStateOf(prefs.getLong("lastRefreshMs", 0L)); private set

    private fun put(k: String, v: String) = prefs.edit().putString(k, v).apply()
    private fun put(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    private fun put(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    private fun put(k: String, v: Long) = prefs.edit().putLong(k, v).apply()

    fun updateLanguage(v: String) { language = v; put("lang", v) }
    fun updateTheme(v: String) { theme = v; put("theme", v) }
    fun updateTtlHours(v: Int) { ttlHours = v.coerceIn(1, 48); put("ttlHours", ttlHours) }
    fun updateAutoRefresh(v: Boolean) { autoRefresh = v; put("autoRefresh", v) }
    fun updateWifiOnly(v: Boolean) { wifiOnlyRefresh = v; put("wifiOnlyRefresh", v) }
    fun updateLiveVehicles(v: Boolean) { liveVehicles = v; put("liveVehicles", v) }
    fun updateShowEstimates(v: Boolean) { showEstimates = v; put("showEstimates", v) }
    fun updateLastRefresh(v: Long) { lastRefreshMs = v; put("lastRefreshMs", v) }

    /** Inline 3-language string helper: Arabic / Turkish / English. */
    fun s(ar: String, tr: String, en: String): String = when (language) {
        "tr" -> tr; "en" -> en; else -> ar
    }
}
