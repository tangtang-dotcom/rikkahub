package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.media.AudioManager
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidSystemTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_system_control",
    description = "Read or control device volume and media playback. Mutations require approval.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("volume")); add(JsonPrimitive("set_volume")); add(JsonPrimitive("media")) }) })
        put("stream", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("media")); add(JsonPrimitive("alarm")); add(JsonPrimitive("ring")); add(JsonPrimitive("notification")) }) })
        put("percent", buildJsonObject { put("type", "integer") }); put("media_action", buildJsonObject { put("type", "string") })
    }, required = listOf("action")) },
    needsApproval = { it.jsonObject["action"]?.jsonPrimitive?.contentOrNull in setOf("set_volume", "media") },
    execute = { input ->
        val o=input.jsonObject; val action=o["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream=when(o["stream"]?.jsonPrimitive?.contentOrNull){"alarm"->AudioManager.STREAM_ALARM;"ring"->AudioManager.STREAM_RING;"notification"->AudioManager.STREAM_NOTIFICATION;else->AudioManager.STREAM_MUSIC}
        val result=when(action){
            "volume" -> buildJsonObject { put("current",audio.getStreamVolume(stream));put("max",audio.getStreamMaxVolume(stream)) }
            "set_volume" -> { val percent=(o["percent"]?.jsonPrimitive?.intOrNull ?: error("percent is required")).coerceIn(0,100); audio.setStreamVolume(stream,(audio.getStreamMaxVolume(stream)*percent/100.0).toInt(),0); buildJsonObject { put("ok",true);put("percent",percent) } }
            "media" -> { val key = when (o["media_action"]?.jsonPrimitive?.contentOrNull) { "play" -> 126; "pause" -> 127; "next" -> 87; "previous" -> 88; "stop" -> 86; else -> error("media_action is required") }; audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)); audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key)); buildJsonObject { put("ok", true) } }
            else -> error("Unsupported action: $action")
        }
        listOf(UIMessagePart.Text(buildJsonObject { put("action",action);put("result",result) }.toString()))
    }
))
