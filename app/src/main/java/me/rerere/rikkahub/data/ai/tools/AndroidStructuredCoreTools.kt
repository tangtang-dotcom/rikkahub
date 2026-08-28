package me.rerere.rikkahub.data.ai.tools

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import java.util.Calendar

private fun structuredTool(
    name: String, description: String, properties: JsonObject = buildJsonObject {},
    required: List<String> = emptyList(), approval: Boolean = false,
    executeBlock: (JsonObject) -> JsonObject,
) = Tool(
    name=name,description=description,parameters={InputSchema.Obj(properties=properties,required=required)},
    needsApproval={approval},execute={input->
        val result=runCatching{executeBlock(input.jsonObject)}.getOrElse{e->buildJsonObject{
            put("ok",false);put("tool",name);put("code",e.message?.takeIf{it.matches(Regex("[A-Z0-9_]+"))}?:"TOOL_FAILED");put("message",e.message?:"Tool failed")
        }};listOf(UIMessagePart.Text(result.toString()))
    },
)
private fun limitProps()=buildJsonObject{put("limit",buildJsonObject{put("type","integer")})}
private fun JsonObject.int(name:String,default:Int)=this[name]?.jsonPrimitive?.intOrNull?:default
private fun JsonObject.str(name:String)=this[name]?.jsonPrimitive?.contentOrNull

internal fun topMemoryAppsCommand(limit: Int): String =
    "/system/bin/ps -A -o rss,comm 2>/dev/null | tail -n +2 | sort -nr | head -n ${limit.coerceIn(1, 30)}"

internal fun getSettingCommand(namespace: String, key: String): String =
    "value=\$(/system/bin/settings get $namespace $key 2>/dev/null); " +
        "if [ -n \"\$value\" ] && [ \"\$value\" != null ]; then printf '%s\\n' \"\$value\"; " +
        "else /system/bin/cmd settings get $namespace $key 2>/dev/null; fi"

internal fun normalizeSettingValue(raw: String): String? = raw.lineSequence()
    .map(String::trim)
    .lastOrNull { it.isNotEmpty() && it != "null" }

internal fun setDeviceStateCommand(target: String, enabled: Boolean): String = when (target) {
    "wifi" -> "/system/bin/cmd wifi set-wifi-enabled ${if (enabled) "enabled" else "disabled"}"
    "bluetooth" -> "/system/bin/cmd bluetooth_manager ${if (enabled) "enable" else "disable"}"
    else -> error("INVALID_ARGUMENT")
}

