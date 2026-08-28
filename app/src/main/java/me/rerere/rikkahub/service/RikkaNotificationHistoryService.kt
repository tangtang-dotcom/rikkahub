package me.rerere.rikkahub.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RikkaNotificationHistoryService:NotificationListenerService(){
    override fun onNotificationPosted(sbn:StatusBarNotification?){
        val item=sbn?:return;val extras=item.notification.extras
        RikkaNotificationHistoryStore.append(this,JSONObject().put("package_name",item.packageName).put("posted_at",item.postTime).put("title",extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()).put("text",extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).put("sub_text",extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()))
    }
}

object RikkaNotificationHistoryStore{
    private val lock=Any();private const val MAX_ITEMS=1000;private const val MAX_AGE_MS=7L*24*60*60*1000
    fun isEnabled(context:Context)=NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    fun append(context:Context,item:JSONObject)=synchronized(lock){
        val now=System.currentTimeMillis();val old=read(context);val next=JSONArray().put(item);for(i in 0 until old.length()){val value=old.optJSONObject(i)?:continue;if(value.optLong("posted_at")>=now-MAX_AGE_MS&&next.length()<MAX_ITEMS)next.put(value)};write(context,next)
    }
    fun search(context:Context,query:String,packageName:String,maxAgeHours:Int,limit:Int):String=synchronized(lock){
        if(!isEnabled(context))return@synchronized JSONObject().put("ok",false).put("tool","search_notification_history").put("code","NOTIFICATION_HISTORY_ACCESS_REQUIRED").toString()
        val cutoff=System.currentTimeMillis()-maxAgeHours.coerceIn(1,168)*3_600_000L;val items=JSONArray();val source=read(context);for(i in 0 until source.length()){if(items.length()>=limit.coerceIn(1,50))break;val value=source.optJSONObject(i)?:continue;if(value.optLong("posted_at")<cutoff)continue;if(packageName.isNotBlank()&&value.optString("package_name")!=packageName)continue;val text=listOf("title","text","sub_text").joinToString(" "){value.optString(it)};if(query.isNotBlank()&&!text.contains(query,true))continue;items.put(value)};JSONObject().put("ok",true).put("tool","search_notification_history").put("items",items).put("count",items.length()).toString()
    }
    private fun file(context:Context)=File(context.filesDir,"agent-notification-history.json")
    private fun read(context:Context)=runCatching{JSONArray(file(context).readText())}.getOrDefault(JSONArray())
    private fun write(context:Context,items:JSONArray){val target=file(context);val temp=File(target.parentFile,target.name+".tmp");temp.writeText(items.toString());if(!temp.renameTo(target)){target.writeText(items.toString());temp.delete()}}
}
