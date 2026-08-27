package me.rerere.rikkahub.data.ai.tools

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** Reports radio state and opens the platform settings UI for user-controlled changes. */
fun createAndroidConnectivityTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_connectivity",
    description = "Read Wi-Fi/Bluetooth state or open the corresponding Android settings page. The settings-page action requires approval; the agent does not silently toggle radios.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("status")); add(JsonPrimitive("wifi_settings")); add(JsonPrimitive("bluetooth_settings")) }) })
    }, required = listOf("action")) },
    needsApproval = { it.jsonObject["action"]?.jsonPrimitive?.contentOrNull != "status" }, execute = { input ->
        val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val result = when (action) {
            "status" -> {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val bluetooth = BluetoothAdapter.getDefaultAdapter()
                buildJsonObject { put("wifi_enabled", wifi.isWifiEnabled); put("bluetooth_available", bluetooth != null); put("bluetooth_enabled", bluetooth?.isEnabled == true) }
            }
            "wifi_settings" -> { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); buildJsonObject { put("opened", "wifi_settings") } }
            "bluetooth_settings" -> { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); buildJsonObject { put("opened", "bluetooth_settings") } }
            else -> error("Unsupported action: $action")
        }
        listOf(UIMessagePart.Text(buildJsonObject { put("ok", true); put("action", action); put("result", result) }.toString()))
    }
))
