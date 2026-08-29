package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.overlay.AccessibilityActionEffects

/** Hands a validated URI to an external Android handler; it never fetches web content itself. */
fun createAndroidUriTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_open_uri",
    description = "Open a validated http(s), tel, mailto, geo, or app-specific URI in an external Android application. Requires approval.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("uri", buildJsonObject { put("type", "string"); put("description", "URI to hand to Android") })
    }, required = listOf("uri")) },
    needsApproval = { true }, execute = { input ->
        val raw = input.jsonObject["uri"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: error("uri is required")
        require(raw.length <= 2048) { "uri is too long" }
        val uri = Uri.parse(raw)
        require(uri.scheme?.lowercase() in setOf("http", "https", "tel", "mailto", "geo")) {
            "Unsupported URI scheme"
        }
        require(!uri.scheme.isNullOrBlank()) { "URI scheme is required" }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(intent.resolveActivity(context.packageManager) != null) { "No application can handle URI" }
        AccessibilityActionEffects.showOperation(context, "open_uri")
        context.startActivity(intent)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", true); put("opened", true); put("scheme", uri.scheme!!.lowercase())
        }.toString()))
    }
))
