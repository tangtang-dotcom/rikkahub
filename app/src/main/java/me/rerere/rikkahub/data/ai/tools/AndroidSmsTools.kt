package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidSmsTools(context: Context): List<Tool> = listOf(Tool(
    name = "android_sms_codes",
    description = "Extract recent 4-8 digit verification codes from SMS metadata/body. Highly sensitive; requires approval and READ_SMS permission.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("max_age_minutes", buildJsonObject { put("type", "integer") })
        put("limit", buildJsonObject { put("type", "integer") })
    }) },
    needsApproval = { true },
    execute = { input ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "NO_PERMISSION"); put("message", "READ_SMS permission is not granted")
            }.toString()))
        }
        val o=input.jsonObject
        val cutoff=System.currentTimeMillis()-(o["max_age_minutes"]?.jsonPrimitive?.longOrNull ?: 10L).coerceIn(1,1440)*60_000L
        val limit=(o["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1,50)
        val regex=Regex("(?<!\\d)\\d{4,8}(?!\\d)")
        val rows=buildJsonArray {
            context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",arrayOf(cutoff.toString()),"${Telephony.Sms.DATE} DESC")?.use { c ->
                var n=0; while(c.moveToNext()&&n<limit){ val body=c.getString(1)?:""; val match=regex.find(body); if(match!=null){n++;add(buildJsonObject{put("sender",c.getString(0)?:"");put("code",match.value);put("date_epoch_ms",c.getLong(2))})} }
            }
        }
        listOf(UIMessagePart.Text(buildJsonObject { put("count",rows.size);put("items",rows) }.toString()))
    }
))
