package me.rerere.rikkahub.data.ai.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.RikkaAccessibilityKeeper
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

private fun textResult(name: String, block: () -> JsonObject): List<UIMessagePart> {
    val value = runCatching(block).getOrElse { failure -> buildJsonObject {
        put("ok", false); put("tool", name)
        put("code", failure.message?.takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: "TOOL_FAILED")
        put("message", failure.message ?: "Tool failed")
    } }
    return listOf(UIMessagePart.Text(value.toString()))
}

private fun obj(properties: JsonObject = buildJsonObject {}, required: List<String> = emptyList()) =
    InputSchema.Obj(properties = properties, required = required)

private fun actionResult(tool: String, result: me.rerere.rikkahub.accessibility.AccessibilityActionResult) = buildJsonObject {
    put("ok", result.ok)
    put("tool", tool)
    result.method.takeIf { it.isNotEmpty() }?.let { put("method", it) }
    result.verifiedBy?.let { put("verified_by", it) }
}

fun createAndroidTextSystemTools(
    context: Context,
    requireApproval: Boolean,
    protectionEnabled: Boolean,
    rootController: AndroidRootTerminalController?,
): List<Tool> {
    fun ensure() = RikkaAccessibilityKeeper.ensureAvailable(context, protectionEnabled, rootController)
    fun editProperties(includeMode: Boolean = false) = buildJsonObject {
        put("text", buildJsonObject { put("type", "string") })
        put("index", buildJsonObject { put("type", "integer") })
        put("observation_id", buildJsonObject { put("type", "string") })
        if (includeMode) put("mode", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("append"); add("replace"); add("paste") }) })
    }
    return listOf(
        Tool(
            name = "input_text",
            description = "Type into the genuinely focused editable field. append inserts at the current selection; replace may target an observed editable node; paste uses the guarded paste path.",
            parameters = { obj(editProperties(true), listOf("text")) },
            needsApproval = { requireApproval },
            execute = { input -> textResult("input_text") {
                val o = input.jsonObject; val text = o["text"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT")
                require(text.length <= 1_000) { "TEXT_TOO_LONG" }; ensure()
                val mode = o["mode"]?.jsonPrimitive?.contentOrNull ?: "append"
                val result = when (mode) {
                    "append" -> RikkaAccessibilityService.inputFocused(text)
                    "paste" -> RikkaAccessibilityService.pasteFocused(text)
                    "replace" -> RikkaAccessibilityService.replaceText(o["observation_id"]?.jsonPrimitive?.contentOrNull, o["index"]?.jsonPrimitive?.intOrNull, text)
                    else -> error("INVALID_ARGUMENT")
                }
                actionResult("input_text", result)
            } },
        ),
        Tool(
            name = "replace_text",
            description = "Replace the focused field or a validated editable node from the specified observation.",
            parameters = { obj(editProperties(), listOf("text")) },
            needsApproval = { requireApproval },
            execute = { input -> textResult("replace_text") {
                val o=input.jsonObject; val text=o["text"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT")
                require(text.length <= 4_000) { "TEXT_TOO_LONG" }; ensure()
                val result=RikkaAccessibilityService.replaceText(o["observation_id"]?.jsonPrimitive?.contentOrNull,o["index"]?.jsonPrimitive?.intOrNull,text)
                actionResult("replace_text", result)
            } },
        ),
        Tool(
            name = "clear_text",
            description = "Clear the focused field or a validated editable node from the specified observation.",
            parameters = { obj(buildJsonObject { put("index",buildJsonObject{put("type","integer")});put("observation_id",buildJsonObject{put("type","string")}) }) },
            needsApproval = { requireApproval },
            execute = { input -> textResult("clear_text") {
                val o=input.jsonObject; ensure(); val result=RikkaAccessibilityService.clearText(o["observation_id"]?.jsonPrimitive?.contentOrNull,o["index"]?.jsonPrimitive?.intOrNull)
                actionResult("clear_text", result)
            } },
        ),
        Tool(
            name = "set_clipboard", description = "Write text to the system clipboard.",
            parameters = { obj(buildJsonObject { put("text",buildJsonObject{put("type","string")}) },listOf("text")) },
            needsApproval = { requireApproval }, execute = { input -> textResult("set_clipboard") {
                val text=input.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT")
                require(text.length <= 20_000) { "TEXT_TOO_LONG" }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RikkaHub",text))
                buildJsonObject { put("ok",true);put("tool","set_clipboard");put("length",text.length) }
            } },
        ),
        Tool(
            name = "get_clipboard", description = "Read text from the current system clipboard.",
            parameters = { obj() }, execute = { textResult("get_clipboard") {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text=clipboard.primaryClip?.takeIf { it.itemCount>0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: error("CLIPBOARD_UNAVAILABLE")
                buildJsonObject { put("ok",true);put("tool","get_clipboard");put("text",text.take(8_000));put("truncated",text.length>8_000) }
            } },
        ),
        Tool(
            name = "paste_text", description = "Insert long text into the focused editable field; only falls back to a temporary clipboard paste when direct editing is rejected.",
            parameters = { obj(buildJsonObject { put("text",buildJsonObject{put("type","string")}) },listOf("text")) },
            needsApproval = { requireApproval }, execute = { input -> textResult("paste_text") {
                val text=input.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT")
                require(text.length <= 20_000) { "TEXT_TOO_LONG" };ensure()
                val result=RikkaAccessibilityService.pasteFocused(text);actionResult("paste_text", result)
            } },
        ),
        Tool(
            name = "wait", description = "Wait 100 to 30000 milliseconds for animation or loading.",
            parameters = { obj(buildJsonObject { put("duration_ms",buildJsonObject{put("type","integer")}) }) },
            execute = { input -> textResult("wait") {
                val duration=(input.jsonObject["duration_ms"]?.jsonPrimitive?.longOrNull ?: 1_000L).coerceIn(100L,30_000L)
                Thread.sleep(duration);buildJsonObject { put("ok",true);put("tool","wait");put("duration_ms",duration) }
            } },
        ),
        Tool(
            name = "wait_for_text", description = "Wait until visible text or content description matches without publishing or replacing the actionable observation.",
            parameters = { obj(buildJsonObject {
                put("text",buildJsonObject{put("type","string")});put("timeout_ms",buildJsonObject{put("type","integer")})
                put("include_desc",buildJsonObject{put("type","boolean")});put("match",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("contains");add("exact");add("prefix");add("regex")})})
            },listOf("text")) },
            execute = { input -> textResult("wait_for_text") {
                val o=input.jsonObject;val needle=o["text"]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank()} ?: error("INVALID_ARGUMENT")
                val timeout=(o["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 10_000L).coerceIn(500L,60_000L);val deadline=System.currentTimeMillis()+timeout
                val include=o["include_desc"]?.jsonPrimitive?.booleanOrNull ?: true;val mode=o["match"]?.jsonPrimitive?.contentOrNull ?: "contains"
                ensure();var attempts=0;var match:me.rerere.rikkahub.accessibility.AccessibilityTextMatch?=null
                while(System.currentTimeMillis()<=deadline){attempts++;match=RikkaAccessibilityService.queryText(needle,include,mode);if(match!=null)break;Thread.sleep(350)}
                if(match==null) buildJsonObject{put("ok",false);put("tool","wait_for_text");put("code","TIMEOUT");put("attempts",attempts)}
                else buildJsonObject{put("ok",true);put("tool","wait_for_text");put("attempts",attempts);put("matched_node",buildJsonObject{match.text?.let{put("text",it)};match.contentDescription?.let{put("content_description",it)};match.className?.let{put("class_name",it)};put("actionable",false)})}
            } },
        ),
        Tool(
            name = "wait_for_package", description = "Wait for an Android package to become the active accessibility window.",
            parameters = { obj(buildJsonObject { put("package_name",buildJsonObject{put("type","string")});put("timeout_ms",buildJsonObject{put("type","integer")}) },listOf("package_name")) },
            execute = { input -> textResult("wait_for_package") {
                val o=input.jsonObject;val target=o["package_name"]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank()} ?: error("INVALID_ARGUMENT")
                val timeout=(o["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 10_000L).coerceIn(500L,60_000L);val deadline=System.currentTimeMillis()+timeout
                ensure();var attempts=0;var current:String?=null
                while(System.currentTimeMillis()<=deadline){attempts++;current=RikkaAccessibilityService.currentPackageName();if(current==target)break;Thread.sleep(350)}
                if(current==target) buildJsonObject{put("ok",true);put("tool","wait_for_package");put("package_name",target);put("attempts",attempts)}
                else buildJsonObject{put("ok",false);put("tool","wait_for_package");put("code","TIMEOUT");put("last_package",current ?: "");put("attempts",attempts)}
            } },
        ),
        Tool(
            name = "press_key", description = "Perform a guarded accessibility global action or IME enter/paste action.",
            parameters = { obj(buildJsonObject { put("button",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("BACK");add("HOME");add("ENTER");add("RECENTS");add("PASTE");add("NOTIFICATIONS");add("QUICK_SETTINGS")})}) },listOf("button")) },
            needsApproval = { requireApproval }, execute = { input -> textResult("press_key") {
                val button=input.jsonObject["button"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT");ensure()
                val result=when(button){
                    "BACK"->RikkaAccessibilityService.global("back");"HOME"->RikkaAccessibilityService.global("home")
                    "RECENTS"->RikkaAccessibilityService.global("recents");"NOTIFICATIONS"->RikkaAccessibilityService.global("notifications")
                    "QUICK_SETTINGS"->RikkaAccessibilityService.global("quick_settings");"ENTER"->RikkaAccessibilityService.pressEnter()
                    "PASTE"->{val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;val text=clipboard.primaryClip?.takeIf{it.itemCount>0}?.getItemAt(0)?.coerceToText(context)?.toString() ?: error("CLIPBOARD_UNAVAILABLE");RikkaAccessibilityService.pasteFocused(text)}
                    else->error("INVALID_ARGUMENT")
                }; actionResult("press_key", result)
            } },
        ),
        Tool(
            name = "open_system_panel", description = "Open notifications or quick settings through the accessibility service.",
            parameters = { obj(buildJsonObject { put("panel",buildJsonObject{put("type","string");put("enum",buildJsonArray{add("notifications");add("quick_settings")})}) },listOf("panel")) },
            needsApproval = { requireApproval }, execute = { input -> textResult("open_system_panel") {
                val panel=input.jsonObject["panel"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_ARGUMENT");ensure()
                RikkaAccessibilityService.global(panel);buildJsonObject{put("ok",true);put("tool","open_system_panel");put("panel",panel)}
            } },
        ),
    )
}
