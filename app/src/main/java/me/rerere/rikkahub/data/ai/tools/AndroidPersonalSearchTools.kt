package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.json.JSONObject

private val PERSONAL_SEARCH_TOOL_NAMES = listOf(
    "search_media","search_audio","search_recordings","search_files","search_calendar_events","search_contacts",
    "search_call_history","search_messages","search_downloads","search_coloros_notes","search_coloros_recordings",
    "search_recording_summaries","search_qq_chat_images","search_wechat_chat_images",
)

fun createAndroidPersonalSearchTools(root: AndroidRootTerminalController): List<Tool> {
    val source = StructuredPersonalDataSource(root)
    return PERSONAL_SEARCH_TOOL_NAMES.map { toolName -> Tool(
        name = toolName,
        description = "Bounded sensitive Android personal-data search using Eta's fixed provider/path contract. Optional query filters known columns and limit is 1..30.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("query",buildJsonObject{put("type","string")});put("limit",buildJsonObject{put("type","integer")})
        }) },
        execute = { input ->
            val content=source.execute(toolName,JSONObject(input.toString()))
                ?: JSONObject().put("ok",false).put("tool",toolName).put("code","TOOL_UNAVAILABLE").toString()
            listOf(UIMessagePart.Text(content))
        },
    ) }
}
