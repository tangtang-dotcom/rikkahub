package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidSettingsTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_display_settings",
    description = "Read or update a small allowlisted set of display settings: screen timeout, brightness mode, brightness, and auto-rotate. Updates require approval and WRITE_SETTINGS access.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("read"); add("write") }) })
        put("key", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("screen_timeout_ms"); add("brightness_mode"); add("brightness"); add("auto_rotate") }) })
        put("value", buildJsonObject { put("type", "integer"); put("description", "Value for write: timeout milliseconds, mode 0/1, brightness 0-255, or auto-rotate 0/1") })
    }, required = listOf("action", "key")) },
    needsApproval = { it.jsonObject["action"]?.jsonPrimitive?.contentOrNull == "write" },
    execute = { input ->
        val o=input.jsonObject; val action=o["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val key=o["key"]?.jsonPrimitive?.contentOrNull ?: error("key is required")
        val resolver=context.contentResolver
        val setting=when(key){
            "screen_timeout_ms" -> Settings.System.SCREEN_OFF_TIMEOUT
            "brightness_mode" -> Settings.System.SCREEN_BRIGHTNESS_MODE
            "brightness" -> Settings.System.SCREEN_BRIGHTNESS
            "auto_rotate" -> Settings.System.ACCELEROMETER_ROTATION
            else -> error("Unsupported key: $key")
        }
        val result=if(action=="read") buildJsonObject { put("key",key);put("value",Settings.System.getInt(resolver,setting)) }
        else {
            require(Settings.System.canWrite(context)) { "WRITE_SETTINGS access is not granted" }
            val raw=o["value"]?.jsonPrimitive?.intOrNull ?: error("value is required")
            val value=when(key){"screen_timeout_ms"->raw.coerceIn(1_000,86_400_000);"brightness"->raw.coerceIn(0,255);"brightness_mode"->raw.coerceIn(0,1);"auto_rotate"->raw.coerceIn(0,1);else->raw}
            Settings.System.putInt(resolver,setting,value)
            buildJsonObject { put("key",key);put("value",value);put("ok",true) }
        }
        listOf(UIMessagePart.Text(buildJsonObject { put("action",action);put("result",result) }.toString()))
    }
))
