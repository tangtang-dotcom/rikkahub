package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.AccessibilityActionResult
import me.rerere.rikkahub.accessibility.RikkaAccessibilityKeeper
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

internal fun etaGestureTools(
    context: Context,
    protectionEnabled: Boolean,
    rootController: AndroidRootTerminalController?,
    needsApproval: Boolean,
): List<Tool> {
    fun ensure() = RikkaAccessibilityKeeper.ensureAvailable(context, protectionEnabled, rootController)

    fun resultParts(result: AccessibilityActionResult, tool: String) = listOf(
        UIMessagePart.Text(buildJsonObject {
            put("ok", result.ok)
            put("tool", tool)
            put("action", result.action)
            result.direction?.let { put("direction", it) }
            result.moved?.let { put("moved", it) }
            result.atBoundary?.let { put("at_boundary", it) }
            result.method?.let { put("method", it) }
            result.deltaX?.let { put("delta_x", it) }
            result.deltaY?.let { put("delta_y", it) }
            result.verifiedBy?.let { put("verified_by", it) }
            result.elapsedMs?.let { put("elapsed_ms", it) }
        }.toString()),
    )

    fun coordinateSchema(required: List<String>, durationMin: Int, durationMax: Int) = InputSchema.Obj(
        properties = buildJsonObject {
            listOf("x", "y", "x1", "y1", "x2", "y2").forEach {
                put(it, buildJsonObject { put("type", "integer") })
            }
            put("duration_ms", buildJsonObject {
                put("type", "integer")
                put("minimum", durationMin)
                put("maximum", durationMax)
            })
            put("coordinate_space", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { add("screen"); add("screenshot") })
            })
        },
        required = required,
    )

    fun coordinateTool(
        name: String,
        description: String,
        required: List<String>,
        defaultDuration: Long,
        durationMin: Int,
        durationMax: Int,
    ) = Tool(
        name = name,
        description = description,
        parameters = { coordinateSchema(required, durationMin, durationMax) },
        needsApproval = { needsApproval },
        execute = { input ->
            ensure()
            val args = input.jsonObject
            val x1 = (args["x1"] ?: args["x"])?.jsonPrimitive?.intOrNull ?: error("x/x1 is required")
            val y1 = (args["y1"] ?: args["y"])?.jsonPrimitive?.intOrNull ?: error("y/y1 is required")
            val x2 = args["x2"]?.jsonPrimitive?.intOrNull ?: x1
            val y2 = args["y2"]?.jsonPrimitive?.intOrNull ?: y1
            resultParts(
                RikkaAccessibilityService.gesture(
                    action = name,
                    x1 = x1,
                    y1 = y1,
                    x2 = x2,
                    y2 = y2,
                    durationMs = args["duration_ms"]?.jsonPrimitive?.longOrNull ?: defaultDuration,
                    coordinateSpace = args["coordinate_space"]?.jsonPrimitive?.contentOrNull,
                ),
                name,
            )
        },
    )

    fun elementSchema(withDuration: Boolean = false, withDirection: Boolean = false) = InputSchema.Obj(
        properties = buildJsonObject {
            put("index", buildJsonObject { put("type", "integer"); put("minimum", 0) })
            put("observation_id", buildJsonObject { put("type", "string") })
            if (withDuration) put("duration_ms", buildJsonObject {
                put("type", "integer"); put("minimum", 300); put("maximum", 3000)
            })
            if (withDirection) put("direction", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { add("up"); add("down"); add("left"); add("right") })
            })
        },
        required = buildList {
            add("index")
            add("observation_id")
            if (withDirection) add("direction")
        },
    )

    fun elementTool(
        name: String,
        description: String,
        action: String,
        withDuration: Boolean = false,
        withDirection: Boolean = false,
    ) = Tool(
        name = name,
        description = description,
        parameters = { elementSchema(withDuration, withDirection) },
        needsApproval = { needsApproval },
        execute = { input ->
            ensure()
            val args = input.jsonObject
            val observationId = args["observation_id"]?.jsonPrimitive?.contentOrNull
                ?: error("observation_id is required")
            val index = args["index"]?.jsonPrimitive?.intOrNull ?: error("index is required")
            val value = if (withDirection) args["direction"]?.jsonPrimitive?.contentOrNull else null
            resultParts(RikkaAccessibilityService.execute(observationId, index, action, value), name)
        },
    )

    val directionSchema = InputSchema.Obj(
        properties = buildJsonObject {
            put("direction", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { add("up"); add("down"); add("left"); add("right") })
            })
        },
        required = listOf("direction"),
    )

    return listOf(
        coordinateTool(EtaCompatibilityToolNames.TAP, "Tap a screen or latest screenshot coordinate.", listOf("x", "y"), 100, 100, 2000),
        coordinateTool(EtaCompatibilityToolNames.TAP_AREA, "Tap the center of a rectangular area.", listOf("x1", "y1", "x2", "y2"), 100, 100, 2000),
        elementTool(EtaCompatibilityToolNames.TAP_ELEMENT, "Tap a node from a matching observe_screen snapshot.", "tap"),
        coordinateTool(EtaCompatibilityToolNames.LONG_PRESS, "Long press a screen or latest screenshot coordinate.", listOf("x", "y"), 800, 300, 3000),
        elementTool(EtaCompatibilityToolNames.LONG_PRESS_ELEMENT, "Long press a node from observe_screen.", "long_press", withDuration = true),
        coordinateTool(EtaCompatibilityToolNames.SWIPE, "Swipe between screen or latest screenshot coordinates.", listOf("x1", "y1", "x2", "y2"), 500, 100, 2000),
        Tool(
            name = EtaCompatibilityToolNames.SCROLL,
            description = "Scroll the largest visible scrollable container in a content-browsing direction.",
            parameters = { directionSchema },
            needsApproval = { needsApproval },
            execute = { input ->
                ensure()
                val direction = input.jsonObject["direction"]?.jsonPrimitive?.contentOrNull
                    ?: error("direction is required")
                val observation = RikkaAccessibilityService.observe(120)
                val target = observation.nodes.asSequence()
                    .filter { it.scrollable && it.enabled }
                    .maxByOrNull {
                        (it.right - it.left).coerceAtLeast(0).toLong() *
                            (it.bottom - it.top).coerceAtLeast(0).toLong()
                    }
                    ?: error("ACCESSIBILITY_NODE_NOT_SCROLLABLE")
                resultParts(
                    RikkaAccessibilityService.execute(observation.observationId, target.index, "scroll", direction),
                    EtaCompatibilityToolNames.SCROLL,
                )
            },
        ),
        elementTool(EtaCompatibilityToolNames.SCROLL_ELEMENT, "Scroll a specified node from observe_screen.", "scroll", withDirection = true),
    )
}
