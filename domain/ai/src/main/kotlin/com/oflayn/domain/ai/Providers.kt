package com.oflayn.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online provider. Talks ONLY to your own backend proxy (the Gemini key lives on the server, never in the APK).
 * [modelId] is read from config at call time, so it can be changed remotely without rebuilding.
 * Proxy contract: POST {model, messages:[{role,content}]} -> {text}
 */
class GeminiProxyProvider(
    private val proxyUrl: () -> String?,
    private val modelId: () -> String,
    private val systemPrompt: String,
    private val timeoutMs: Int = 15_000,
) : AiProvider {
    override val id = "gemini-proxy"
    override suspend fun isAvailable(): Boolean = !proxyUrl().isNullOrBlank()

    override suspend fun generate(request: AiRequest): AiResponse {
        val url = proxyUrl() ?: error("No proxy configured")
        val body = buildJsonObject {
            put("model", modelId())
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                request.history.takeLast(6).forEach { m ->
                    add(buildJsonObject { put("role", if (m.fromUser) "user" else "assistant"); put("content", m.text) })
                }
                add(buildJsonObject { put("role", "user"); put("content", request.text) })
            })
        }.toString()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) error("Proxy HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val out = (Json.parseToJsonElement(text) as JsonObject)["text"]?.jsonPrimitive?.contentOrNull
                ?: error("Proxy response has no text")
            return AiResponse(out, id)
        } finally {
            conn.disconnect()
        }
    }
}

/** Native on-device LLM runtime (e.g. MediaPipe LLM Inference). Implemented in the app module when the runtime and model file exist. */
interface LocalLlmEngine {
    suspend fun isModelInstalled(): Boolean
    suspend fun complete(prompt: String): String
    suspend fun unload()
}

class GemmaProvider(
    override val id: String,
    private val engine: LocalLlmEngine,
    private val systemPrompt: String,
) : AiProvider {
    override suspend fun isAvailable(): Boolean = engine.isModelInstalled()
    override suspend fun generate(request: AiRequest): AiResponse {
        val prompt = buildString {
            append(systemPrompt).append("\n")
            request.history.takeLast(4).forEach { append(if (it.fromUser) "User: " else "Assistant: ").append(it.text).append("\n") }
            append("User: ").append(request.text).append("\nAssistant:")
        }
        return AiResponse(engine.complete(prompt), id)
    }
}

/**
 * Keeps an LLM from being a source of facts: tools run first (via [assistant]); the LLM may only rephrase VERIFIED DATA.
 * If the assistant found nothing grounded, its own answer is returned and the LLM is not asked to guess.
 */
class GroundedProvider(
    override val id: String,
    private val llm: AiProvider,
    private val assistant: LocalDataAssistant,
) : AiProvider {
    override suspend fun isAvailable(): Boolean = llm.isAvailable()
    override suspend fun generate(request: AiRequest): AiResponse {
        val base = assistant.generate(request)
        if (!base.grounded) return base
        val prompt = "User question: ${request.text}\n" +
            "VERIFIED DATA (the only allowed source of facts):\n${base.text}\n" +
            "Answer in the user's language using only the data above. Do not add numbers, stops, routes, prices or times that are not in it. Keep source and freshness labels."
        val out = llm.generate(AiRequest(prompt, request.history))
        return AiResponse(out.text, llm.id, base.toolsUsed, true)
    }
}
