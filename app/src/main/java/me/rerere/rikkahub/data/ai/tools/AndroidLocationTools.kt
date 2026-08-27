package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/** Reads the device's already-known location; it does not start active tracking. */
fun createAndroidLocationTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_location",
    description = "Read the most recent cached device location from Android location providers. Does not continuously track; requires approval and location permission.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("include_altitude", buildJsonObject { put("type", "boolean") })
    }) },
    needsApproval = { true },
    execute = { input ->
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
            put("error", "NO_PERMISSION"); put("message", "Location permission is not granted")
        }.toString()))
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locations = manager.getProviders(true).mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
        }.sortedByDescending { it.time }
        val location = locations.firstOrNull()
        if (location == null) return@Tool listOf(UIMessagePart.Text(buildJsonObject {
            put("error", "NO_CACHED_LOCATION"); put("message", "No cached location is currently available")
        }.toString()))
        val includeAltitude = input.jsonObject["include_altitude"]?.jsonPrimitive?.booleanOrNull == true
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", true); put("latitude", location.latitude); put("longitude", location.longitude)
            put("accuracy_meters", location.accuracy.toDouble()); put("provider", location.provider ?: "")
            put("time_epoch_ms", location.time)
            if (includeAltitude && location.hasAltitude()) put("altitude_meters", location.altitude)
        }.toString()))
    }
))
