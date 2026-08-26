package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** Package discovery and explicit app launch. Launching an app is approval-gated. */
fun createAndroidAppControlTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "android_app_control",
        description = "Inspect an installed package or launch its main activity. Launching is an external side effect and requires approval.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("info"); add("launch") }) })
            put("package_name", buildJsonObject { put("type", "string") })
        }, required = listOf("action", "package_name")) },
        needsApproval = { input -> input.jsonObject["action"]?.jsonPrimitive?.contentOrNull == "launch" },
        execute = { input ->
            val o = input.jsonObject
            val action = o["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val packageName = o["package_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: error("package_name is required")
            val pm = context.packageManager
            val app = try { pm.getApplicationInfo(packageName, 0) } catch (_: Exception) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "PACKAGE_NOT_FOUND"); put("package_name", packageName)
                }.toString()))
            }
            if (action == "launch") {
                val intent = pm.getLaunchIntentForPackage(packageName)
                    ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "NO_LAUNCH_ACTIVITY"); put("package_name", packageName)
                    }.toString()))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("ok", true); put("action", action); put("package_name", packageName)
                put("label", pm.getApplicationLabel(app).toString())
                put("enabled", app.enabled)
                put("system_app", (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            }.toString()))
        },
    )
)
