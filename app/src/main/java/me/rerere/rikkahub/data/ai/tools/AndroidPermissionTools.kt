package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidPermissionTools(context: Context): List<Tool> = listOf(Tool(
 name="android_permission_status", description="Read statuses of permissions used by Android agent tools; does not request or change them.",
 parameters={InputSchema.Obj(properties=buildJsonObject{put("permissions",buildJsonObject{put("type","array");put("items",buildJsonObject{put("type","string")})})})},
 needsApproval={false}, execute={input->
  val ps=input.jsonObject["permissions"]?.jsonArray?.mapNotNull{it.jsonPrimitive.contentOrNull} ?: listOf(Manifest.permission.READ_CALENDAR,Manifest.permission.READ_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_SMS,Manifest.permission.WRITE_CALENDAR,Manifest.permission.POST_NOTIFICATIONS)
  val items=ps.distinct().take(30).map{p->buildJsonObject{put("permission",p);put("granted",ContextCompat.checkSelfPermission(context,p)==PackageManager.PERMISSION_GRANTED)}}
  val usage=try{val ops=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager;@Suppress("DEPRECATION") val mode=ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName);mode==AppOpsManager.MODE_ALLOWED}catch(_:Throwable){false}
  listOf(UIMessagePart.Text(buildJsonObject{put("items",buildJsonArray{items.forEach{add(it)}});put("usage_access_granted",usage);put("write_settings_granted",Settings.System.canWrite(context))}.toString()))
 }
))
