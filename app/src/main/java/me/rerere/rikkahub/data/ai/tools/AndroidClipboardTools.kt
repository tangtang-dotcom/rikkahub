package me.rerere.rikkahub.data.ai.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidClipboardTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "android_clipboard",
        description = "Read or replace the current text clipboard. Clipboard access is privacy-sensitive; mutations require approval.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("read")); add(JsonPrimitive("write")); add(JsonPrimitive("clear")) }) })
            put("text", buildJsonObject { put("type", "string") })
        }, required = listOf("action")) },
        needsApproval = { input -> input.jsonObject["action"]?.jsonPrimitive?.contentOrNull in setOf("write", "clear") },
        execute = { input ->
            val o = input.jsonObject
            val action = o["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val result = when (action) {
                "read" -> buildJsonObject {
                    put("has_primary_clip", clipboard.hasPrimaryClip())
                    put("text", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.take(20_000))
                }
                "write" -> {
                    val text = o["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                    clipboard.setPrimaryClip(ClipData.newPlainText("RikkaHub", text))
                    buildJsonObject { put("ok", true); put("length", text.length) }
                }
                "clear" -> { clipboard.clearPrimaryClip(); buildJsonObject { put("ok", true) } }
                else -> error("Unsupported action: $action")
            }
            listOf(UIMessagePart.Text(buildJsonObject { put("action", action); put("result", result) }.toString()))
        },
    ),
)
