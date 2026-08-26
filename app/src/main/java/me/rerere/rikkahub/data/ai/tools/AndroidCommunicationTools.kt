package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidCommunicationTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_call_history",
    description = "Read bounded recent call-log metadata. Personal data access requires approval and READ_CALL_LOG permission.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("query", buildJsonObject { put("type", "string") })
        put("limit", buildJsonObject { put("type", "integer") })
    }) },
    needsApproval = { true },
    execute = { input ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "NO_PERMISSION"); put("message", "READ_CALL_LOG permission is not granted")
            }.toString()))
        }
        val o = input.jsonObject
        val query = o["query"]?.jsonPrimitive?.contentOrNull
        val limit = (o["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1, 100)
        val rows = buildJsonArray {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                query?.let { "${CallLog.Calls.NUMBER} LIKE ? OR ${CallLog.Calls.CACHED_NAME} LIKE ?" },
                query?.let { arrayOf("%$it%", "%$it%") },
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count++ < limit) add(buildJsonObject {
                    put("number", cursor.getString(0) ?: "")
                    put("name", cursor.getString(1) ?: "")
                    put("type", cursor.getInt(2))
                    put("date_epoch_ms", cursor.getLong(3))
                    put("duration_seconds", cursor.getLong(4))
                })
            }
        }
        listOf(UIMessagePart.Text(buildJsonObject {
            put("query", query ?: ""); put("count", rows.size); put("items", rows)
        }.toString()))
    },
))
