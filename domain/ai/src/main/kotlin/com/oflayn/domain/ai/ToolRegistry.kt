package com.oflayn.domain.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class ToolRegistry(tools: List<AiTool>, private val timeoutMs: Long = 5_000) {
    private val byName = tools.associateBy { it.name }
    val names: Set<String> get() = byName.keys
    fun describe(): List<AiTool> = byName.values.toList()

    suspend fun call(name: String, args: Map<String, String> = emptyMap()): ToolResult {
        val tool = byName[name] ?: return ToolResult.failure("Unknown tool: $name")
        tool.validate(args)?.let { return ToolResult.failure(it) }
        return try {
            withTimeoutOrNull(timeoutMs) { tool.execute(args) } ?: ToolResult.failure("Tool $name timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.failure("Tool $name failed: ${e.javaClass.simpleName}")
        }
    }
}
