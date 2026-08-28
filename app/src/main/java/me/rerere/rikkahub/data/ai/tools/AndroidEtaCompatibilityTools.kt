package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.accessibility.AccessibilityScreenshot
import me.rerere.rikkahub.accessibility.RikkaAccessibilityKeeper
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

internal object EtaCompatibilityToolNames {
    const val OBSERVE_SCREEN = "observe_screen"
    const val SEARCH_APPS = "search_apps"
    const val CURRENT_CONTEXT = "get_current_context"
    const val LAUNCH_APP = "launch_app"
    const val OPEN_URI = "open_uri"
    const val READ_IMAGE = "read_image"
    const val TAP = "tap"
    const val TAP_AREA = "tap_area"
    const val TAP_ELEMENT = "tap_element"
    const val LONG_PRESS = "long_press"
    const val LONG_PRESS_ELEMENT = "long_press_element"
    const val SWIPE = "swipe"
    const val SCROLL = "scroll"
    const val SCROLL_ELEMENT = "scroll_element"
    const val SKILLS_LIST = "skills_list"
    const val SKILLS_READ = "skills_read"
    const val SKILLS_READ_RESOURCE = "skills_read_resource"
    const val SKILLS_LIST_CURATED = "skills_list_curated"
    const val SKILLS_INSPECT_GITHUB = "skills_inspect_github"
    const val SKILLS_INSTALL_FROM_GITHUB = "skills_install_from_github"
    val all = listOf(
        OBSERVE_SCREEN, TAP, TAP_AREA, TAP_ELEMENT, LONG_PRESS, LONG_PRESS_ELEMENT, SWIPE,
        SCROLL, SCROLL_ELEMENT, SEARCH_APPS, LAUNCH_APP, OPEN_URI, READ_IMAGE, CURRENT_CONTEXT,
        SKILLS_LIST, SKILLS_READ, SKILLS_READ_RESOURCE, SKILLS_LIST_CURATED,
        SKILLS_INSPECT_GITHUB, SKILLS_INSTALL_FROM_GITHUB,
    )
}

/** Eta-compatible split entry points backed by RikkaHub's real stores. */
fun createEtaAndroidCompatibilityTools(
    context: Context,
    skillManager: SkillManager,
    enabledSkills: Set<String>,
    protectionEnabled: Boolean = false,
    rootController: AndroidRootTerminalController? = null,
    accessibilityNeedsApproval: Boolean = false,
): List<Tool> = listOf(
    observeScreenTool(context, protectionEnabled, rootController),
    *etaGestureTools(context, protectionEnabled, rootController, accessibilityNeedsApproval).toTypedArray(),
    searchAppsTool(context),
    *etaContextActionTools(context, rootController).toTypedArray(),
    currentContextTool(context),
    skillsListTool(skillManager, enabledSkills), skillsReadTool(skillManager, enabledSkills),
    *etaSkillTools(skillManager, enabledSkills).toTypedArray(),
)

private fun observeScreenTool(context: Context, protectionEnabled: Boolean, rootController: AndroidRootTerminalController?) = Tool(
    name = EtaCompatibilityToolNames.OBSERVE_SCREEN,
    description = "Observe the current Android screen. Returns foreground package, display size, observation_id and visible UI nodes by default; include a screenshot only when needed.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("include_screenshot", buildJsonObject { put("type", "boolean"); put("default", false) })
        put("include_ui_tree", buildJsonObject { put("type", "boolean"); put("default", true) })
        put("max_nodes", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 120); put("default", 60) })
    }) },
    execute = { input ->
        val args = input.jsonObject
        RikkaAccessibilityKeeper.ensureAvailable(context, protectionEnabled, rootController)
        val includeTree = args["include_ui_tree"]?.jsonPrimitive?.booleanOrNull ?: true
        val includeScreenshot = args["include_screenshot"]?.jsonPrimitive?.booleanOrNull ?: false
        val maxNodes = (args["max_nodes"]?.jsonPrimitive?.intOrNull ?: 60).coerceIn(1, 120)
        val observation = RikkaAccessibilityService.observe(if (includeTree) maxNodes else 1)
        var screenshot: AccessibilityScreenshot? = null
        if (includeScreenshot) screenshot = RikkaAccessibilityService.captureScreenshot(observation.observationId)
        val payload = buildJsonObject {
            put("ok", true); put("tool", "observe_screen"); put("observation_id", observation.observationId)
            put("ui_tree_truncated", includeTree && observation.truncated)
            put("coordinate_contract", buildJsonObject {
                put("default_coordinate_space", if (screenshot == null) "screen" else "screenshot")
                put("node_bounds_coordinate_space", "screen")
            })
            put("screen", buildJsonObject { put("width", observation.display.width); put("height", observation.display.height) })
            observation.packageName?.let { put("package_name", it) }
            put("ui_nodes", buildJsonArray {
                if (includeTree) observation.nodes.forEach { node -> add(buildJsonObject {
                    put("index", node.index); node.className?.let { put("class", it) }
                    node.text?.let { put("text", it) }; node.contentDescription?.let { put("desc", it) }
                    put("clickable", node.clickable); put("editable", node.editable); put("scrollable", node.scrollable); put("enabled", node.enabled)
                    put("bounds", buildJsonObject { put("left", node.left); put("top", node.top); put("right", node.right); put("bottom", node.bottom) })
                    put("center", buildJsonObject { put("x", (node.left + node.right) / 2); put("y", (node.top + node.bottom) / 2) })
                }) }
            })
            put("screenshot", buildJsonObject {
                put("requested", includeScreenshot); put("attached", screenshot != null)
                screenshot?.let { put("width", it.width); put("height", it.height) }
            })
        }
        buildList { add(UIMessagePart.Text(payload.toString())); screenshot?.let { add(UIMessagePart.Image(it.uri)) } }
    },
)

