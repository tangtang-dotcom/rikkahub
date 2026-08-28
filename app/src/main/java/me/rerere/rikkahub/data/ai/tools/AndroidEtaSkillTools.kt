package me.rerere.rikkahub.data.ai.tools

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.java.KoinJavaComponent.getKoin

internal fun etaSkillTools(
    skillManager: SkillManager,
    enabledSkills: Set<String>,
): List<Tool> {
    val github = EtaGitHubSkillSession(skillManager, getKoin().get<OkHttpClient>())
    return listOf(
        skillsReadResourceTool(skillManager, enabledSkills),
        Tool(
            name = "skills_list_curated",
            description = "List installable Skills from the public openai/skills curated catalog. This is read-only and returns a pinned commit SHA.",
            parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
            execute = { listOf(UIMessagePart.Text(github.inspect(EtaGitHubRepository("openai", "skills", "main", "skills/.curated")))) },
        ),
        Tool(
            name = "skills_inspect_github",
            description = "Inspect a public GitHub repository and list every directory containing SKILL.md without installing anything.",
            parameters = { InputSchema.Obj(properties = buildJsonObject {
                put("repository", buildJsonObject { put("type", "string"); put("maxLength", 500) })
                put("ref", buildJsonObject { put("type", "string"); put("maxLength", 200) })
                put("path", buildJsonObject { put("type", "string"); put("maxLength", 1000) })
            }, required = listOf("repository")) },
            execute = { input ->
                val args = input.jsonObject
                val repository = EtaGitHubRepositoryParser.resolve(
                    args["repository"]?.jsonPrimitive?.contentOrNull ?: error("REPOSITORY_REQUIRED"),
                    args["ref"]?.jsonPrimitive?.contentOrNull,
                    args["path"]?.jsonPrimitive?.contentOrNull,
                )
                listOf(UIMessagePart.Text(github.inspect(repository)))
            },
        ),
        Tool(
            name = "skills_install_from_github",
            description = "Install exact Skill roots returned by skills_inspect_github. Installation is pinned to the inspected commit, bounded, path-safe, atomic, and never runs bundled scripts.",
            parameters = { InputSchema.Obj(properties = buildJsonObject {
                put("repository", buildJsonObject { put("type", "string"); put("maxLength", 500) })
                put("ref", buildJsonObject { put("type", "string"); put("maxLength", 200) })
                put("paths", buildJsonObject {
                    put("type", "array"); put("items", buildJsonObject { put("type", "string"); put("maxLength", 1000) })
                    put("minItems", 1); put("maxItems", 20); put("uniqueItems", true)
                })
                put("replaceExisting", buildJsonObject { put("type", "boolean") })
                put("expectedReplacementId", buildJsonObject { put("type", "string"); put("maxLength", 500) })
            }, required = listOf("repository", "paths")) },
            needsApproval = { true },
            execute = { input -> listOf(UIMessagePart.Text(github.install(input.jsonObject))) },
        ),
    )
}

private fun skillsReadResourceTool(skillManager: SkillManager, enabledSkills: Set<String>) = Tool(
    name = "skills_read_resource",
    description = "Read a bounded UTF-8 text resource inside an enabled Skill. Paths are relative, cannot escape the Skill root, and files are never executed.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("skillId", buildJsonObject { put("type", "string"); put("maxLength", 500) })
        put("relativePath", buildJsonObject { put("type", "string"); put("maxLength", 1000) })
        put("maxChars", buildJsonObject { put("type", "integer"); put("minimum", 512); put("maximum", 64000) })
    }, required = listOf("skillId", "relativePath")) },
    execute = { input ->
        val args = input.jsonObject
        val id = args["skillId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val relativePath = args["relativePath"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(id.isNotBlank() && relativePath.isNotBlank()) { "MISSING_PARAM" }
        val skill = skillManager.listSkills().firstOrNull {
            it.name.equals(id, true) || it.skillDir.name.equals(id, true) || it.skillDir.path == id || it.skillFile.path == id
        } ?: error("SKILL_NOT_FOUND")
        require(skill.name in enabledSkills || skill.skillDir.name in enabledSkills) { "SKILL_NOT_ENABLED" }
        val file = skillManager.resolveSkillFile(skill.skillDir.name, relativePath) ?: error("INVALID_SKILL_RESOURCE_PATH")
        require(file.isFile && !file.isDirectory) { "SKILL_RESOURCE_NOT_FOUND" }
        require(file.length() <= 2L * 1024L * 1024L) { "SKILL_RESOURCE_TOO_LARGE" }
        val bytes = file.readBytes()
        val text = bytes.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "SKILL_RESOURCE_NOT_UTF8" }
        val maxChars = (args["maxChars"]?.jsonPrimitive?.intOrNull ?: 16_000).coerceIn(512, 64_000)
        val visible = text.take(maxChars).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", true); put("skillId", skill.name); put("relativePath", relativePath)
            put("text", visible); put("truncated", visible.length < text.length); put("totalChars", text.length)
        }.toString()))
    },
)

