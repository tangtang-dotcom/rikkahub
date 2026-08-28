package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.app.*
import android.app.usage.*
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import me.rerere.rikkahub.service.RikkaNotificationHistoryStore
import org.json.JSONObject

private fun contextTool(name:String,description:String,properties:JsonObject=buildJsonObject{},block:(JsonObject)->String)=Tool(
    name=name,description=description,parameters={InputSchema.Obj(properties=properties)},execute={input->
        val out=runCatching{block(input.jsonObject)}.getOrElse{e->JSONObject().put("ok",false).put("tool",name).put("code",e.message?:"TOOL_FAILED").toString()};listOf(UIMessagePart.Text(out))
    }
)
private fun searchProperties()=buildJsonObject{put("query",buildJsonObject{put("type","string")});put("package_name",buildJsonObject{put("type","string")});put("max_age_hours",buildJsonObject{put("type","integer")});put("limit",buildJsonObject{put("type","integer")})}
private fun quote(value:String)="'"+value.replace("'","'\\''")+"'"

fun createAndroidStructuredContextTools(context:Context,root:AndroidRootTerminalController):List<Tool>{
    fun requireUsageStatsAccess(){
        val appOps=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode=appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)
        if(mode!=AppOpsManager.MODE_ALLOWED) error("USAGE_STATS_ACCESS_REQUIRED")
    }
    return listOf(
        contextTool("wifi_credentials","Read saved Wi-Fi SSIDs and PSKs from Android's protected Wi-Fi configuration.",buildJsonObject{put("ssid",buildJsonObject{put("type","string")});put("limit",buildJsonObject{put("type","integer")})}){o->
            val r=root.executeSync("cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",timeoutMs=15_000);if(r.exitCode!=0||r.timedOut)error("WIFI_CREDENTIALS_UNAVAILABLE");val requested=o["ssid"]?.jsonPrimitive?.contentOrNull?.trim()?.trim('"').orEmpty();val limit=(o["limit"]?.jsonPrimitive?.intOrNull?:20).coerceIn(1,50);val blocks=Regex("<Network>(.*?)</Network>",RegexOption.DOT_MATCHES_ALL).findAll(r.stdout);val items=org.json.JSONArray();val seen=hashSetOf<String>();for(m in blocks){val b=m.value;val ssid=Regex("name=\"SSID\"[^>]*value=\"([^\"]*)\"").find(b)?.groupValues?.get(1)?.replace("&quot;","\"")?.trim('"')?:continue;if(requested.isNotBlank()&&!ssid.equals(requested,true)||!seen.add(ssid.lowercase()))continue;val psk=Regex("name=\"PreSharedKey\"[^>]*value=\"([^\"]*)\"").find(b)?.groupValues?.get(1)?.replace("&quot;","\"")?.trim('"');items.put(JSONObject().put("ssid",ssid).put("password",psk?:JSONObject.NULL));if(items.length()>=limit)break};JSONObject().put("ok",true).put("tool","wifi_credentials").put("items",items).put("count",items.length()).toString()
        },
        contextTool("recent_notifications","Read current notification title/text through Android's root notification command.",buildJsonObject{put("package_name",buildJsonObject{put("type","string")});put("limit",buildJsonObject{put("type","integer")})}){o->
            val limit=(o["limit"]?.jsonPrimitive?.intOrNull?:10).coerceIn(1,20)
            val filter=o["package_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val listed=root.executeSync("cmd notification list",timeoutMs=10_000)
            if(listed.exitCode!=0||listed.timedOut)error("NOTIFICATION_ACCESS_UNAVAILABLE")
            val items=org.json.JSONArray()
            fun extra(source:String,name:String)=Regex("$name=(?:String \\()?(.*?)(?:\\)|,|$)").find(source)?.groupValues?.get(1).orEmpty()
            for(key in listed.stdout.lineSequence().map(String::trim).filter{it.isNotBlank()}){
                if(items.length()>=limit)break
                val detail=root.executeSync("cmd notification get ${quote(key)}",timeoutMs=5_000)
                if(detail.exitCode!=0)continue
                val pkg=Regex("pkg=([^\\s]+)").find(detail.stdout)?.groupValues?.get(1).orEmpty()
                if(filter.isNotBlank()&&pkg!=filter)continue
                items.put(JSONObject().put("package_name",pkg).put("title",extra(detail.stdout,"android.title")).put("text",extra(detail.stdout,"android.text")).put("sub_text",extra(detail.stdout,"android.subText")))
            }
            JSONObject().put("ok",true).put("tool","recent_notifications").put("items",items).put("count",items.length()).toString()
        },
        contextTool("recent_app_activity","Read recent foreground activity transitions from UsageStats.",searchProperties()){o->
            requireUsageStatsAccess();val hours=(o["max_age_hours"]?.jsonPrimitive?.intOrNull?:24).coerceIn(1,168);val limit=(o["limit"]?.jsonPrimitive?.intOrNull?:20).coerceIn(1,50);val filter=o["package_name"]?.jsonPrimitive?.contentOrNull.orEmpty();val end=System.currentTimeMillis();val events=(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(end-hours*3_600_000L,end);val rows=ArrayDeque<JSONObject>();val event=UsageEvents.Event();while(events.hasNextEvent()){events.getNextEvent(event);if(event.eventType!=UsageEvents.Event.ACTIVITY_RESUMED||(filter.isNotBlank()&&event.packageName!=filter))continue;rows.addFirst(JSONObject().put("package_name",event.packageName).put("activity",event.className).put("resumed_at",event.timeStamp));while(rows.size>limit)rows.removeLast()};JSONObject().put("ok",true).put("tool","recent_app_activity").put("items",org.json.JSONArray(rows)).put("count",rows.size).put("window_hours",hours).toString()
        },
        contextTool("app_usage_summary","Summarize foreground time by package from UsageStats.",searchProperties()){o->
            requireUsageStatsAccess();val hours=(o["max_age_hours"]?.jsonPrimitive?.intOrNull?:24).coerceIn(1,168);val limit=(o["limit"]?.jsonPrimitive?.intOrNull?:20).coerceIn(1,50);val end=System.currentTimeMillis();val stats=(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryUsageStats(UsageStatsManager.INTERVAL_DAILY,end-hours*3_600_000L,end).filter{it.totalTimeInForeground>0}.sortedByDescending{it.totalTimeInForeground}.take(limit);val items=org.json.JSONArray();stats.forEach{items.put(JSONObject().put("package_name",it.packageName).put("foreground_ms",it.totalTimeInForeground).put("last_time_used",it.lastTimeUsed))};JSONObject().put("ok",true).put("tool","app_usage_summary").put("items",items).put("count",items.length()).put("window_hours",hours).toString()
        },
        contextTool("get_current_location","Read the newest cached Android location without starting tracking."){_->
            val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!fine&&!coarse)error("LOCATION_PERMISSION_REQUIRED");val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager;val location=manager.getProviders(true).mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.time}?:error("NO_CACHED_LOCATION");JSONObject().put("ok",true).put("tool","get_current_location").put("latitude",location.latitude).put("longitude",location.longitude).put("accuracy_meters",location.accuracy).put("provider",location.provider).put("timestamp_ms",location.time).toString()
        },
        contextTool("get_device_environment","Read lock, DND, ring, audio-output, and external-display state."){_->
            val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager;val notification=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager;val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager;val displays=(context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).displays;JSONObject().put("ok",true).put("tool","get_device_environment").put("interactive",power.isInteractive).put("keyguard_locked",keyguard.isKeyguardLocked).put("interruption_filter",notification.currentInterruptionFilter).put("ringer_mode",audio.ringerMode).put("music_active",audio.isMusicActive).put("wired_output",audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any{it.type in setOf(3,4,11,22)}).put("external_display_count",displays.count{it.displayId!=0}).toString()
        },
        contextTool("read_sms_code","Extract recent 4-8 digit verification codes without returning full SMS bodies.",buildJsonObject{put("max_age_minutes",buildJsonObject{put("type","integer")})}){o->
            val minutes=(o["max_age_minutes"]?.jsonPrimitive?.intOrNull?:10).coerceIn(1,1440);val r=root.executeSync("content query --uri content://sms/inbox --projection address:body:date --sort 'date DESC'",timeoutMs=15_000);if(r.exitCode!=0||r.timedOut)error("SMS_ACCESS_UNAVAILABLE");val cutoff=System.currentTimeMillis()-minutes*60_000L;val items=org.json.JSONArray();val code=Regex("(?<!\\d)(\\d{4,8})(?!\\d)");for(line in r.stdout.lineSequence()){if(items.length()>=10)break;val date=Regex("date=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?:continue;if(date<cutoff)continue;val body=Regex("body=(.*?)(?=,\\s*date=|$)").find(line)?.groupValues?.get(1).orEmpty();val value=code.find(body)?.groupValues?.get(1)?:continue;val sender=Regex("address=(.*?)(?=,\\s*body=|$)").find(line)?.groupValues?.get(1).orEmpty();items.put(JSONObject().put("code",value).put("sender",sender).put("timestamp_ms",date))};JSONObject().put("ok",true).put("tool","read_sms_code").put("items",items).put("count",items.length()).toString()
        },
        contextTool("get_logcat","Read a bounded recent logcat snapshot and optionally filter already-read lines.",buildJsonObject{put("query",buildJsonObject{put("type","string")});put("max_lines",buildJsonObject{put("type","integer")})}){o->
            val max=(o["max_lines"]?.jsonPrimitive?.intOrNull?:200).coerceIn(20,500);val query=o["query"]?.jsonPrimitive?.contentOrNull.orEmpty();val r=root.executeSync("logcat -d -v threadtime -t $max",timeoutMs=15_000);if(r.exitCode!=0||r.timedOut)error("LOGCAT_UNAVAILABLE");val lines=r.stdout.lineSequence().filter{query.isBlank()||it.contains(query,true)}.take(max).toList();JSONObject().put("ok",true).put("tool","get_logcat").put("lines",org.json.JSONArray(lines)).put("count",lines.size).put("truncated",r.truncated).toString()
        },
        contextTool("search_notification_history","Search up to seven days of notifications captured after notification-listener access was granted.",searchProperties()){o->RikkaNotificationHistoryStore.search(context,o["query"]?.jsonPrimitive?.contentOrNull.orEmpty(),o["package_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),o["max_age_hours"]?.jsonPrimitive?.intOrNull?:24,o["limit"]?.jsonPrimitive?.intOrNull?:20)},
    )
}
