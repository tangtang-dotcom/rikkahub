package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.provider.MediaStore
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidMediaIndexTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_media_index",
    description = "List bounded metadata for user-visible images, audio, video, or downloads. Does not open or modify files.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("kind", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("images")); add(JsonPrimitive("audio")); add(JsonPrimitive("video")); add(JsonPrimitive("downloads")) }) })
        put("query", buildJsonObject { put("type", "string") }); put("limit", buildJsonObject { put("type", "integer") })
    }, required = listOf("kind")) },
    needsApproval = { true }, execute = { input ->
        val o=input.jsonObject; val kind=o["kind"]?.jsonPrimitive?.contentOrNull ?: error("kind is required")
        val limit=(o["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1,100); val q=o["query"]?.jsonPrimitive?.contentOrNull
        val uri=when(kind){"images"->MediaStore.Images.Media.EXTERNAL_CONTENT_URI;"audio"->MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;"video"->MediaStore.Video.Media.EXTERNAL_CONTENT_URI;"downloads"->MediaStore.Downloads.EXTERNAL_CONTENT_URI;else->error("Unsupported kind: $kind")}
        val rows=buildJsonArray { context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_MODIFIED),q?.let{"${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"},q?.let{arrayOf("%$it%")} ,"${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use{c->var n=0;while(c.moveToNext()&&n++<limit)add(buildJsonObject{put("name",c.getString(0)?:"");put("mime_type",c.getString(1)?:"");put("size_bytes",c.getLong(2));put("modified_epoch_seconds",c.getLong(3))})} }
        listOf(UIMessagePart.Text(buildJsonObject { put("kind",kind);put("count",rows.size);put("items",rows) }.toString()))
    }
))
