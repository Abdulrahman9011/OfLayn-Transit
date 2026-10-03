package com.oflayn.app.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.oflayn.app.Settings
import com.oflayn.domain.ai.LocalLlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class Verdict { OK, NOT_RECOMMENDED, UNSUPPORTED }

data class DeviceReport(
    val totalRamMb: Long, val availRamMb: Long, val freeStorageMb: Long, val abis: List<String>, val sdk: Int,
    val verdict: Verdict, val reasons: List<String>,
)

sealed interface GemmaUi {
    data object Idle : GemmaUi
    data class Downloading(val bytes: Long, val total: Long) : GemmaUi
    data class Error(val message: String) : GemmaUi
    data object Working : GemmaUi
}

/**
 * Gemma 4 E2B on-device via LiteRT-LM. E2B is the ceiling for this app: E4B is intentionally not wired anywhere.
 * Nothing is downloaded or loaded unless the user asks; the engine is released when the AI screen is left.
 */
class GemmaManager(private val ctx: Context, private val settings: Settings) : LocalLlmEngine {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(dir, MODEL_FILE)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private val _ui = MutableStateFlow<GemmaUi>(GemmaUi.Idle)
    val ui: StateFlow<GemmaUi> = _ui

    val installed: Boolean get() = modelFile.exists() && modelFile.length() > MIN_VALID_BYTES
    val installedBytes: Long get() = if (modelFile.exists()) modelFile.length() else 0L

    fun report(): DeviceReport {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = mi.totalMem / MB
        val avail = mi.availMem / MB
        val st = StatFs(ctx.filesDir.path)
        val free = st.availableBytes / MB
        val abis = Build.SUPPORTED_ABIS.toList()
        val reasons = ArrayList<String>()
        var verdict = Verdict.OK
        if ("arm64-v8a" !in abis) { verdict = Verdict.UNSUPPORTED; reasons += "No arm64-v8a ABI" }
        if (total < MIN_TOTAL_RAM_MB) { verdict = Verdict.UNSUPPORTED; reasons += "Total RAM $total MB < $MIN_TOTAL_RAM_MB MB" }
        if (verdict == Verdict.OK && avail < MIN_AVAIL_RAM_MB) { verdict = Verdict.NOT_RECOMMENDED; reasons += "Available RAM $avail MB < $MIN_AVAIL_RAM_MB MB now" }
        if (settings.gemmaBenchOk == false) { verdict = Verdict.NOT_RECOMMENDED; reasons += "Benchmark failed on this device" }
        return DeviceReport(total, avail, free, abis, Build.VERSION.SDK_INT, verdict, reasons)
    }

    /** User-initiated download with progress; cancelling the calling coroutine aborts and removes the partial file. */
    suspend fun download() = withContext(Dispatchers.IO) {
        val r = report()
        if (r.verdict == Verdict.UNSUPPORTED) { _ui.value = GemmaUi.Error(r.reasons.joinToString()); return@withContext }
        if (r.freeStorageMb < MODEL_APPROX_MB + STORAGE_MARGIN_MB) { _ui.value = GemmaUi.Error("Need ${MODEL_APPROX_MB + STORAGE_MARGIN_MB} MB free, have ${r.freeStorageMb} MB"); return@withContext }
        val part = File(dir, "$MODEL_FILE.part")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) { _ui.value = GemmaUi.Error("HTTP ${conn.responseCode}"); return@withContext }
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input -> part.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    if (!isActive) throw CancellationException()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    _ui.value = GemmaUi.Downloading(done, total)
                }
            } }
            if (total > 0 && done != total) { part.delete(); _ui.value = GemmaUi.Error("Incomplete download"); return@withContext }
            if (done < MIN_VALID_BYTES) { part.delete(); _ui.value = GemmaUi.Error("File too small to be the model"); return@withContext }
            modelFile.delete()
            part.renameTo(modelFile)
            settings.clearGemmaBench()
            _ui.value = GemmaUi.Idle
        } catch (e: CancellationException) {
            part.delete(); _ui.value = GemmaUi.Idle; throw e
        } catch (e: Exception) {
            part.delete(); _ui.value = GemmaUi.Error(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        } finally { conn?.disconnect() }
    }

    suspend fun delete() = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { engine?.close() }; engine = null; modelFile.delete(); File(dir, "$MODEL_FILE.part").delete() }
        settings.clearGemmaBench()
    }

    /** Loads the model, runs one short generation and stores the timings. Fails closed: a failure marks Gemma unusable here. */
    suspend fun benchmark(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!installed) return@withContext "Model not installed"
            _ui.value = GemmaUi.Working
            try {
                val t0 = System.nanoTime()
                val e = ensureEngine()
                val loadMs = (System.nanoTime() - t0) / 1_000_000
                val t1 = System.nanoTime()
                val out = e.createConversation().use { it.sendMessage("Reply with the single word OK.").toString() }
                val genMs = (System.nanoTime() - t1) / 1_000_000
                val ok = out.isNotBlank() && loadMs <= MAX_LOAD_MS && genMs <= MAX_GEN_MS
                val summary = "load ${loadMs} ms, short reply ${genMs} ms, " + if (ok) "usable" else "too slow for this device"
                settings.setGemmaBench(ok, summary)
                _ui.value = GemmaUi.Idle
                summary
            } catch (e: Exception) {
                val s = "benchmark failed: ${e.javaClass.simpleName}"
                settings.setGemmaBench(false, s); _ui.value = GemmaUi.Error(s); s
            }
        }
    }

    private fun ensureEngine(): Engine {
        engine?.let { return it }
        val e = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), cacheDir = ctx.cacheDir.path))
        e.initialize() // may take several seconds; always called off the main thread
        engine = e
        return e
    }

    override suspend fun isModelInstalled(): Boolean = installed && report().verdict != Verdict.UNSUPPORTED && settings.gemmaBenchOk != false

    override suspend fun complete(prompt: String): String = mutex.withLock {
        withContext(Dispatchers.IO) { ensureEngine().createConversation().use { it.sendMessage(prompt).toString() } }
    }

    /** Frees the model's RAM (called when the AI screen is left). */
    override suspend fun unload() = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { engine?.close() }; engine = null }
    }

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_APPROX_MB = 2_600L
        const val STORAGE_MARGIN_MB = 1_000L
        const val MIN_VALID_BYTES = 2_000_000_000L
        // Design thresholds, to be tuned from real benchmark results on the target device.
        const val MIN_TOTAL_RAM_MB = 3_500L
        const val MIN_AVAIL_RAM_MB = 2_200L
        const val MAX_LOAD_MS = 60_000L
        const val MAX_GEN_MS = 30_000L
        private const val MB = 1024L * 1024L
    }
}
