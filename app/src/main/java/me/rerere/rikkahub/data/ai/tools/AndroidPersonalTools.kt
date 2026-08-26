package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.Manifest
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidPersonalTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_calendar_events",
    description = "Read bounded calendar event metadata; exposes personal data and requires approval.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("from_epoch_ms", buildJsonObject { put("type", "integer") })
        put("to_epoch_ms", buildJsonObject { put("type", "integer") })
        put("offset", buildJsonObject { put("type", "integer") })
        put("limit", buildJsonObject { put("type", "integer") })
    }) },
    needsApproval = { true },
    execute = { input ->
        val obj = input.jsonObject
        val from = obj["from_epoch_ms"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        val to = obj["to_epoch_ms"]?.jsonPrimitive?.longOrNull ?: from + 30L * 86400000
        require(to > from) { "to_epoch_ms must be after from_epoch_ms" }
        val offset = (obj["offset"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
        val limit = (obj["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1, 100)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        val rows = mutableListOf<JsonObject>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            val id=c.getColumnIndex(CalendarContract.Instances.EVENT_ID); val title=c.getColumnIndex(CalendarContract.Instances.TITLE)
            val location=c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION); val begin=c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val end=c.getColumnIndex(CalendarContract.Instances.END); val allDay=c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (c.moveToNext()) rows += buildJsonObject {
                put("event_id", c.getLong(id)); put("title", c.getString(title) ?: "")
                put("location", c.getString(location) ?: ""); put("begin_epoch_ms", c.getLong(begin))
                put("end_epoch_ms", c.getLong(end)); put("all_day", c.getInt(allDay) != 0)
            }
        }
        val page = rows.drop(offset).take(limit)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("from_epoch_ms", from); put("to_epoch_ms", to); put("offset", offset); put("limit", limit)
            put("total", rows.size); put("has_more", offset + page.size < rows.size)
            put("items", buildJsonArray { page.forEach { add(it) } })
        }.toString()))
    },
),
Tool(
        name = "android_contacts",
        description = "Read bounded contact names and phone numbers; exposes personal data and requires approval.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {
            put("query", buildJsonObject { put("type", "string") })
            put("limit", buildJsonObject { put("type", "integer") })
        }) },
        needsApproval = { true },
        execute = { input ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "NO_PERMISSION"); put("message", "READ_CONTACTS permission is not granted")
                }.toString()))
            }
            val query = input.jsonObject["query"]?.jsonPrimitive?.contentOrNull
            val limit = (input.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1, 100)
            val rows = mutableListOf<JsonObject>()
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                query?.let { "${ContactsContract.Data.DISPLAY_NAME} LIKE ?" }, query?.let { arrayOf("%$it%") },
                "${ContactsContract.Data.DISPLAY_NAME} ASC")?.use { c ->
                while (c.moveToNext() && rows.size < limit) rows += buildJsonObject {
                    put("name", c.getString(0) ?: ""); put("phone", c.getString(1) ?: "")
                }
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("query", query ?: ""); put("count", rows.size)
                put("items", buildJsonArray { rows.forEach { add(it) } })
            }.toString()))
        },
    ),
))
