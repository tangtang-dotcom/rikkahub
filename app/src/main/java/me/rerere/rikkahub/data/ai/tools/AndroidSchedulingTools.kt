package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidSchedulingTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "android_schedule",
        description = "Create a system alarm or countdown timer through Clock UI; requires approval.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("kind", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("alarm"))
                            add(JsonPrimitive("timer"))
                        })
                    })
                    put("hour", buildJsonObject { put("type", "integer") })
                    put("minute", buildJsonObject { put("type", "integer") })
                    put("duration_seconds", buildJsonObject { put("type", "integer") })
                    put("message", buildJsonObject { put("type", "string") })
                },
                required = listOf("kind"),
            )
        },
        needsApproval = { true },
        execute = { input ->
            val obj = input.jsonObject
            val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: error("kind is required")
            val action = when (kind) {
                "alarm" -> AlarmClock.ACTION_SET_ALARM
                "timer" -> AlarmClock.ACTION_SET_TIMER
                else -> error("Unsupported kind: $kind")
            }
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (kind == "alarm") {
                val hour = (obj["hour"]?.jsonPrimitive?.intOrNull
                    ?: error("hour is required")).coerceIn(0, 23)
                val minute = (obj["minute"]?.jsonPrimitive?.intOrNull
                    ?: error("minute is required")).coerceIn(0, 59)
                intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
                intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
            } else {
                val duration = (obj["duration_seconds"]?.jsonPrimitive?.intOrNull
                    ?: error("duration_seconds is required")).coerceIn(1, 86_400)
                intent.putExtra(AlarmClock.EXTRA_LENGTH, duration)
            }
            obj["message"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it.take(100)) }
            require(intent.resolveActivity(context.packageManager) != null) {
                "No clock application can handle $kind"
            }
            context.startActivity(intent)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("ok", true)
                put("kind", kind)
                put("opened_clock_ui", true)
            }.toString()))
        },
    ),
)
