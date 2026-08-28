package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.json.JSONObject

fun createAndroidColorOsMemoryTools(context:Context,root:AndroidRootTerminalController):List<Tool>{
    val source=StructuredColorOsMemorySource(context,root)
    return listOf("search_coloros_memories","search_saved_places","search_personal_orders").map{name->Tool(
        name=name,description="Search a bounded read-only snapshot of the verified ColorOS system-memory database.",
        parameters={InputSchema.Obj(properties=buildJsonObject{put("query",buildJsonObject{put("type","string")});put("limit",buildJsonObject{put("type","integer")})})},
        execute={input->listOf(UIMessagePart.Text(source.execute(name,JSONObject(input.toString()))))}
    )}
}
