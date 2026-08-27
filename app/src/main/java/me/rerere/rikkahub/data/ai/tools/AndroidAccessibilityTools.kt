package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService

private val ACCESSIBILITY_ACTIONS = listOf("observe", "tap", "long_press", "input", "scroll_forward", "scroll_backward", "enter", "back", "home", "recents")

fun createAndroidAccessibilityTools(): List<Tool> = listOf(Tool(
    name = "android_accessibility",
    description = "Observe the active Android screen as a bounded UI tree, then act only using the returned observation_id and node index. Use observe before every action. Supports tap, long press, input, enter, scrolling, back, home, and recents. Expired observations and unavailable service return explicit errors; never retry blindly.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { ACCESSIBILITY_ACTIONS.forEach(::add) }) })
                put("observation_id", buildJsonObject { put("type", "string") })
                put("node_index", buildJsonObject { put("type", "integer") })
                put("text", buildJsonObject { put("type", "string") })
                put("max_nodes", buildJsonObject { put("type", "integer") })
            },
            required = listOf("action"),
        )
    },
    needsApproval = { input -> input.jsonObject["action"]?.jsonPrimitive?.contentOrNull != "observe" },
    execute = { input ->
        val p = input.jsonObject
        val action = p["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        require(action in ACCESSIBILITY_ACTIONS) { "Unsupported accessibility action: $action" }
        val result = when {
            action == "observe" -> {
                val observation = RikkaAccessibilityService.observe((p["max_nodes"]?.jsonPrimitive?.intOrNull ?: 120).coerceIn(1, 120))
                buildJsonObject {
                    put("ok", true); put("observation_id", observation.observationId)
                    observation.packageName?.let { put("package_name", it) }
                    put("nodes", buildJsonArray {
                        observation.nodes.forEach { node ->
                            add(buildJsonObject {
                                put("index", node.index); node.className?.let { put("class_name", it) }
                                node.text?.let { put("text", it) }; node.contentDescription?.let { put("content_description", it) }
                                put("clickable", node.clickable); put("editable", node.editable); put("enabled", node.enabled)
                                put("bounds", buildJsonObject { put("left", node.left); put("top", node.top); put("right", node.right); put("bottom", node.bottom) })
                            })
                        }
                    })
                }
            }
            action in setOf("back", "home", "recents") -> {
                val r = RikkaAccessibilityService.global(action)
                buildJsonObject { put("ok", r.ok); put("action", r.action) }
            }
            else -> {
                val index = p["node_index"]?.jsonPrimitive?.intOrNull ?: error("node_index is required")
                val observationId = p["observation_id"]?.jsonPrimitive?.contentOrNull ?: error("observation_id is required")
                val r = RikkaAccessibilityService.execute(observationId, index, action, p["text"]?.jsonPrimitive?.contentOrNull)
                buildJsonObject { put("ok", r.ok); put("action", r.action); put("observation_id", observationId) }
            }
        }
        listOf(UIMessagePart.Text(result.toString()))
    },
))