private fun searchAppsTool(context: Context) = Tool(
    name = EtaCompatibilityToolNames.SEARCH_APPS, description = "Search installed Android applications by display name or package name.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("query", buildJsonObject { put("type", "string") }); put("include_system", buildJsonObject { put("type", "boolean") })
        put("limit", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 20) })
    }, required = listOf("query")) },
    execute = { input ->
        val args = input.jsonObject; val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(query.isNotBlank()) { "INVALID_ARGUMENT" }
        val includeSystem = args["include_system"]?.jsonPrimitive?.booleanOrNull ?: false
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 20); val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)).asSequence().map { app ->
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            Triple(app, label, (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }.filter { (app, label, system) -> (includeSystem || !system) && (label.contains(query, true) || app.packageName.contains(query, true)) }
            .sortedWith(compareBy<Triple<ApplicationInfo, String, Boolean>>({ !it.second.equals(query, true) }, { !it.first.packageName.equals(query, true) }, { it.second.lowercase() }))
            .take(limit).toList()
        val items = JSONArray(); apps.forEach { (app, label, system) -> items.put(JSONObject().put("app_name", label).put("package_name", app.packageName).put("system_app", system).put("enabled", app.enabled)) }
        listOf(UIMessagePart.Text(JSONObject().put("ok", true).put("tool", "search_apps").put("query", query).put("apps", items).put("count", items.length()).toString()))
    },
)

private fun currentContextTool(context: Context) = Tool(
    name = EtaCompatibilityToolNames.CURRENT_CONTEXT, description = "Get the phone's current local date/time, timezone, weekday and newest cached system location.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) }, execute = {
        val now = ZonedDateTime.now(); listOf(UIMessagePart.Text(JSONObject().put("datetime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("timezone", now.zone.id).put("weekday", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.SIMPLIFIED_CHINESE))
            .put("location", latestLocation(context)).toString()))
    },
)

private fun latestLocation(context: Context): JSONObject {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return JSONObject().put("status", "permission_required")
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        ?: return JSONObject().put("status", "unavailable")
    return JSONObject().put("latitude", round(location.latitude * 100_000.0) / 100_000.0).put("longitude", round(location.longitude * 100_000.0) / 100_000.0)
        .put("accuracy_m", location.accuracy.roundToInt()).put("age_s", ((System.currentTimeMillis() - location.time).coerceAtLeast(0L)) / 1_000L)
}

private fun skillsListTool(skillManager: SkillManager, enabledSkills: Set<String>) = Tool(
    name = EtaCompatibilityToolNames.SKILLS_LIST, description = "List installed skills with id, name, description and capabilities.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("query", buildJsonObject { put("type", "string") }); put("limit", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 200) })
    }) }, execute = { input ->
        val query = input.jsonObject["query"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
        val limit = (input.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 50).coerceIn(1, 200)
        val skills = skillManager.listSkills().filter { skill -> query.isBlank() || listOf(skill.name, skill.description, skill.skillFile.path).any { it.lowercase().contains(query) } }.take(limit)
        val items = JSONArray(); skills.forEach { items.put(skillSummary(it, it.name in enabledSkills)) }
        listOf(UIMessagePart.Text(JSONObject().put("ok", true).put("query", query).put("count", items.length()).put("items", items).toString()))
    },
)

private fun skillsReadTool(skillManager: SkillManager, enabledSkills: Set<String>) = Tool(
    name = EtaCompatibilityToolNames.SKILLS_READ, description = "Read the bounded SKILL.md body of an installed skill by id, name or path.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("skillId", buildJsonObject { put("type", "string") }); put("maxChars", buildJsonObject { put("type", "integer"); put("minimum", 512); put("maximum", 64000) })
    }, required = listOf("skillId")) }, execute = { input ->
        val id = input.jsonObject["skillId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(); if (id.isBlank()) error("MISSING_PARAM")
        val maxChars = (input.jsonObject["maxChars"]?.jsonPrimitive?.intOrNull ?: 16_000).coerceIn(512, 64_000)
        val skill = findSkill(skillManager.listSkills(), id) ?: error("NOT_FOUND"); val body = SkillFrontmatterParser.extractBody(skill.skillFile.readText())
        val truncated = body.length > maxChars
        listOf(UIMessagePart.Text(JSONObject().put("ok", true).put("id", skill.name).put("name", skill.name).put("description", skill.description)
            .put("enabled", skill.name in enabledSkills).put("rootPath", skill.skillDir.path).put("skillFilePath", skill.skillFile.path)
            .put("capabilities", capabilities(skill)).put("bodyMarkdown", if (truncated) body.take(maxChars) + "\n..." else body).put("truncated", truncated).toString()))
    },
)

private fun findSkill(skills: List<SkillMetadata>, id: String): SkillMetadata? = skills.firstOrNull {
    it.name.equals(id, true) || it.skillFile.path == id || it.skillDir.path == id || it.skillDir.name.equals(id, true)
}
private fun skillSummary(skill: SkillMetadata, enabled: Boolean) = JSONObject().put("id", skill.name).put("name", skill.name).put("description", skill.description)
    .put("enabled", enabled).put("rootPath", skill.skillDir.path).put("skillFilePath", skill.skillFile.path).put("capabilities", capabilities(skill))
private fun capabilities(skill: SkillMetadata): JSONArray = JSONArray().apply {
    if (skill.skillDir.resolve("scripts").isDirectory) put("scripts"); if (skill.skillDir.resolve("references").isDirectory) put("references")
    if (skill.skillDir.resolve("assets").isDirectory) put("assets"); if (skill.skillDir.resolve("evals").isDirectory) put("evals")
}
