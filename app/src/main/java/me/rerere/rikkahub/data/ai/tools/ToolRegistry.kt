package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** Final boundary for first-party, workspace, skill and MCP tools. */
internal fun normalizeToolRegistry(tools: List<Tool>): List<Tool> {
    val duplicates = tools.groupBy(Tool::name).filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Duplicate tool names: ${duplicates.sorted().joinToString(", ")}" }
    return tools.map(::withToolErrorBoundary)
}

private fun withToolErrorBoundary(tool: Tool): Tool = tool.copy(
    execute = { input ->
        try {
            tool.execute(input)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("ok", false)
                    put("error", buildJsonObject {
                        put("code", errorCode(error))
                        put("message", error.message ?: error::class.simpleName ?: "Tool execution failed")
                        put("retryable", isRetryable(error))
                    })
                }.toString()
            ))
        }
    }
)

private fun errorCode(error: Throwable): String {
    val message = error.message.orEmpty()
    // First-party tools use stable machine-readable codes as exception messages.
    // Preserve them instead of collapsing them into TOOL_EXECUTION_FAILED.
    if (message.matches(Regex("[A-Z][A-Z0-9_]+"))) return message
    return when (error) {
        is IllegalArgumentException -> "INVALID_ARGUMENT"
        is SecurityException -> "PERMISSION_DENIED"
        is java.util.concurrent.TimeoutException -> "TIMEOUT"
        else -> "TOOL_EXECUTION_FAILED"
    }
}

private fun isRetryable(error: Throwable): Boolean =
    error is java.io.IOException || error is java.util.concurrent.TimeoutException
