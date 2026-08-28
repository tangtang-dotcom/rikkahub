package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.browser.AgentBrowserSession
import org.json.JSONObject

private val BROWSER_ACTIONS = listOf(
    "navigate", "get_readable", "get_text", "find_elements", "click", "type", "scroll",
    "screenshot", "get_page_info", "go_back", "go_forward", "reload", "wait_for_selector",
)

fun createAndroidBrowserTools(context: Context, runId: String): List<Tool> = listOf(
    Tool(
        name = "browser_use",
        description = "Operate a shared offscreen Android WebView. One call performs one action; navigate before reading or interacting.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { BROWSER_ACTIONS.forEach(::add) }) })
            put("url", buildJsonObject { put("type", "string") })
            put("selector", buildJsonObject { put("type", "string") })
            put("text", buildJsonObject { put("type", "string") })
            put("submit", buildJsonObject { put("type", "boolean") })
            put("coordinate_x", buildJsonObject { put("type", "integer") })
            put("coordinate_y", buildJsonObject { put("type", "integer") })
            put("amount", buildJsonObject { put("type", "integer") })
            put("direction", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("up"); add("down") }) })
            put("offset", buildJsonObject { put("type", "integer") })
            put("max_chars", buildJsonObject { put("type", "integer") })
            put("read_image", buildJsonObject { put("type", "boolean") })
            put("timeout_ms", buildJsonObject { put("type", "integer") })
        }, required = listOf("action")) },
        execute = { input ->
            val result = AgentBrowserSession.execute(context, JSONObject(input.toString()), runId, "")
            buildList {
                add(UIMessagePart.Text(result.content))
                result.images.forEach { add(UIMessagePart.Image(it.dataUrl)) }
            }
        },
    )
)
