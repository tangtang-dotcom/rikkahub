package me.rerere.rikkahub.data.ai.tools

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.KeyguardManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

private val DEVICE_ACTIONS = listOf("status", "battery", "memory", "storage", "network", "time", "environment", "apps")

internal fun androidDeviceAction(input: kotlinx.serialization.json.JsonObject): String {
    val action = input["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
    require(action in DEVICE_ACTIONS) { "Unsupported action: $action" }
    return action
}

fun createAndroidDeviceTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_device_info",
    description = "Read Android device status, battery, memory, storage, network, time, environment, and installed-app information.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("action", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { DEVICE_ACTIONS.forEach { add(it) } })
                            put("description", "Read-only diagnostic category")
                        })
                        put("offset", buildJsonObject { put("type", "integer") })
                        put("limit", buildJsonObject { put("type", "integer") })
    }, required = listOf("action")) },
    needsApproval = { false },
    execute = { input ->
        val action = androidDeviceAction(input.jsonObject)
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
            "time" -> buildJsonObject {
                    val now = java.time.ZonedDateTime.now()
                    put("epoch_seconds", java.time.Instant.now().epochSecond)
                    put("iso8601", now.toString())
                    put("timezone", java.time.ZoneId.systemDefault().id)
                    put("offset", now.offset.toString())
                    put("locale", java.util.Locale.getDefault().toLanguageTag())
                }
            "environment" -> buildJsonObject {
                    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    put("interactive", power.isInteractive)
                    put("keyguard_locked", keyguard.isKeyguardLocked)
                    put("ringer_mode", audio.ringerMode)
                    put("music_active", audio.isMusicActive)
                    put("volume_music", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                    put("max_volume_music", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                }
            "apps" -> {
                    val offset = (input.jsonObject["offset"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
                    val limit = (input.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1, 100)
                    val apps = context.packageManager.getInstalledApplications(0)
                        .sortedBy { context.packageManager.getApplicationLabel(it).toString().lowercase() }
                    val page = apps.drop(offset).take(limit)
                    buildJsonObject {
                        put("offset", offset); put("limit", limit); put("total", apps.size)
                        put("has_more", offset + page.size < apps.size)
                        put("items", kotlinx.serialization.json.buildJsonArray {
                            page.forEach { app -> add(buildJsonObject {
                                put("package_name", app.packageName)
                                put("label", context.packageManager.getApplicationLabel(app).toString())
                                put("system_app", (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                                put("enabled", app.enabled)
                            }) }
                        })
                    }
                }
            else -> error("Unsupported action: $action")
            }
        listOf(UIMessagePart.Text(payload.toString()))
    },
))
