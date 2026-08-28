package me.rerere.rikkahub.data.files

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore

class SkillManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "SkillManager"
        private const val BUILTIN_SKILLS_MANIFEST = "builtin_skills/manifest.json"
    }

    @Volatile
    private var builtinSkillsSeeded = false

    fun getSkillsDir(): File {
        val dir = context.filesDir.resolve(FileFolders.SKILLS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSkills(): List<SkillMetadata> {
        ensureBuiltinSkillsSeeded()
        val skillsDir = getSkillsDir()
        return skillsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val skillFile = dir.resolve("SKILL.md")
                if (!skillFile.exists()) return@mapNotNull null
                parseSkillFile(skillFile, dir)
            }
            ?: emptyList()
    }


    /**
     * Seed Eta's packaged built-in skills into RikkaHub's normal skill directory.
     * Existing directories are never overwritten, so user-managed skills remain authoritative.
     */
    internal fun ensureBuiltinSkillsSeeded() {
        if (builtinSkillsSeeded) return
        synchronized(this) {
            if (builtinSkillsSeeded) return
            val seeded = runCatching {
                val builtins = context.assets.open(BUILTIN_SKILLS_MANIFEST).bufferedReader().use { reader ->
                    parseBuiltinSkillManifest(reader.readText())
                }
                val skillsRoot = getSkillsDir()
                for (builtin in builtins) {
                    val id = builtin.id
                    val assetPath = builtin.assetPath
                    val targetDir = SkillPaths.resolveSkillDir(skillsRoot, id) ?: continue
                    if (targetDir.resolve("SKILL.md").isFile) continue
                    if (targetDir.exists()) {
                        Log.w(TAG, "Builtin skill target exists but is incomplete; preserving it: $id")
                        continue
                    }
                    val files = linkedMapOf<String, ByteArray>()
                    collectAssetFiles(assetPath, "", files)
                    if (!saveSkillFileBytesAtomically(id, files)) {
                        error("Unable to install built-in skill: $id")
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to seed built-in skills", error)
            }.isSuccess
            builtinSkillsSeeded = seeded
        }
    }

    private fun collectAssetFiles(
        assetPath: String,
        relativePath: String,
        output: MutableMap<String, ByteArray>,
    ) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            if (relativePath.isBlank()) error("Invalid empty built-in skill asset: $assetPath")
            output[relativePath] = context.assets.open(assetPath).use { it.readBytes() }
            return
        }
        children.forEach { child ->
            collectAssetFiles(
                assetPath = "$assetPath/$child",
                relativePath = if (relativePath.isBlank()) child else "$relativePath/$child",
                output = output,
            )
        }
    }

    fun readSkillBody(skillName: String): String? {
        ensureBuiltinSkillsSeeded()
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return SkillFrontmatterParser.extractBody(skillFile.readText())
    }

    fun readSkillContent(skillName: String): String? {
        ensureBuiltinSkillsSeeded()
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return skillFile.readText()
    }

    fun saveSkill(name: String, content: String): SkillMetadata? {
        // 通过原子写入(staging + rename)落盘，避免直接 mkdirs 失败时
        // writeText 抛出 FileNotFoundException 导致崩溃
        if (!saveSkillFileBytesAtomically(name, mapOf("SKILL.md" to content.toByteArray()))) {
            return null
        }
        val skillDir = resolveSkillDir(name) ?: return null
        return parseSkillFile(skillDir.resolve("SKILL.md"), skillDir)
    }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val skillDir = resolveSkillDir(name) ?: return@withContext false
        val deleted = skillDir.deleteRecursively()
        if (deleted) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.enabledSkills.contains(name)) {
                            assistant.copy(enabledSkills = assistant.enabledSkills - name)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    /**
     * 清理所有助手 enabledSkills 中已不存在于磁盘的技能名。
     *
     * 当用户在 App 外直接删除 /skills/ 目录下的技能时，不会走 [deleteSkill] 的清理逻辑，
     * 导致 enabledSkills 残留"幽灵"技能名，使扩展入口角标计数偏大。
     */
    suspend fun pruneOrphanedEnabledSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        val skills = listSkills()
        val existing = skills.mapTo(HashSet()) { it.name }
        settingsStore.update { settings ->
            var changed = false
            val newAssistants = settings.assistants.map { assistant ->
                val pruned = assistant.enabledSkills.filterTo(LinkedHashSet()) { it in existing }
                if (pruned.size != assistant.enabledSkills.size) {
                    changed = true
                    assistant.copy(enabledSkills = pruned)
                } else {
                    assistant
                }
            }
            if (changed) settings.copy(assistants = newAssistants) else settings
        }
        skills
    }

    fun getSkillDir(skillName: String): File? = resolveSkillDir(skillName)

    fun saveSkillFile(skillName: String, relativePath: String, content: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        return true
    }

    fun saveSkillFilesAtomically(skillName: String, files: Map<String, String>): Boolean {
        return saveSkillFileBytesAtomically(
            skillName = skillName,
            files = files.mapValues { it.value.toByteArray() },
        )
    }

    fun saveSkillFileBytesAtomically(skillName: String, files: Map<String, ByteArray>): Boolean {
        val skillsDir = getSkillsDir()
        val targetDir = resolveSkillDir(skillName) ?: return false
        val stagingDir = createTempSkillDir(skillsDir, skillName, "staging") ?: return false
        var backupDir: File? = null

        try {
            for ((relativePath, content) in files) {
                val target = SkillPaths.resolveSkillFile(stagingDir, relativePath) ?: return false
                target.parentFile?.mkdirs()
                target.writeBytes(content)
            }

            if (!stagingDir.resolve("SKILL.md").exists()) return false

            if (targetDir.exists()) {
                backupDir = createTempSkillDir(skillsDir, skillName, "backup") ?: return false
                if (!targetDir.renameTo(backupDir)) return false
            }

            if (!stagingDir.renameTo(targetDir)) {
                if (backupDir != null && !targetDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                return false
            }

            backupDir?.deleteRecursively()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "saveSkillFilesAtomically: Failed to save $skillName", e)
            if (backupDir != null && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            return false
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            if (backupDir?.exists() == true && targetDir.exists()) {
                backupDir.deleteRecursively()
            }
        }
    }

    fun isBuiltinSkillId(skillName: String): Boolean = runCatching {
        context.assets.open(BUILTIN_SKILLS_MANIFEST).bufferedReader().use { reader ->
            parseBuiltinSkillManifest(reader.readText()).any { it.id == skillName }
        }
    }.getOrDefault(false)

    fun installSkillBatchAtomically(skills: Map<String, Map<String, ByteArray>>): Boolean {
        if (skills.isEmpty() || skills.keys.any { resolveSkillDir(it) == null }) return false
        val root = getSkillsDir()
        val operation = root.resolve(".install-${System.nanoTime()}.tmp")
        val staged = operation.resolve("staged")
        val backups = operation.resolve("backups")
        if (!staged.mkdirs() || !backups.mkdirs()) return false
        val movedTargets = mutableListOf<Pair<File, File>>()
        val installedTargets = mutableListOf<File>()
        try {
            skills.forEach { (id, files) ->
                val dir = staged.resolve(id)
                files.forEach { (relativePath, bytes) ->
                    val target = SkillPaths.resolveSkillFile(dir, relativePath) ?: return false
                    target.parentFile?.mkdirs(); target.writeBytes(bytes)
                }
                if (!dir.resolve("SKILL.md").isFile) return false
            }
            skills.keys.forEach { id ->
                val target = resolveSkillDir(id) ?: return false
                if (target.exists()) {
                    val backup = backups.resolve(id)
                    if (!target.renameTo(backup)) return false
                    movedTargets += target to backup
                }
            }
            skills.keys.forEach { id ->
                val source = staged.resolve(id)
                val target = resolveSkillDir(id) ?: return false
                if (!source.renameTo(target)) error("Unable to commit Skill $id")
                installedTargets += target
            }
            return true
        } catch (error: Exception) {
            Log.w(TAG, "installSkillBatchAtomically failed", error)
            installedTargets.asReversed().forEach { it.deleteRecursively() }
            movedTargets.asReversed().forEach { (target, backup) ->
                if (!target.exists()) backup.renameTo(target)
            }
            return false
        } finally {
            operation.deleteRecursively()
        }
    }

    fun deleteSkillFile(skillName: String, relativePath: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        return target.delete()
    }

    fun resolveSkillFile(skillName: String, relativePath: String): File? {
        val skillDir = resolveSkillDir(skillName) ?: return null
        return SkillPaths.resolveSkillFile(skillDir, relativePath)
    }

    private fun resolveSkillDir(skillName: String): File? {
        return SkillPaths.resolveSkillDir(getSkillsDir(), skillName)
    }

    private fun createTempSkillDir(skillsRoot: File, skillName: String, suffix: String): File? {
        repeat(100) { attempt ->
            val candidate = skillsRoot.resolve(".$skillName.$suffix.$attempt.tmp")
            if (!candidate.exists() && candidate.mkdirs()) {
                return candidate
            }
        }
        return null
    }

    private fun parseSkillFile(skillFile: File, skillDir: File): SkillMetadata? {
        return runCatching {
            val content = skillFile.readText()
            val frontmatter = SkillFrontmatterParser.parse(content)
            val name = frontmatter["name"]?.takeIf { it.isNotBlank() } ?: return null
            val description = frontmatter["description"]?.takeIf { it.isNotBlank() } ?: return null
            SkillMetadata(
                name = name,
                description = description,
                compatibility = frontmatter["compatibility"],
                skillDir = skillDir,
            )
        }.getOrElse {
            Log.w(TAG, "parseSkillFile: Failed to parse ${skillFile.absolutePath}", it)
            null
        }
    }
}

data class SkillMetadata(
    val name: String,
    val description: String,
    val compatibility: String? = null,
    val skillDir: File,
) {
    val skillFile: File get() = skillDir.resolve("SKILL.md")
}


internal data class BuiltinSkillAssetSpec(
    val id: String,
    val assetPath: String,
)

internal fun parseBuiltinSkillManifest(content: String): List<BuiltinSkillAssetSpec> {
    val skills = JSONObject(content).optJSONArray("skills") ?: error("Missing skills array")
    return buildList {
        for (index in 0 until skills.length()) {
            val item = skills.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val assetPath = item.optString("assetPath").trim()
            if (id.isNotBlank() && assetPath.isNotBlank()) {
                add(BuiltinSkillAssetSpec(id = id, assetPath = assetPath))
            }
        }
    }
}
