package me.rerere.rikkahub.data.ai.tools

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidDeviceTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_device_info",
    description = "Read Android device status, battery, memory, storage, and network information.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject { put("type", "string") })
    }, required = listOf("action")) },
    needsApproval = { false },
    execute = { input ->
        val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val payload = when (action) {
            "status" -> buildJsonObject {
                    put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL)
                    put("android_release", Build.VERSION.RELEASE ?: "unknown"); put("sdk", Build.VERSION.SDK_INT)
                    put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                }
            "battery" -> buildJsonObject {
                    val m = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val level = m.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    put("level_percent", level.takeIf { it in 0..100 })
                    put("charging_current_ua", m.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
                }
            "memory" -> buildJsonObject {
                    val i = ActivityManager.MemoryInfo()
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(i)
                    put("total_bytes", i.totalMem); put("available_bytes", i.availMem); put("low_memory", i.lowMemory)
                }
            "storage" -> buildJsonObject {
                    val s = StatFs(context.filesDir.absolutePath)
                    put("total_bytes", s.totalBytes); put("available_bytes", s.availableBytes)
                }
            "network" -> buildJsonObject {
                    val m = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val c = m.activeNetwork?.let(m::getNetworkCapabilities)
                    put("connected", c != null); put("validated", c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                    put("transport_wifi", c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                    put("transport_cellular", c?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                    put("transport_ethernet", c?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
                }
            else -> error("Unsupported action: $action")
            }
        listOf(UIMessagePart.Text(payload.toString()))
    },
))
