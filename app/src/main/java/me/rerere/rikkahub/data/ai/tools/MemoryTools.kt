package me.rerere.rikkahub.data.ai.tools

import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.toLocalString

fun buildMemoryTools(
    json: Json,
    onCreation: suspend (String) -> AssistantMemory,
    onUpdate: suspend (Int, String) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit,
    onRead: suspend () -> List<AssistantMemory>,
    onReplaceAll: (suspend (String) -> Unit)? = null,
    includeMutations: Boolean = true,
): List<Tool> = buildList {
    add(buildMemoryGetTool(onRead))
    if (includeMutations) {
        onReplaceAll?.let { add(buildMemoryWriteTool(onRead, it)) }
        add(buildMemoryMutationTool(json, onCreation, onUpdate, onDelete))
    }
}

private fun memoryDocument(memories: List<AssistantMemory>): String =
    memories.joinToString("\n\n") { it.content.trimEnd() }.trimEnd()

private fun memoryRevision(document: String): String = MessageDigest.getInstance("SHA-256")
    .digest(document.toByteArray()).joinToString("") { "%02x".format(it) }

private fun buildMemoryGetTool(onRead: suspend () -> List<AssistantMemory>) = Tool(
    name = "memory_get",
    description = "Read persistent cross-conversation memory with bounded paging or case-insensitive query matching.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("query", buildJsonObject { put("type", "string"); put("maxLength", 500) })
        put("start_line", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        put("max_chars", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 32000) })
    }) },
    execute = { input ->
        val document = memoryDocument(onRead())
        val bytes = document.toByteArray()
        val lines = if (document.isEmpty()) emptyList() else document.lines()
        val query = input.jsonObject["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val maxChars = (input.jsonObject["max_chars"]?.jsonPrimitive?.intOrNull ?: 12_000).coerceIn(1, 32_000)
        val start = (input.jsonObject["start_line"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val selected = if (query.isBlank()) lines.drop(start - 1) else lines.mapIndexedNotNull { index, line ->
            if (line.contains(query, ignoreCase = true)) "${index + 1}: $line" else null
        }
        val visible = ArrayList<String>(); var charCount = 0
        for (line in selected) {
            val extra = line.length + if (visible.isEmpty()) 0 else 1
            if (charCount + extra > maxChars) break
            visible += line; charCount += extra
        }
        val payload = buildJsonObject {
            put("ok", true); put("revision", memoryRevision(document)); put("bytes", bytes.size); put("line_count", lines.size)
            if (query.isBlank()) {
                put("start_line", start); if (visible.isEmpty()) put("end_line", JsonNull) else put("end_line", start + visible.size - 1)
            } else { put("start_line", JsonNull); put("end_line", JsonNull) }
            put("matched_lines", if (query.isBlank()) visible.size else selected.size)
            put("has_more", visible.size < selected.size); put("content", visible.joinToString("\n"))
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

private fun buildMemoryWriteTool(
    onRead: suspend () -> List<AssistantMemory>,
    onReplaceAll: suspend (String) -> Unit,
) = Tool(
    name = "memory_write",
    description = "Atomically update persistent memory using the exact revision from memory_get. Store durable facts only; never store secrets or transient requests.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("mode", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("replace_range"); add("append"); add("clear") }) })
        put("revision", buildJsonObject { put("type", "string"); put("minLength", 64); put("maxLength", 64) })
        put("start_line", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        put("end_line", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        put("content", buildJsonObject { put("type", "string"); put("maxLength", 3500) })
    }, required = listOf("mode", "revision")) },
    needsApproval = { true },
    execute = { input ->
        val args = input.jsonObject
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull ?: error("MODE_REQUIRED")
        val suppliedRevision = args["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(Regex("[0-9a-fA-F]{64}").matches(suppliedRevision)) { "INVALID_REVISION" }
        val current = memoryDocument(onRead())
        val currentRevision = memoryRevision(current)
        if (!currentRevision.equals(suppliedRevision, ignoreCase = true)) error("MEMORY_REVISION_CONFLICT")
        val content = args["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(content.length <= 3500) { "CONTENT_TOO_LARGE" }
        val updated = when (mode) {
            "clear" -> {
                require(args["start_line"] == null && args["end_line"] == null && content.isEmpty()) { "INVALID_CLEAR_ARGUMENTS" }
                ""
            }
            "append" -> {
                require(args["start_line"] == null && args["end_line"] == null && content.isNotBlank()) { "INVALID_APPEND_ARGUMENTS" }
                if (current.isBlank()) content.trim() else current.trimEnd() + "\n\n" + content.trim()
            }
            "replace_range" -> {
                val start = args["start_line"]?.jsonPrimitive?.intOrNull ?: error("START_LINE_REQUIRED")
                val end = args["end_line"]?.jsonPrimitive?.intOrNull ?: error("END_LINE_REQUIRED")
                val lines = if (current.isEmpty()) emptyList() else current.lines()
                require(start >= 1 && end >= start && end <= lines.size) { "INVALID_LINE_RANGE" }
                buildList {
                    addAll(lines.take(start - 1)); if (content.isNotEmpty()) addAll(content.lines()); addAll(lines.drop(end))
                }.joinToString("\n").trimEnd()
            }
            else -> error("INVALID_MODE")
        }
        onReplaceAll(updated)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", true); put("tool", "memory_write"); put("mode", mode); put("revision", memoryRevision(updated))
            put("bytes", updated.toByteArray().size); put("line_count", if (updated.isEmpty()) 0 else updated.lines().size)
        }.toString()))
    },
)

private fun buildMemoryMutationTool(
    json: Json,
    onCreation: suspend (String) -> AssistantMemory,
    onUpdate: suspend (Int, String) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit,
) = Tool(
    name = "memory_tool",
    description = "Store long-term information across conversations with create, edit, or delete. Merge similar records and do not store sensitive information. Today is ${LocalDate.now().toLocalString(true)}.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("create"); add("edit"); add("delete") }) })
        put("id", buildJsonObject { put("type", "integer") }); put("content", buildJsonObject { put("type", "string") })
    }, required = listOf("action")) },
    execute = {
        val params = it.jsonObject; val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val payload = when (action) {
            "create" -> json.encodeToJsonElement(AssistantMemory.serializer(), onCreation(params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")))
            "edit" -> json.encodeToJsonElement(AssistantMemory.serializer(), onUpdate(params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required"), params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")))
            "delete" -> { val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required"); onDelete(id); buildJsonObject { put("success", true); put("id", id) } }
            else -> error("unknown action: $action")
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)
