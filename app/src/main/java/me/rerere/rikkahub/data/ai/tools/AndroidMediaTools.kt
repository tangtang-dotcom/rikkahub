package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** Safe, read-only Android settings and media-session bridge. */
fun createAndroidMediaTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "android_system_read",
        description = "Read a small allowlisted set of non-sensitive Android system settings.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("key", buildJsonObject {
                put("type", "string")
                put("enum", kotlinx.serialization.json.buildJsonArray {
                    add("screen_timeout_ms"); add("screen_brightness_mode"); add("screen_brightness")
                })
            })
        }, required = listOf("key")) },
        needsApproval = { false },
        execute = { input ->
            val key = input.jsonObject["key"]?.jsonPrimitive?.contentOrNull ?: error("key is required")
            val value = when (key) {
                "screen_timeout_ms" -> Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
                "screen_brightness_mode" -> Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                "screen_brightness" -> Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                else -> error("Unsupported setting: $key")
            }
            listOf(UIMessagePart.Text(buildJsonObject { put("key", key); put("value", value) }.toString()))
        },
    )
)
