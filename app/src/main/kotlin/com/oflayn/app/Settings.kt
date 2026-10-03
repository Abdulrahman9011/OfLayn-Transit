package com.oflayn.app

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI-observable settings persisted in SharedPreferences (non-sensitive values only; no API keys). */
class Settings(private val prefs: SharedPreferences) {
    var language by mutableStateOf(prefs.getString("lang", "ar") ?: "ar"); private set
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system"); private set
    var cardType by mutableStateOf(prefs.getString("cardType", "UNKNOWN") ?: "UNKNOWN"); private set
    var nfcSound by mutableStateOf(prefs.getBoolean("nfcSound", true)); private set
    var vibration by mutableStateOf(prefs.getBoolean("vibration", true)); private set
    var offlineMode by mutableStateOf(prefs.getBoolean("offlineMode", false)); private set
    var autoSync by mutableStateOf(prefs.getBoolean("autoSync", true)); private set
    var mobileData by mutableStateOf(prefs.getBoolean("mobileData", true)); private set
    var voiceAi by mutableStateOf(prefs.getBoolean("voiceAi", true)); private set
    var continuousVoice by mutableStateOf(prefs.getBoolean("continuousVoice", false)); private set
    var navVoice by mutableStateOf(prefs.getBoolean("navVoice", true)); private set
    var notifications by mutableStateOf(prefs.getBoolean("notifications", true)); private set
    /** Changeable without rebuilding the app. */
    var modelId by mutableStateOf(prefs.getString("modelId", "gemini-3.7-flash") ?: "gemini-3.7-flash"); private set
    /** Your own HTTPS backend that holds the Gemini key. Empty = online AI disabled. */
    var proxyUrl by mutableStateOf(prefs.getString("proxyUrl", "") ?: ""); private set
    var overpassUrl by mutableStateOf(prefs.getString("overpassUrl", "https://overpass-api.de/api/interpreter") ?: ""); private set
    /** Basemap style (MapLibre). Default is a keyless public style that has NOT been verified here; change it freely. */
    var mapStyleUrl by mutableStateOf(prefs.getString("mapStyleUrl", "https://tiles.openfreemap.org/styles/liberty") ?: ""); private set
    /** User-typed balance in kurus; -1 = none. Always shown as MANUAL, never as a verified balance. */
    var manualBalanceKurus by mutableStateOf(prefs.getLong("manualBalanceKurus", -1L)); private set
    var manualBalanceAtMs by mutableStateOf(prefs.getLong("manualBalanceAtMs", 0L)); private set
    var gemmaBenchOk by mutableStateOf(if (prefs.contains("gemmaBenchOk")) prefs.getBoolean("gemmaBenchOk", false) else null); private set
    var gemmaBenchSummary by mutableStateOf(prefs.getString("gemmaBenchSummary", "") ?: ""); private set
    var linkedCardHash by mutableStateOf(prefs.getString("linkedCardHash", "") ?: ""); private set

    private fun put(k: String, v: String) = prefs.edit().putString(k, v).apply()
    private fun put(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    fun setLanguage(v: String) { language = v; put("lang", v) }
    fun setTheme(v: String) { theme = v; put("theme", v) }
    fun setCardType(v: String) { cardType = v; put("cardType", v) }
    fun setNfcSound(v: Boolean) { nfcSound = v; put("nfcSound", v) }
    fun setVibration(v: Boolean) { vibration = v; put("vibration", v) }
    fun setOfflineMode(v: Boolean) { offlineMode = v; put("offlineMode", v) }
    fun setAutoSync(v: Boolean) { autoSync = v; put("autoSync", v) }
    fun setMobileData(v: Boolean) { mobileData = v; put("mobileData", v) }
    fun setVoiceAi(v: Boolean) { voiceAi = v; put("voiceAi", v) }
    fun setContinuousVoice(v: Boolean) { continuousVoice = v; put("continuousVoice", v) }
    fun setNavVoice(v: Boolean) { navVoice = v; put("navVoice", v) }
    fun setNotifications(v: Boolean) { notifications = v; put("notifications", v) }
    fun setModelId(v: String) { modelId = v.trim(); put("modelId", modelId) }
    fun setProxyUrl(v: String) { proxyUrl = v.trim(); put("proxyUrl", proxyUrl) }
    fun setOverpassUrl(v: String) { overpassUrl = v.trim(); put("overpassUrl", overpassUrl) }
    fun setMapStyleUrl(v: String) { mapStyleUrl = v.trim(); put("mapStyleUrl", mapStyleUrl) }
    fun setManualBalance(kurus: Long?) {
        manualBalanceKurus = kurus ?: -1L; manualBalanceAtMs = if (kurus == null) 0L else System.currentTimeMillis()
        prefs.edit().putLong("manualBalanceKurus", manualBalanceKurus).putLong("manualBalanceAtMs", manualBalanceAtMs).apply()
    }
    fun setGemmaBench(ok: Boolean, summary: String) {
        gemmaBenchOk = ok; gemmaBenchSummary = summary
        prefs.edit().putBoolean("gemmaBenchOk", ok).putString("gemmaBenchSummary", summary).apply()
    }
    fun clearGemmaBench() { gemmaBenchOk = null; gemmaBenchSummary = ""; prefs.edit().remove("gemmaBenchOk").remove("gemmaBenchSummary").apply() }
    fun setLinkedCardHash(v: String) { linkedCardHash = v; put("linkedCardHash", v) }

    fun summary(): String = "language=$language theme=$theme offlineMode=$offlineMode autoSync=$autoSync mobileData=$mobileData voiceAi=$voiceAi"
}
