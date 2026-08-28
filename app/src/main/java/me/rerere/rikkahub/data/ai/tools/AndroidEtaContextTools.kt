package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.koin.java.KoinJavaComponent.getKoin

internal fun etaContextActionTools(
    context: Context,
    rootController: AndroidRootTerminalController?,
): List<Tool> = listOf(
    Tool(
        name = EtaCompatibilityToolNames.LAUNCH_APP,
        description = "Launch an installed Android application by exact package name or an unambiguous display-name match.",
        parameters = { InputSchema.Obj(
            properties = buildJsonObject {
                put("package_name", buildJsonObject { put("type", "string") })
                put("app_name", buildJsonObject { put("type", "string") })
            },
        ) },
        needsApproval = { false },
        execute = { input ->
            val args = input.jsonObject
            val packageName = args["package_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val appName = args["app_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            require(packageName.isNotBlank() || appName.isNotBlank()) { "PACKAGE_OR_APP_NAME_REQUIRED" }
            val pm = context.packageManager
            val candidates = if (packageName.isNotBlank()) {
                listOfNotNull(runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull())
            } else {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)).filter {
                    runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName)
                        .contains(appName, ignoreCase = true)
                }
            }
            if (candidates.isEmpty()) error("PACKAGE_NOT_FOUND")
            if (candidates.size > 1) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("ok", false); put("tool", EtaCompatibilityToolNames.LAUNCH_APP)
                    put("code", "AMBIGUOUS_APP_NAME")
                    put("candidates", buildJsonArray {
                        candidates.take(20).forEach { add(appSummary(pm, it)) }
                    })
                }.toString()))
            }
            val app = candidates.single()
            val intent = pm.getLaunchIntentForPackage(app.packageName) ?: error("NO_LAUNCH_ACTIVITY")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("ok", true); put("tool", EtaCompatibilityToolNames.LAUNCH_APP)
                put("package_name", app.packageName)
                put("app_name", pm.getApplicationLabel(app).toString())
                put("launched", true)
            }.toString()))
        },
    ),
    Tool(
        name = EtaCompatibilityToolNames.OPEN_URI,
        description = "Hand a validated URI to an external Android application; this tool does not read or interact with web pages.",
        parameters = { InputSchema.Obj(
            properties = buildJsonObject { put("uri", buildJsonObject { put("type", "string"); put("maxLength", 2048) }) },
            required = listOf("uri"),
        ) },
        needsApproval = { true },
        execute = { input ->
            val raw = input.jsonObject["uri"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            require(raw.isNotBlank() && raw.length <= 2048) { "INVALID_URI" }
            val uri = Uri.parse(raw)
            require(!uri.scheme.isNullOrBlank()) { "URI_SCHEME_REQUIRED" }
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            require(intent.resolveActivity(context.packageManager) != null) { "NO_URI_HANDLER" }
            context.startActivity(intent)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("ok", true); put("tool", EtaCompatibilityToolNames.OPEN_URI)
                put("opened", true); put("scheme", uri.scheme!!.lowercase())
            }.toString()))
        },
    ),
    Tool(
        name = EtaCompatibilityToolNames.READ_IMAGE,
        description = "Read one local image path, file URI, or content URI and attach it as visual model input.",
        parameters = { InputSchema.Obj(
            properties = buildJsonObject { put("path", buildJsonObject { put("type", "string"); put("maxLength", 1024) }) },
            required = listOf("path"),
        ) },
        execute = { input ->
            val raw = input.jsonObject["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            require(raw.isNotBlank() && raw.length <= 1024) { "INVALID_IMAGE_PATH" }
            val source = when {
                raw.startsWith("content://") -> Uri.parse(raw)
                raw.startsWith("file://") -> importImageFile(context, Uri.parse(raw).path?.let(::File) ?: error("INVALID_IMAGE_PATH"), rootController)
                raw.startsWith("/") -> importImageFile(context, File(raw), rootController)
                else -> error("INVALID_IMAGE_PATH")
            }
            val manager = getKoin().get<FilesManager>()
            val managed = if (source.scheme == "content") manager.createChatFilesByContents(listOf(source)).singleOrNull()
                ?: error("IMAGE_READ_FAILED") else source
            listOf(
                UIMessagePart.Image(managed.toString()),
                UIMessagePart.Text(buildJsonObject {
                    put("ok", true); put("tool", EtaCompatibilityToolNames.READ_IMAGE); put("path", raw)
                }.toString()),
            )
        },
    ),
)

private fun appSummary(pm: PackageManager, app: ApplicationInfo) = buildJsonObject {
    put("app_name", runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName))
    put("package_name", app.packageName)
}

private fun importImageFile(
    context: Context,
    source: File,
    rootController: AndroidRootTerminalController?,
): Uri {
    val bytes = if (source.canRead()) {
        require(source.length() in 1..(25L * 1024L * 1024L)) { "IMAGE_TOO_LARGE_OR_EMPTY" }
        source.readBytes()
    } else {
        val root = rootController ?: error("ROOT_REQUIRED_FOR_IMAGE_PATH")
        val temporary = File(context.cacheDir, "eta-read-image-${UUID.randomUUID()}")
        try {
            val result = root.executeSync("cp -- ${source.path.shellQuote()} ${temporary.path.shellQuote()}", timeoutMs = 20_000)
            if (result.exitCode != 0 || !temporary.canRead()) error("IMAGE_READ_FAILED")
            require(temporary.length() in 1..(25L * 1024L * 1024L)) { "IMAGE_TOO_LARGE_OR_EMPTY" }
            temporary.readBytes()
        } finally {
            temporary.delete()
        }
    }
    require(bytes.isNotEmpty()) { "IMAGE_EMPTY" }
    require(bytes.size <= 25 * 1024 * 1024) { "IMAGE_TOO_LARGE" }
    return getKoin().get<FilesManager>().createChatFilesByByteArrays(listOf(bytes)).single()
}

private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"
