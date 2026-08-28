package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.toLocalString
import java.security.MessageDigest
import java.time.LocalDate

fun buildMemoryTools(
    json: Json,
    onCreation: suspend (String) -> AssistantMemory,
    onUpdate: suspend (Int, String) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit,
    onRead: suspend () -> List<AssistantMemory>,
    includeMutations: Boolean = true,
): List<Tool> = buildList {
    add(buildMemoryGetTool(onRead))
    if (includeMutations) add(buildMemoryMutationTool(json, onCreation, onUpdate, onDelete))
}

private fun buildMemoryGetTool(onRead: suspend () -> List<AssistantMemory>) = Tool(
    name = "memory_get",
    description = "Read persistent cross-conversation memory with bounded paging or case-insensitive query matching.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("query", buildJsonObject { put("type", "string"); put("maxLength", 500) })
        put("start_line", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        put("max_chars", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 32000) })
    }) },
    execute = { input ->
        val document = buildString {
            appendLine("# RikkaHub Memory")
            onRead().forEach { memory -> appendLine(); appendLine("## Memory ${memory.id}"); appendLine(memory.content) }
        }.trimEnd()
        val bytes = document.toByteArray()
        val revision = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val lines = document.lines()
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
            put("ok", true); put("revision", revision); put("bytes", bytes.size); put("line_count", lines.size)
            if (query.isBlank()) {
                put("start_line", start); if (visible.isEmpty()) put("end_line", JsonNull) else put("end_line", start + visible.size - 1)
            } else { put("start_line", JsonNull); put("end_line", JsonNull) }
            put("matched_lines", if (query.isBlank()) visible.size else selected.size)
            put("has_more", visible.size < selected.size); put("content", visible.joinToString("\n"))
        }
        listOf(UIMessagePart.Text(payload.toString()))
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