private data class EtaGitHubRepository(val owner: String, val repository: String, val ref: String? = null, val path: String? = null) {
    val slug get() = "$owner/$repository"
}
private data class EtaSkillSnapshot(val repository: EtaGitHubRepository, val ref: String, val commitSha: String, val paths: Set<String>)
private data class EtaPendingConflict(val slug: String, val commitSha: String, val path: String, val id: String)

private object EtaGitHubRepositoryParser {
    private val ownerPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
    private val repositoryPattern = Regex("[A-Za-z0-9_.-]{1,100}")
    private val refPattern = Regex("[A-Za-z0-9._/-]{1,200}")

    fun resolve(value: String, explicitRef: String?, explicitPath: String?): EtaGitHubRepository {
        val parsed = parse(value)
        val ref = explicitRef?.trim()?.takeIf(String::isNotEmpty)?.let(::normalizeRef)
        val path = explicitPath?.trim()?.takeIf(String::isNotEmpty)?.let(::normalizePath)
        require(parsed.ref == null || ref == null || parsed.ref == ref) { "GITHUB_REF_MISMATCH" }
        require(parsed.path == null || path == null || parsed.path == path) { "GITHUB_PATH_MISMATCH" }
        return parsed.copy(ref = ref ?: parsed.ref, path = path ?: parsed.path)
    }

    fun normalizePath(value: String): String {
        val normalized = value.trim().trimEnd('/')
        if (normalized == ".") return "."
        require(normalized.isNotBlank() && normalized.length <= 1000 && !normalized.startsWith('/') && '\\' !in normalized && '\u0000' !in normalized) { "INVALID_GITHUB_PATH" }
        val parts = normalized.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.any(Char::isISOControl) }) { "INVALID_GITHUB_PATH" }
        return parts.joinToString("/")
    }

    private fun normalizeRef(value: String): String {
        val ref = value.trim()
        require(refPattern.matches(ref) && !ref.startsWith('/') && !ref.endsWith('/') && "//" !in ref && "@{" !in ref && ref.split('/').none { it == "." || it == ".." || it.endsWith(".lock") }) { "INVALID_GITHUB_REF" }
        return ref
    }

    private fun parse(value: String): EtaGitHubRepository {
        val input = value.trim()
        require(input.isNotBlank() && input.length <= 2048) { "INVALID_GITHUB_SOURCE" }
        if (!input.contains("://")) {
            val parts = input.removeSuffix(".git").split('/')
            require(parts.size == 2) { "INVALID_GITHUB_SOURCE" }
            return EtaGitHubRepository(validateOwner(parts[0]), validateRepository(parts[1]))
        }
        val uri = URI(input)
        require(uri.scheme.equals("https", true) && uri.host?.lowercase(Locale.ROOT) in setOf("github.com", "www.github.com") && uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null) { "INVALID_GITHUB_SOURCE" }
        val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
        require(parts.size >= 2) { "INVALID_GITHUB_SOURCE" }
        val owner = validateOwner(parts[0]); val repo = validateRepository(parts[1].removeSuffix(".git"))
        if (parts.size == 2) return EtaGitHubRepository(owner, repo)
        require(parts.size >= 4 && parts[2] in setOf("tree", "blob")) { "INVALID_GITHUB_SOURCE" }
        val ref = normalizeRef(parts[3])
        var path = parts.drop(4).joinToString("/").takeIf(String::isNotBlank)?.let(::normalizePath)
        if (parts[2] == "blob" && path?.substringAfterLast('/') == "SKILL.md") path = path.substringBeforeLast('/', ".")
        return EtaGitHubRepository(owner, repo, ref, path)
    }

    private fun validateOwner(value: String) = value.also { require(ownerPattern.matches(it)) { "INVALID_GITHUB_OWNER" } }
    private fun validateRepository(value: String) = value.also { require(repositoryPattern.matches(it) && it !in setOf(".", "..")) { "INVALID_GITHUB_REPOSITORY" } }
}

