package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.json.JSONObject

fun createAndroidPrivateDatabaseTools(context:Context,root:AndroidRootTerminalController):List<Tool>{
    val source=StructuredPrivateDatabaseSource(context,root)
    return listOf("search_clipboard_history","get_health_summary").map{name->Tool(
        name=name,description="Read a bounded snapshot of a verified local Android database using Eta's fixed schema contract.",
        parameters={InputSchema.Obj(properties=buildJsonObject{put("query",buildJsonObject{put("type","string")});put("limit",buildJsonObject{put("type","integer")});put("enabled_only",buildJsonObject{put("type","boolean")});put("days",buildJsonObject{put("type","integer")})})},
        execute={input->listOf(UIMessagePart.Text(source.execute(name,JSONObject(input.toString()))?:JSONObject().put("ok",false).put("tool",name).put("code","TOOL_UNAVAILABLE").toString()))}
    )}
}
