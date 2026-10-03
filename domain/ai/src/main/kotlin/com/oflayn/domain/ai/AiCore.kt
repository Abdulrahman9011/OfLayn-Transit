package com.oflayn.domain.ai

import com.oflayn.core.model.Freshness

data class AiMessage(val fromUser: Boolean, val text: String)

data class AiRequest(val text: String, val history: List<AiMessage> = emptyList())

data class AiResponse(
    val text: String,
    val provider: String,
    val toolsUsed: List<String> = emptyList(),
    /** true when every fact in [text] came from a tool result (not from model memory). */
    val grounded: Boolean = false,
)

interface AiProvider {
    val id: String
    suspend fun isAvailable(): Boolean
    suspend fun generate(request: AiRequest): AiResponse
}

data class ToolResult(
    val ok: Boolean,
    val data: String,
    val source: String,
    val freshness: Freshness,
    val error: String? = null,
) {
    companion object {
        fun failure(error: String) = ToolResult(false, "", "none", Freshness.UNAVAILABLE, error)
    }
}

interface AiTool {
    val name: String
    val description: String
    /** JSON-schema-like text describing accepted arguments. */
    val inputSchema: String
    val outputSchema: String get() = "{ok, data, source, freshness}"
    /** Returns an error message if [args] are invalid, else null. */
    fun validate(args: Map<String, String>): String? = null
    suspend fun execute(args: Map<String, String>): ToolResult
}