private class EtaGitHubSkillSession(
    private val skillManager: SkillManager,
    baseClient: OkHttpClient,
) {
    private val client = baseClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val snapshots = linkedMapOf<String, EtaSkillSnapshot>()
    private var pendingConflict: EtaPendingConflict? = null

    fun inspect(requested: EtaGitHubRepository): String = guarded {
        val ref = requested.ref ?: requestJson(api("repos", requested.owner, requested.repository), 512 * 1024).get("default_branch")?.jsonPrimitive?.contentOrNull ?: error("GITHUB_DEFAULT_BRANCH_MISSING")
        val commit = requestJson(api("repos", requested.owner, requested.repository, "commits", ref), 1024 * 1024)
        val sha = commit["sha"]?.jsonPrimitive?.contentOrNull?.takeIf { Regex("[0-9a-fA-F]{40}").matches(it) } ?: error("INVALID_GITHUB_RESPONSE")
        val treeSha = commit["commit"]?.jsonObject?.get("tree")?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull ?: error("INVALID_GITHUB_RESPONSE")
        val tree = requestJson(api("repos", requested.owner, requested.repository, "git", "trees", treeSha, query = "recursive" to "1"), 16 * 1024 * 1024)
        require(tree["truncated"]?.jsonPrimitive?.booleanOrNull != true) { "REPOSITORY_TREE_TOO_LARGE" }
        val prefix = requested.path?.takeUnless { it == "." }?.trimEnd('/')
        val paths = tree["tree"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "blob") return@mapNotNull null
            val file = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (file.substringAfterLast('/') != "SKILL.md") return@mapNotNull null
            val root = file.substringBeforeLast('/', ".").let(EtaGitHubRepositoryParser::normalizePath)
            root.takeIf { prefix == null || it == prefix || it.startsWith("$prefix/") }
        }.distinct().sorted()
        require(paths.size <= 500) { "TOO_MANY_SKILL_CANDIDATES" }
        val snapshot = EtaSkillSnapshot(requested, ref, sha.lowercase(), paths.toSet())
        snapshots[key(requested.slug, requested.ref)] = snapshot
        snapshots[key(requested.slug, sha.lowercase())] = snapshot
        buildJsonObject {
            put("ok", true); put("repository", requested.slug); put("ref", ref); put("commitSha", sha.lowercase())
            if (prefix == null) put("prefix", JsonNull) else put("prefix", prefix)
            put("count", paths.size); put("items", buildJsonArray { paths.forEach { path -> add(buildJsonObject {
                put("name", if (path == ".") requested.repository else path.substringAfterLast('/')); put("path", path)
                put("installed", skillManager.listSkills().any { it.name.equals(path.substringAfterLast('/'), true) })
            }) } })
        }.toString()
    }

    fun install(args: JsonObject): String = guarded {
        val requested = EtaGitHubRepositoryParser.resolve(
            args["repository"]?.jsonPrimitive?.contentOrNull ?: error("REPOSITORY_REQUIRED"),
            args["ref"]?.jsonPrimitive?.contentOrNull,
            null,
        )
        val paths = args["paths"]?.jsonArray?.map { EtaGitHubRepositoryParser.normalizePath(it.jsonPrimitive.content) } ?: error("PATHS_REQUIRED")
        require(paths.isNotEmpty() && paths.size <= 20 && paths.distinct().size == paths.size) { "INVALID_SKILL_SELECTION" }
        val replace = args["replaceExisting"]?.jsonPrimitive?.booleanOrNull ?: false
        val expectedId = args["expectedReplacementId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val snapshot: EtaSkillSnapshot
        if (replace) {
            require(paths.size == 1 && expectedId.isNotBlank()) { "SKILL_REPLACE_SCOPE_TOO_BROAD" }
            val conflict = pendingConflict ?: error("SKILL_CONFLICT_REPLAY_REQUIRED")
            require(conflict.slug == requested.slug && conflict.path == paths.single() && conflict.id == expectedId && requested.ref?.equals(conflict.commitSha, true) == true) { "SKILL_CONFLICT_REPLAY_MISMATCH" }
            snapshot = snapshots[key(requested.slug, conflict.commitSha)] ?: error("SKILL_INSPECTION_REQUIRED")
        } else {
            snapshot = snapshots[key(requested.slug, requested.ref)] ?: error("SKILL_INSPECTION_REQUIRED")
            require(paths.all { it in snapshot.paths }) { "INVALID_SKILL_SELECTION" }
        }
        val prefix = requested.path?.takeUnless { it == "." }
        require(prefix == null || paths.all { it == prefix || it.startsWith("$prefix/") }) { "INVALID_SKILL_SELECTION" }
        val archive = requestBytes(codeload(requested, snapshot.commitSha), 32 * 1024 * 1024, "application/zip")
        val packages = extractSkills(archive, paths)
        val conflicts = packages.keys.filter { id -> skillManager.getSkillDir(id)?.exists() == true }
        val builtin = conflicts.filter(skillManager::isBuiltinSkillId)
        if (builtin.isNotEmpty()) return@guarded errorPayload("SKILL_CONFLICT", "Built-in Skills cannot be replaced", builtin, false)
        if (!replace && conflicts.isNotEmpty()) {
            val first = conflicts.first(); pendingConflict = EtaPendingConflict(requested.slug, snapshot.commitSha, paths.singleOrNull() ?: "", first)
            return@guarded errorPayload("SKILL_CONFLICT", "Skill already exists", conflicts, paths.size == 1)
        }
        if (replace) require(conflicts == listOf(expectedId) && packages.keys == setOf(expectedId)) { "SKILL_CONFLICT_REPLAY_MISMATCH" }
        require(skillManager.installSkillBatchAtomically(packages)) { "SKILL_INSTALL_FAILED" }
        pendingConflict = null
        buildJsonObject {
            put("ok", true); put("repository", requested.slug); put("ref", snapshot.ref); put("commitSha", snapshot.commitSha)
            put("installed", buildJsonArray { packages.keys.sorted().forEach(::add) }); put("count", packages.size)
            put("availableNextTurn", true)
        }.toString()
    }

    private fun extractSkills(archive: ByteArray, selected: List<String>): Map<String, Map<String, ByteArray>> {
        val filesByPath = selected.associateWith { linkedMapOf<String, ByteArray>() }.toMutableMap()
        var entries = 0; var total = 0L
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++; require(entries <= 2048) { "SKILL_ARCHIVE_TOO_MANY_ENTRIES" }
                val raw = entry.name.replace('\\', '/')
                require(!raw.startsWith('/') && raw.split('/').none { it == ".." } && raw.length <= 2000) { "UNSAFE_SKILL_ARCHIVE_PATH" }
                if (entry.isDirectory) continue
                val repositoryPath = raw.substringAfter('/', "")
                if (repositoryPath.isBlank()) continue
                val selectedRoot = selected.firstOrNull { it == "." || repositoryPath == it || repositoryPath.startsWith("$it/") } ?: continue
                val relative = if (selectedRoot == ".") repositoryPath else repositoryPath.removePrefix("$selectedRoot/")
                if (relative.isBlank()) continue
                val bytes = readBounded(zip, 32 * 1024 * 1024)
                total += bytes.size; require(total <= 128L * 1024L * 1024L) { "SKILL_ARCHIVE_EXTRACTED_TOO_LARGE" }
                filesByPath.getValue(selectedRoot)[relative] = bytes
            }
        }
        val result = linkedMapOf<String, Map<String, ByteArray>>()
        selected.forEach { path ->
            val files = filesByPath.getValue(path)
            val skillMd = files["SKILL.md"] ?: error("SKILL_MD_MISSING:$path")
            require(skillMd.size <= 512 * 1024) { "SKILL_MD_TOO_LARGE" }
            val text = skillMd.toString(Charsets.UTF_8)
            require(text.toByteArray(Charsets.UTF_8).contentEquals(skillMd)) { "SKILL_MD_NOT_UTF8" }
            val frontmatter = SkillFrontmatterParser.parse(text)
            val id = frontmatter["name"]?.trim().orEmpty()
            require(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}").matches(id) && !frontmatter["description"].isNullOrBlank()) { "INVALID_SKILL_METADATA" }
            require(id !in result) { "DUPLICATE_SKILL_ID" }
            result[id] = files
        }
        return result
    }

    private fun requestJson(url: HttpUrl, limit: Int): JsonObject = Json.parseToJsonElement(requestBytes(url, limit, "application/vnd.github+json").toString(Charsets.UTF_8)).jsonObject
    private fun requestBytes(url: HttpUrl, limit: Int, accept: String): ByteArray {
        val request = Request.Builder().url(url).header("Accept", accept).header("User-Agent", "RikkaHub-Eta-Skill-Installer").header("X-GitHub-Api-Version", "2022-11-28").build()
        return try {
            client.newCall(request).execute().use { response ->
                require(!response.isRedirect) { "GITHUB_REDIRECT_REJECTED" }
                if (!response.isSuccessful) error(when (response.code) { 403, 429 -> "GITHUB_RATE_LIMITED"; 404 -> "GITHUB_NOT_FOUND"; else -> "GITHUB_REQUEST_FAILED:${response.code}" })
                val length = response.body.contentLength(); require(length < 0 || length <= limit) { "GITHUB_RESPONSE_TOO_LARGE" }
                response.body.byteStream().use { readBounded(it, limit) }
            }
        } catch (e: IOException) { error("GITHUB_NETWORK_ERROR:${e.message}") }
    }

    private fun api(vararg segments: String, query: Pair<String, String>? = null) = HttpUrl.Builder().scheme("https").host("api.github.com").apply { segments.forEach(::addPathSegment); query?.let { addQueryParameter(it.first, it.second) } }.build()
    private fun codeload(repo: EtaGitHubRepository, sha: String) = HttpUrl.Builder().scheme("https").host("codeload.github.com").addPathSegment(repo.owner).addPathSegment(repo.repository).addPathSegment("zip").addPathSegment(sha).build()
    private fun key(slug: String, ref: String?) = "$slug|${ref.orEmpty()}".lowercase()
    private fun errorPayload(code: String, message: String, ids: List<String>, replaceAllowed: Boolean) = buildJsonObject {
        put("ok", false); put("code", code); put("message", message); put("conflicts", buildJsonArray { ids.forEach { id -> add(buildJsonObject { put("id", id); put("replaceAllowed", replaceAllowed && !skillManager.isBuiltinSkillId(id)) }) } })
    }.toString()
    private inline fun guarded(block: () -> String): String = try { block() } catch (e: Exception) { errorPayload(e.message?.substringBefore(':') ?: "SKILL_TOOL_FAILED", e.message ?: "Skill operation failed", emptyList(), false) }
}

private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0
    while (true) { val read = input.read(buffer); if (read < 0) break; total += read; require(total <= limit) { "RESPONSE_TOO_LARGE" }; output.write(buffer, 0, read) }
    return output.toByteArray()
}