internal fun waitForDeviceState(context: Context, target: String, expected: Boolean): Boolean {
    fun current(): Boolean = when (target) {
        "wifi" -> (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        "bluetooth" -> android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        else -> error("INVALID_ARGUMENT")
    }
    repeat(20) {
        val value = current()
        if (value == expected) return value
        Thread.sleep(250)
    }
    return current()
}

fun createAndroidStructuredCoreTools(context:Context,root:AndroidRootTerminalController):List<Tool>{
    val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun stream(value:String?)=when(value){"alarm"->AudioManager.STREAM_ALARM;"ring"->AudioManager.STREAM_RING;"notification"->AudioManager.STREAM_NOTIFICATION;else->AudioManager.STREAM_MUSIC}
    return listOf(
        structuredTool("set_alarm","Create a system alarm directly.",buildJsonObject{
            put("hour",buildJsonObject{put("type","integer")});put("minute",buildJsonObject{put("type","integer")});put("label",buildJsonObject{put("type","string")});put("repeat_days",buildJsonObject{put("type","array");put("items",buildJsonObject{put("type","string")})});put("vibrate",buildJsonObject{put("type","boolean")})
        },listOf("hour","minute"),true){o->
            val hour=o.int("hour",-1);val minute=o.int("minute",-1);require(hour in 0..23&&minute in 0..59){"INVALID_ARGUMENT"}
            val map=mapOf("sun" to Calendar.SUNDAY,"mon" to Calendar.MONDAY,"tue" to Calendar.TUESDAY,"wed" to Calendar.WEDNESDAY,"thu" to Calendar.THURSDAY,"fri" to Calendar.FRIDAY,"sat" to Calendar.SATURDAY)
            val days=ArrayList<Int>();o["repeat_days"]?.jsonArray?.forEach{map[it.jsonPrimitive.content]?.let(days::add)}
            val intent=Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AlarmClock.EXTRA_HOUR,hour).putExtra(AlarmClock.EXTRA_MINUTES,minute).putExtra(AlarmClock.EXTRA_SKIP_UI,true).putExtra(AlarmClock.EXTRA_VIBRATE,o["vibrate"]?.jsonPrimitive?.booleanOrNull?:true)
            o.str("label")?.take(100)?.let{intent.putExtra(AlarmClock.EXTRA_MESSAGE,it)};if(days.isNotEmpty())intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS,days)
            require(intent.resolveActivity(context.packageManager)!=null){"CLOCK_UNAVAILABLE"};context.startActivity(intent);buildJsonObject{put("ok",true);put("tool","set_alarm");put("hour",hour);put("minute",minute)}
        },
        structuredTool("set_timer","Create a system countdown timer directly.",buildJsonObject{put("duration_seconds",buildJsonObject{put("type","integer")});put("label",buildJsonObject{put("type","string")})},listOf("duration_seconds"),true){o->
            val duration=o.int("duration_seconds",0);require(duration in 1..86400){"INVALID_ARGUMENT"};val intent=Intent(AlarmClock.ACTION_SET_TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AlarmClock.EXTRA_LENGTH,duration).putExtra(AlarmClock.EXTRA_SKIP_UI,true);o.str("label")?.take(100)?.let{intent.putExtra(AlarmClock.EXTRA_MESSAGE,it)};require(intent.resolveActivity(context.packageManager)!=null){"CLOCK_UNAVAILABLE"};context.startActivity(intent);buildJsonObject{put("ok",true);put("tool","set_timer");put("duration_seconds",duration)}
        },
        structuredTool("device_status","Read battery, memory, storage, Android version, and uptime."){_->
            val battery=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager;val memory=ActivityManager.MemoryInfo().also{(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)};val storage=StatFs(context.filesDir.absolutePath)
            buildJsonObject{put("ok",true);put("tool","device_status");put("manufacturer",Build.MANUFACTURER);put("model",Build.MODEL);put("android_release",Build.VERSION.RELEASE?:"");put("sdk",Build.VERSION.SDK_INT);put("battery_percent",battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));put("memory_total_bytes",memory.totalMem);put("memory_available_bytes",memory.availMem);put("storage_total_bytes",storage.totalBytes);put("storage_available_bytes",storage.availableBytes);put("uptime_ms",SystemClock.elapsedRealtime())}
        },
        structuredTool("network_info","Read active network validation and Wi-Fi metadata."){_->
            val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val caps=manager.activeNetwork?.let(manager::getNetworkCapabilities);val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            buildJsonObject{put("ok",true);put("tool","network_info");put("connected",caps!=null);put("validated",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true);put("wifi",caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true);put("cellular",caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true);put("ethernet",caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)==true);put("wifi_enabled",wifi.isWifiEnabled);runCatching{wifi.connectionInfo}.getOrNull()?.let{put("ssid",it.ssid?.removeSurrounding("\"")?:"");put("bssid",it.bssid?:"");put("rssi",it.rssi);put("link_speed_mbps",it.linkSpeed)}}
        },
        structuredTool("top_memory_apps","List processes by current RSS.",limitProps()){o->
            val limit=o.int("limit",10).coerceIn(1,30);val r=root.executeSync(topMemoryAppsCommand(limit),timeoutMs=10_000);if(r.exitCode!=0||r.timedOut)error("ROOT_UNAVAILABLE");val items=buildJsonArray{r.stdout.lineSequence().filter{it.isNotBlank()}.forEach{line->val p=line.trim().split(Regex("\\s+"),limit=2);if(p.size==2)add(buildJsonObject{put("rss_kb",p[0].toLongOrNull()?:0);put("process",p[1])})}};buildJsonObject{put("ok",true);put("tool","top_memory_apps");put("items",items);put("count",items.size)}
        },
        structuredTool("top_storage_apps","List app private storage use.",limitProps()){o->
            val limit=o.int("limit",10).coerceIn(1,30);val r=root.executeSync("du -sk /data/user/0/* 2>/dev/null | sort -nr | head -n $limit",timeoutMs=30_000);if(r.exitCode!=0||r.timedOut)error("ROOT_UNAVAILABLE");val items=buildJsonArray{r.stdout.lineSequence().filter{it.isNotBlank()}.forEach{line->val p=line.trim().split(Regex("\\s+"),limit=2);if(p.size==2)add(buildJsonObject{put("total_kb",p[0].toLongOrNull()?:0);put("package_name",p[1].substringAfterLast('/'))})}};buildJsonObject{put("ok",true);put("tool","top_storage_apps");put("items",items);put("count",items.size)}
        },
        structuredTool("media_control","Control the current media session.",buildJsonObject{put("action",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("play");add("pause");add("play_pause");add("next");add("previous");add("stop")})})},listOf("action"),true){o->
            val key=when(o.str("action")){"play"->KeyEvent.KEYCODE_MEDIA_PLAY;"pause"->KeyEvent.KEYCODE_MEDIA_PAUSE;"play_pause"->KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;"next"->KeyEvent.KEYCODE_MEDIA_NEXT;"previous"->KeyEvent.KEYCODE_MEDIA_PREVIOUS;"stop"->KeyEvent.KEYCODE_MEDIA_STOP;else->error("INVALID_ARGUMENT")};audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));buildJsonObject{put("ok",true);put("tool","media_control");put("action",o.str("action")?:"")}
        },
        structuredTool("set_volume","Set a system audio stream percentage.",buildJsonObject{put("stream",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("media");add("alarm");add("ring");add("notification")})});put("percent",buildJsonObject{put("type","integer")})},listOf("stream","percent"),true){o->
            val percent=o.int("percent",-1);require(percent in 0..100){"INVALID_ARGUMENT"};val channel=stream(o.str("stream"));val index=(audio.getStreamMaxVolume(channel)*percent/100.0).toInt();audio.setStreamVolume(channel,index,0);buildJsonObject{put("ok",true);put("tool","set_volume");put("stream",o.str("stream")?:"media");put("percent",percent);put("index",index)}
        },
        structuredTool("get_setting","Read an exact Android Settings value.",buildJsonObject{put("namespace",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("system");add("secure");add("global")})});put("key",buildJsonObject{put("type","string")})},listOf("namespace","key")){o->
            val key=o.str("key")?.takeIf{it.matches(Regex("[A-Za-z0-9_.-]{1,200}"))}?:error("INVALID_ARGUMENT");val namespace=o.str("namespace")?:error("INVALID_ARGUMENT");val frameworkValue=when(namespace){"system"->Settings.System.getString(context.contentResolver,key);"secure"->Settings.Secure.getString(context.contentResolver,key);"global"->if(key=="airplane_mode_on") Settings.Global.getInt(context.contentResolver,key,0).toString() else Settings.Global.getString(context.contentResolver,key);else->error("INVALID_ARGUMENT")};val rootResult=if(frameworkValue==null)root.executeSync(getSettingCommand(namespace,key),timeoutMs=10_000)else null;val value=frameworkValue?:rootResult?.takeIf{it.exitCode==0&&!it.timedOut}?.stdout?.let(::normalizeSettingValue);buildJsonObject{put("ok",true);put("tool","get_setting");put("namespace",namespace);put("key",key);put("source",if(frameworkValue!=null)"framework" else if(value!=null)"root" else "unavailable");value?.let{put("value",it)}?:put("value",JsonNull)}
        },
        structuredTool("set_setting","Modify a non-critical Android Settings value.",buildJsonObject{put("namespace",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("system");add("secure");add("global")})});put("key",buildJsonObject{put("type","string")});put("value",buildJsonObject{put("type","string")})},listOf("namespace","key","value"),true){o->
            val key=o.str("key")?.takeIf{it.matches(Regex("[A-Za-z0-9_.-]{1,200}"))}?:error("INVALID_ARGUMENT");require(key !in setOf("enabled_accessibility_services","accessibility_enabled","adb_enabled","device_provisioned","user_setup_complete")){"SETTING_PROTECTED"};val value=o.str("value")?:error("INVALID_ARGUMENT");val command="/system/bin/settings put ${o.str("namespace")} '$key' '${value.replace("'","'\\''")}'";val r=root.executeSync(command,timeoutMs=10_000);if(r.exitCode!=0||r.timedOut)error("SETTING_WRITE_FAILED");buildJsonObject{put("ok",true);put("tool","set_setting");put("namespace",o.str("namespace")?:"");put("key",key)}
        },
        structuredTool("set_device_state","Enable or disable Wi-Fi or Bluetooth.",buildJsonObject{put("target",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("wifi");add("bluetooth")})});put("enabled",buildJsonObject{put("type","boolean")})},listOf("target","enabled"),true){o->
            val enabled=o["enabled"]?.jsonPrimitive?.booleanOrNull?:error("INVALID_ARGUMENT");val target=o.str("target")?:error("INVALID_ARGUMENT");val command=setDeviceStateCommand(target,enabled);val r=root.executeSync(command,timeoutMs=10_000);if(r.exitCode!=0||r.timedOut)error("DEVICE_STATE_CHANGE_FAILED");val observed=waitForDeviceState(context,target,enabled);if(observed!=enabled)error("DEVICE_STATE_NOT_CONFIRMED");buildJsonObject{put("ok",true);put("tool","set_device_state");put("target",target);put("enabled",enabled);put("observed_enabled",observed);put("verified",true)}
        },
        structuredTool("app_state_control","Force-stop, freeze, or unfreeze a non-core package.",buildJsonObject{put("package_name",buildJsonObject{put("type","string")});put("action",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("force_stop");add("freeze");add("unfreeze")})})},listOf("package_name","action"),true){o->
            val pkg=o.str("package_name")?.takeIf{it.matches(Regex("[A-Za-z0-9_.]+"))}?:error("INVALID_ARGUMENT");require(pkg!=context.packageName&&pkg !in setOf("android","com.android.systemui","com.android.settings")){"PACKAGE_PROTECTED"};val action=o.str("action")?:error("INVALID_ARGUMENT");val command=when(action){"force_stop"->"am force-stop '$pkg'";"freeze"->"pm disable-user --user 0 '$pkg'";"unfreeze"->"pm enable --user 0 '$pkg'";else->error("INVALID_ARGUMENT")};val r=root.executeSync(command,timeoutMs=15_000);if(r.exitCode!=0||r.timedOut)error("APP_STATE_CHANGE_FAILED");buildJsonObject{put("ok",true);put("tool","app_state_control");put("package_name",pkg);put("action",action)}
        },
    )
}
