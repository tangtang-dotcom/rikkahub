package me.rerere.rikkahub.data.ai.tools

import android.app.AlarmClock
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

fun createAndroidSchedulingTools(context: Context): List<Tool> = listOf(Tool(
 name="android_schedule", description="Create a system alarm or countdown timer through Clock UI; requires approval.",
 parameters={InputSchema.Obj(properties=buildJsonObject{
  put("kind",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("alarm");add("timer")})})
  put("hour",buildJsonObject{put("type","integer")});put("minute",buildJsonObject{put("type","integer")})
  put("duration_seconds",buildJsonObject{put("type","integer")});put("message",buildJsonObject{put("type","string")})
 },required=listOf("kind"))}, needsApproval={true}, execute={input->
  val o=input.jsonObject;val kind=o["kind"]?.jsonPrimitive?.contentOrNull?:error("kind is required")
  val i=Intent(when(kind){"alarm"->AlarmClock.ACTION_SET_ALARM;"timer"->AlarmClock.ACTION_SET_TIMER;else->error("Unsupported kind: $kind")}).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  if(kind=="alarm"){i.putExtra(AlarmClock.EXTRA_HOUR,(o["hour"]?.jsonPrimitive?.intOrNull?:error("hour is required")).coerceIn(0,23));i.putExtra(AlarmClock.EXTRA_MINUTES,(o["minute"]?.jsonPrimitive?.intOrNull?:error("minute is required")).coerceIn(0,59))}
  else i.putExtra(AlarmClock.EXTRA_LENGTH,(o["duration_seconds"]?.jsonPrimitive?.intOrNull?:error("duration_seconds is required")).coerceIn(1,86400))
  o["message"]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank()}?.let{i.putExtra(AlarmClock.EXTRA_MESSAGE,it.take(100))}
  require(i.resolveActivity(context.packageManager)!=null){"No clock application can handle $kind"};context.startActivity(i)
  listOf(UIMessagePart.Text(buildJsonObject{put("ok",true);put("kind",kind);put("opened_clock_ui",true)}.toString()))
 }
))
