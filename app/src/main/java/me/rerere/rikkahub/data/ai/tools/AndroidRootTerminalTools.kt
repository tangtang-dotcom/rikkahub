package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

fun createAndroidRootTerminalTools(
    controller: AndroidRootTerminalController,
    requireApproval: Boolean,
): List<Tool> = listOf(
    createTerminalTool(controller, requireApproval),
    createRunCommandTool(controller, requireApproval),
    createReadFileTool(controller),
    createWriteFileTool(controller, requireApproval),
    createListDirectoryTool(controller),
)

private fun createTerminalTool(controller: AndroidRootTerminalController, requireApproval: Boolean) = Tool(
    name = "terminal",
    description = "Manage terminal sessions on the current device. environment=android runs Android system commands and root operations; environment=linux runs the optional RikkaHub Linux tool environment. Use open_and_exec for one-shot commands, open then exec with session_id for persistent state, and async=true without session_id for long-running jobs. Use read_async_result to stream output and close to stop jobs or sessions.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        enumProperty("action", "open", "exec", "open_and_exec", "read_async_result", "close")
        enumProperty("identity", "user", "root")
        enumProperty("environment", "android", "linux")
        stringProperty("command"); stringProperty("cwd"); stringProperty("session_id"); stringProperty("job_id")
        integerProperty("timeout_ms"); booleanProperty("merge_stderr"); booleanProperty("async")
        integerProperty("offset_chars"); integerProperty("max_chars"); booleanProperty("close_if_done")
    }, required = listOf("action")) },
    needsApproval = { input -> terminalNeedsApproval(input.jsonObject["action"]?.jsonPrimitive?.contentOrNull, requireApproval) },
    execute = { input ->
        val p = input.jsonObject
        val result = withContext(Dispatchers.IO) {
            controller.terminalAction(
                action = required(p, "action"), command = p.string("command"), cwd = p.optionalString("cwd"),
                timeoutMs = (p.int("timeout_ms") ?: 30_000).coerceIn(1, 180_000),
                identity = p.optionalString("identity") ?: "root",
                mergeStderr = p.boolean("merge_stderr") ?: false,
                sessionId = p.optionalString("session_id"), jobId = p.optionalString("job_id"),
                async = p.boolean("async") ?: false, offsetChars = p.int("offset_chars") ?: 0,
                maxChars = (p.int("max_chars") ?: 8_000).coerceIn(1, 16_000),
                closeIfDone = p.boolean("close_if_done") ?: false,
                environment = p.optionalString("environment") ?: "android",
            )
        }
        listOf(UIMessagePart.Text(result))
    },
)

private fun createRunCommandTool(controller: AndroidRootTerminalController, requireApproval: Boolean) = Tool(
    name = "run_command",
    description = "Run a non-interactive command in Android's real root shell. Each call uses a fresh shell; do not use for interactive or long-running commands.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        stringProperty("command"); stringProperty("cwd"); integerProperty("timeout_seconds")
    }, required = listOf("command")) },
    needsApproval = { requireApproval },
    execute = { input ->
        val p = input.jsonObject
        val result = withContext(Dispatchers.IO) {
            controller.runCommand(required(p, "command"), p.optionalString("cwd"), (p.int("timeout_seconds") ?: 30).coerceIn(1, 180))
        }
        listOf(UIMessagePart.Text(result))
    },
)

private fun createReadFileTool(controller: AndroidRootTerminalController) = Tool(
    name = "read_file",
    description = "Read an Android file through the root boundary. Use offset_bytes and max_bytes for bounded paging.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        stringProperty("path"); integerProperty("offset_bytes"); integerProperty("max_bytes")
    }, required = listOf("path")) },
    execute = { input ->
        val p = input.jsonObject
        val result = withContext(Dispatchers.IO) {
            controller.readFile(required(p, "path"), p.int("offset_bytes") ?: 0, (p.int("max_bytes") ?: 65_536).coerceIn(1, 262_144))
        }
        listOf(UIMessagePart.Text(result))
    },
)

private fun createWriteFileTool(controller: AndroidRootTerminalController, requireApproval: Boolean) = Tool(
    name = "write_file",
    description = "Write an Android file through the root boundary. Parent directories are created. Use only when the user explicitly requested a file modification.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        stringProperty("path"); stringProperty("content"); booleanProperty("append")
    }, required = listOf("path", "content")) },
    needsApproval = { requireApproval },
    execute = { input ->
        val p = input.jsonObject
        val result = withContext(Dispatchers.IO) {
            controller.writeFile(required(p, "path"), required(p, "content"), p.boolean("append") ?: false)
        }
        listOf(UIMessagePart.Text(result))
    },
)

private fun createListDirectoryTool(controller: AndroidRootTerminalController) = Tool(
    name = "list_directory",
    description = "List an Android directory through the root boundary with bounded output.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        stringProperty("path"); booleanProperty("show_hidden"); integerProperty("limit")
    }) },
    execute = { input ->
        val p = input.jsonObject
        val result = withContext(Dispatchers.IO) {
            controller.listDirectory(p.optionalString("path").orEmpty(), p.boolean("show_hidden") ?: false, (p.int("limit") ?: 80).coerceIn(1, 200))
        }
        listOf(UIMessagePart.Text(result))
    },
)

private fun JsonObjectBuilder.stringProperty(name: String) = put(name, buildJsonObject { put("type", "string") })
private fun JsonObjectBuilder.integerProperty(name: String) = put(name, buildJsonObject { put("type", "integer") })
private fun JsonObjectBuilder.booleanProperty(name: String) = put(name, buildJsonObject { put("type", "boolean") })
private fun JsonObjectBuilder.enumProperty(name: String, vararg values: String) = put(name, buildJsonObject {
    put("type", "string"); put("enum", buildJsonArray { values.forEach { add(it) } })
})
private fun JsonObject.optionalString(name: String) = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.boolean(name: String) = this[name]?.jsonPrimitive?.booleanOrNull
private fun required(input: JsonObject, name: String) = input.optionalString(name) ?: error("$name is required")

internal fun terminalNeedsApproval(action: String?, requireApproval: Boolean): Boolean =
    requireApproval && action in setOf("exec", "open_and_exec")
