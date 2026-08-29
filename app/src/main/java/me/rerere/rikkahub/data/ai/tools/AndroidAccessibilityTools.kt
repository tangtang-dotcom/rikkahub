package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.AccessibilityScreenshot
import me.rerere.rikkahub.accessibility.RikkaAccessibilityKeeper
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

private val ACCESSIBILITY_ACTIONS = listOf("observe", "tap", "tap_area", "long_press", "input", "scroll_forward", "scroll_backward", "scroll", "swipe", "enter", "back", "home", "recents", "notifications", "quick_settings")

fun createAndroidAccessibilityTools(
    context: Context,
    requireApproval: Boolean = true,
    protectionEnabled: Boolean = false,
    rootController: AndroidRootTerminalController? = null,
): List<Tool> = listOf(Tool(
    name = "android_accessibility",
    description = "Observe the active Android screen as a bounded UI tree, then act only using the returned observation_id and node index. Use observe before every action. Supports tap, long press, input, directional scrolling, physical-screen gestures, global actions, and optional screenshots. Node bounds always use physical screen pixels. When an observation includes a screenshot, coordinate gestures default to screenshot pixels and are converted to the physical display; pass coordinate_space=screen for node-derived coordinates. The user-enabled accessibility bridge executes gestures without per-action approval prompts. Expired observations and unavailable service return explicit errors; never retry blindly.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { ACCESSIBILITY_ACTIONS.forEach(::add) }) })
                put("observation_id", buildJsonObject { put("type", "string") })
                put("node_index", buildJsonObject { put("type", "integer") })
                put("text", buildJsonObject { put("type", "string") })
                put("direction", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("up"); add("down"); add("left"); add("right") }) })
                put("x1", buildJsonObject { put("type", "integer") })
                put("y1", buildJsonObject { put("type", "integer") })
                put("x2", buildJsonObject { put("type", "integer") })
                put("y2", buildJsonObject { put("type", "integer") })
                put("duration_ms", buildJsonObject { put("type", "integer") })
                put("coordinate_space", buildJsonObject {
                    put("type", "string"); put("enum", buildJsonArray { add("screen"); add("screenshot") })
                })
                put("max_nodes", buildJsonObject { put("type", "integer") })
                put("include_screenshot", buildJsonObject { put("type", "boolean") })
            },
            required = listOf("action"),
        )
    },
    needsApproval = { input ->
        requireApproval && input.jsonObject["action"]?.jsonPrimitive?.contentOrNull != "observe"
    },
    execute = { input ->
        val p = input.jsonObject
        val action = p["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        require(action in ACCESSIBILITY_ACTIONS) { "Unsupported accessibility action: $action" }
        RikkaAccessibilityKeeper.ensureAvailable(context, protectionEnabled, rootController)
        var screenshot: AccessibilityScreenshot? = null
        val result = when {
            action == "observe" -> {
                val observation = RikkaAccessibilityService.observe((p["max_nodes"]?.jsonPrimitive?.intOrNull ?: 120).coerceIn(1, 120))
                if (p["include_screenshot"]?.jsonPrimitive?.booleanOrNull == true) {
                    screenshot = RikkaAccessibilityService.captureScreenshot(observation.observationId)
                }
                buildJsonObject {
                    put("ok", true); put("observation_id", observation.observationId); put("truncated", observation.truncated)
                    put("coordinate_contract", buildJsonObject {
                        put("default_coordinate_space", if (screenshot == null) "screen" else "screenshot")
                        put("node_bounds_coordinate_space", "screen")
                    })
                    put("screen", buildJsonObject {
                        put("width", observation.display.width); put("height", observation.display.height)
                    })
                    screenshot?.let { capture ->
                        put("screenshot", buildJsonObject {
                            put("included", true); put("width", capture.width); put("height", capture.height)
                            put("screen_to_image_scale", buildJsonObject {
                                put("x", capture.width.toDouble() / observation.display.width)
                                put("y", capture.height.toDouble() / observation.display.height)
                            })
                        })
                    }
                    observation.packageName?.let { put("package_name", it) }
                    put("nodes", buildJsonArray {
                        observation.nodes.forEach { node ->
                            add(buildJsonObject {
                                put("index", node.index); node.className?.let { put("class_name", it) }
                                node.text?.let { put("text", it) }; node.contentDescription?.let { put("content_description", it) }
                                put("clickable", node.clickable); put("editable", node.editable); put("scrollable", node.scrollable); put("enabled", node.enabled)
                                put("bounds", buildJsonObject { put("left", node.left); put("top", node.top); put("right", node.right); put("bottom", node.bottom) })
                            })
                        }
                    })
                }
            }
            action in setOf("back", "home", "recents", "notifications", "quick_settings") -> {
                val r = RikkaAccessibilityService.global(action)
                buildJsonObject { put("ok", r.ok); put("action", r.action) }
            }
            action == "swipe" || action == "tap_area" -> {
                val left = p["x1"]?.jsonPrimitive?.intOrNull ?: error("x1 is required")
                val top = p["y1"]?.jsonPrimitive?.intOrNull ?: error("y1 is required")
                val right = p["x2"]?.jsonPrimitive?.intOrNull ?: error("x2 is required")
                val bottom = p["y2"]?.jsonPrimitive?.intOrNull ?: error("y2 is required")
                val duration = p["duration_ms"]?.jsonPrimitive?.longOrNull ?: if (action == "tap_area") 100L else 500L
                val r = RikkaAccessibilityService.gesture(
                    action, left, top, right, bottom, duration,
                    observationId = p["observation_id"]?.jsonPrimitive?.contentOrNull,
                    coordinateSpace = p["coordinate_space"]?.jsonPrimitive?.contentOrNull,
                )
                buildJsonObject { put("ok", r.ok); put("action", r.action) }
            }
            else -> {
                val index = p["node_index"]?.jsonPrimitive?.intOrNull ?: error("node_index is required")
                val observationId = p["observation_id"]?.jsonPrimitive?.contentOrNull ?: error("observation_id is required")
                val value = if (action == "scroll") p["direction"]?.jsonPrimitive?.contentOrNull
                    else p["text"]?.jsonPrimitive?.contentOrNull
                val r = runCatching { RikkaAccessibilityService.execute(observationId, index, action, value) }
                    .getOrElse { failure ->
                        if (action != "tap" && action != "long_press") throw failure
                        val bounds = RikkaAccessibilityService.nodeBounds(observationId, index)
                        RikkaAccessibilityService.gesture(action, bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY(), if (action == "long_press") p["duration_ms"]?.jsonPrimitive?.longOrNull ?: 800L else 100L, observationId, "screen")
                    }
                buildJsonObject {
                    put("ok", r.ok); put("action", r.action); put("observation_id", observationId)
                    r.direction?.let { put("direction", it) }; r.moved?.let { put("moved", it) }
                    r.atBoundary?.let { put("at_boundary", it) }; r.method?.let { put("method", it) }
                    r.deltaX?.let { put("delta_x", it) }; r.deltaY?.let { put("delta_y", it) }
                    r.verifiedBy?.let { put("verified_by", it) }; r.elapsedMs?.let { put("elapsed_ms", it) }
                }
            }
        }
        buildList {
            add(UIMessagePart.Text(result.toString()))
            screenshot?.let { add(UIMessagePart.Image(it.uri)) }
        }
    },
))
