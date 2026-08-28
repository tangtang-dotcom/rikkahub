package me.rerere.rikkahub.data.terminal

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.coroutineContext

internal enum class LinuxEnvironmentState {
    NOT_INSTALLED,
    BASE_READY,
    READY,
}

internal data class LinuxEnvironmentStatus(
    val state: LinuxEnvironmentState,
    val version: String? = null,
)

internal data class LinuxEnvironmentHealth(
    val healthy: Boolean,
    val availableTools: List<String>,
    val missingTools: List<String>,
    val workspaceReady: Boolean,
    val sharedStorageReady: Boolean,
    val availableBytes: Long,
)

internal enum class LinuxInstallStage {
    CHECKING,
    DOWNLOADING,
    EXTRACTING,
    INSTALLING_TOOLS,
    COMPLETE,
}

internal data class LinuxInstallProgress(
    val stage: LinuxInstallStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
)

internal sealed interface LinuxInstallResult {
    data object AlreadyReady : LinuxInstallResult
    data class Installed(val version: String) : LinuxInstallResult
    data class UnsupportedAbi(val abi: String) : LinuxInstallResult
    data object RootUnavailable : LinuxInstallResult
    data object BusyBoxUnavailable : LinuxInstallResult
    data object EnvironmentUnavailable : LinuxInstallResult
    data class Failed(val stage: LinuxInstallStage) : LinuxInstallResult
}

/**
 * 下载官方 Alpine minirootfs，并在 Root 授权边界内完成原子解压与常用工具安装。
 * 下载内容先校验固定 SHA-256；安装过程不会扩大到 App 私有环境目录之外。
 */
internal class LinuxEnvironmentInstaller(
    private val context: Context,
    httpClient: OkHttpClient = VerifiedArtifactDownloader.defaultHttpClient(),
) {
    private val artifactDownloader = VerifiedArtifactDownloader(httpClient)
    fun status(): LinuxEnvironmentStatus {
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context)
        val version = readInstalledVersion(rootfs)
        val state = when {
            LinuxEnvironmentPaths.commonToolsReady(rootfs.absolutePath) -> LinuxEnvironmentState.READY
            LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath) -> LinuxEnvironmentState.BASE_READY
            else -> LinuxEnvironmentState.NOT_INSTALLED
        }
        return LinuxEnvironmentStatus(state = state, version = version)
    }

    suspend fun install(
        forceToolInstall: Boolean = false,
        onProgress: suspend (LinuxInstallProgress) -> Unit = {},
    ): LinuxInstallResult {
        installMutex.lock()
        return try {
            installLocked(forceToolInstall, onProgress)
        } finally {
            installMutex.unlock()
        }
    }

    suspend fun inspectHealth(): LinuxEnvironmentHealth = withContext(Dispatchers.IO) {
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context)
        val availableBytes = rootfs.parentFile?.usableSpace ?: context.filesDir.usableSpace
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
            return@withContext LinuxEnvironmentHealth(
                healthy = false,
                availableTools = emptyList(),
                missingTools = HEALTH_CHECK_COMMANDS,
                workspaceReady = false,
                sharedStorageReady = false,
                availableBytes = availableBytes,
            )
        }
        val command = buildString {
            append("for eta_tool in ")
            append(HEALTH_CHECK_COMMANDS.joinToString(" "))
            append("; do command -v \"${'$'}eta_tool\" >/dev/null 2>&1 && printf 'tool:%s\\n' \"${'$'}eta_tool\"; done\n")
            append("mountpoint -q /workspace && printf 'mount:workspace\\n'\n")
            append("mountpoint -q /storage/emulated/0 && printf 'mount:sdcard\\n'\n")
            append("true")
        }
        val result = InstallerShellRunner.run(
            command = command,
            timeoutSeconds = 30,
            environment = TerminalEnvironment.LINUX,
            linuxRootfsPath = rootfs.absolutePath,
        )
        val facts = result.output.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val availableTools = HEALTH_CHECK_COMMANDS.filter { tool -> "tool:$tool" in facts }
        val missingTools = HEALTH_CHECK_COMMANDS - availableTools.toSet()
        val workspaceReady = "mount:workspace" in facts
        val sharedStorageReady = "mount:sdcard" in facts
        LinuxEnvironmentHealth(
            healthy = result.exitCode == 0 && missingTools.isEmpty() && workspaceReady,
            availableTools = availableTools,
            missingTools = missingTools,
            workspaceReady = workspaceReady,
            sharedStorageReady = sharedStorageReady,
            availableBytes = availableBytes,
        )
    }

    private suspend fun installLocked(
        forceToolInstall: Boolean,
        onProgress: suspend (LinuxInstallProgress) -> Unit,
    ): LinuxInstallResult = withContext(Dispatchers.IO) {
        if (!forceToolInstall && status().state == LinuxEnvironmentState.READY) {
            return@withContext LinuxInstallResult.AlreadyReady
        }
        val artifact = artifactForAbis(Build.SUPPORTED_ABIS.toList())
            ?: return@withContext LinuxInstallResult.UnsupportedAbi(
                Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" },
            )

        onProgress(LinuxInstallProgress(LinuxInstallStage.CHECKING))
        when (runPreflight().exitCode) {
            0 -> Unit
            PREFLIGHT_ROOT_UNAVAILABLE -> return@withContext LinuxInstallResult.RootUnavailable
            PREFLIGHT_BUSYBOX_UNAVAILABLE, PREFLIGHT_BUSYBOX_INCOMPLETE ->
                return@withContext LinuxInstallResult.BusyBoxUnavailable
            PREFLIGHT_ENVIRONMENT_UNAVAILABLE ->
                return@withContext LinuxInstallResult.EnvironmentUnavailable
            else -> return@withContext LinuxInstallResult.Failed(LinuxInstallStage.CHECKING)
        }

        val rootfs = LinuxEnvironmentPaths.rootfsDir(context)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
            val archive = File(context.cacheDir, artifact.fileName + ".download")
            try {
                onProgress(LinuxInstallProgress(LinuxInstallStage.DOWNLOADING))
                val downloaded = artifactDownloader.download(artifact, archive) { downloadedBytes, totalBytes ->
                    onProgress(
                        LinuxInstallProgress(
                            stage = LinuxInstallStage.DOWNLOADING,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                        ),
                    )
                }
                if (!downloaded) {
                    return@withContext LinuxInstallResult.Failed(LinuxInstallStage.DOWNLOADING)
                }
                coroutineContext.ensureActive()
                onProgress(LinuxInstallProgress(LinuxInstallStage.EXTRACTING))
                val extracted = installRootfs(artifact, archive, rootfs)
                if (!extracted) {
                    return@withContext LinuxInstallResult.Failed(LinuxInstallStage.EXTRACTING)
                }
            } finally {
                archive.delete()
            }
        }

        coroutineContext.ensureActive()
        onProgress(LinuxInstallProgress(LinuxInstallStage.INSTALLING_TOOLS))
        if (!installCommonTools(rootfs)) {
            return@withContext LinuxInstallResult.Failed(LinuxInstallStage.INSTALLING_TOOLS)
        }
        onProgress(LinuxInstallProgress(LinuxInstallStage.COMPLETE))
        LinuxInstallResult.Installed(artifact.version)
    }

    private suspend fun runPreflight(): InstallerCommandResult {
        val requiredApplets = listOf(
            "ash",
            "chroot",
            "gzip",
            "mount",
            "sha256sum",
            "tar",
            "unshare",
        ).joinToString(" ")
        val command = """
            if [ "${'$'}(id -u)" != 0 ]; then exit $PREFLIGHT_ROOT_UNAVAILABLE; fi
            ${AndroidBusyBox.discoveryScript()}
            if [ -z "${'$'}rikka_busybox" ]; then exit $PREFLIGHT_BUSYBOX_UNAVAILABLE; fi
            for eta_applet in $requiredApplets; do
              "${'$'}rikka_busybox" --list | "${'$'}rikka_busybox" grep -qx "${'$'}eta_applet" || exit $PREFLIGHT_BUSYBOX_INCOMPLETE
            done
            "${'$'}rikka_busybox" unshare -m --propagation private \
              "${'$'}rikka_busybox" chroot / /system/bin/sh -c ':' || exit $PREFLIGHT_ENVIRONMENT_UNAVAILABLE
        """.trimIndent()
        return InstallerShellRunner.run(
            command = command,
            timeoutSeconds = 15,
            environment = TerminalEnvironment.ANDROID,
        )
    }

    private suspend fun installRootfs(
        artifact: VerifiedArtifact,
        archive: File,
        rootfs: File,
    ): Boolean {
        val parent = rootfs.parentFile ?: return false
        val temporaryRootfs = File(parent, "rootfs.installing")
        val markerBody = "version=${artifact.version}\\nsha256=${artifact.sha256}\\n"
        val command = """
            ${AndroidBusyBox.discoveryScript()}
            [ -n "${'$'}rikka_busybox" ] || exit 127
            eta_archive=${shellQuote(archive.absolutePath)}
            eta_parent=${shellQuote(parent.absolutePath)}
            eta_rootfs=${shellQuote(rootfs.absolutePath)}
            eta_temporary=${shellQuote(temporaryRootfs.absolutePath)}
            eta_actual_sha=${'$'}("${'$'}rikka_busybox" sha256sum "${'$'}eta_archive" | "${'$'}rikka_busybox" awk '{print ${'$'}1}')
            [ "${'$'}eta_actual_sha" = ${shellQuote(artifact.sha256)} ] || exit 65
            "${'$'}rikka_busybox" mkdir -p "${'$'}eta_parent" || exit 66
            "${'$'}rikka_busybox" rm -rf "${'$'}eta_temporary"
            "${'$'}rikka_busybox" mkdir -p "${'$'}eta_temporary" || exit 66
            "${'$'}rikka_busybox" tar -xzf "${'$'}eta_archive" -C "${'$'}eta_temporary" || exit 67
            [ -x "${'$'}eta_temporary/bin/busybox" ] || exit 68
            "${'$'}rikka_busybox" mkdir -p \
              "${'$'}eta_temporary/proc" \
              "${'$'}eta_temporary/sys" \
              "${'$'}eta_temporary/dev" \
              "${'$'}eta_temporary/workspace" \
              "${'$'}eta_temporary/storage/emulated/0" \
              "${'$'}eta_temporary/data/local/tmp" \
              "${'$'}eta_temporary/tmp"
            "${'$'}rikka_busybox" chmod 1777 "${'$'}eta_temporary/tmp"
            "${'$'}rikka_busybox" rm -f "${'$'}eta_temporary/sdcard"
            "${'$'}rikka_busybox" ln -s /storage/emulated/0 "${'$'}eta_temporary/sdcard"
            cat > "${'$'}eta_temporary/etc/resolv.conf" <<'ETA_RESOLV_EOF'
            nameserver 1.1.1.1
            nameserver 8.8.8.8
            ETA_RESOLV_EOF
            cat > "${'$'}eta_temporary/etc/apk/repositories" <<'ETA_REPOSITORIES_EOF'
            https://dl-cdn.alpinelinux.org/alpine/v3.24/main
            https://dl-cdn.alpinelinux.org/alpine/v3.24/community
            ETA_REPOSITORIES_EOF
            printf ${shellQuote(markerBody)} > "${'$'}eta_temporary/${LinuxEnvironmentPaths.READY_MARKER}"
            "${'$'}rikka_busybox" chmod 0644 "${'$'}eta_temporary/${LinuxEnvironmentPaths.READY_MARKER}"
            "${'$'}rikka_busybox" rm -rf "${'$'}eta_rootfs"
            "${'$'}rikka_busybox" mv "${'$'}eta_temporary" "${'$'}eta_rootfs" || exit 69
        """.trimIndent()
        val result = InstallerShellRunner.run(
            command = command,
            timeoutSeconds = 120,
            environment = TerminalEnvironment.ANDROID,
        )
        TerminalLogger.info(
            "Alpine environment action=extract outcome=${if (result.exitCode == 0) "succeeded" else "failed"} " +
                "exitCode=${result.exitCode} outputChars=${result.output.length}",
        )
        return result.exitCode == 0 && LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)
    }

    private suspend fun installCommonTools(rootfs: File): Boolean {
        val packages = AGENT_PACKAGES.joinToString(" ")
        val command = """
            apk update
            apk add --no-cache $packages
            cat > /${LinuxEnvironmentPaths.COMMON_TOOLS_MARKER} <<'RIKKAHUB_TOOLSET_EOF'
            alpine=$ALPINE_VERSION
            toolset=${LinuxEnvironmentPaths.TOOLSET_REVISION}
            profiles=agent
            RIKKAHUB_TOOLSET_EOF
            chmod 0644 /${LinuxEnvironmentPaths.COMMON_TOOLS_MARKER}
        """.trimIndent()
        val result = InstallerShellRunner.run(
            command = command,
            timeoutSeconds = COMMON_TOOLS_TIMEOUT_SECONDS,
            environment = TerminalEnvironment.LINUX,
            linuxRootfsPath = rootfs.absolutePath,
        )
        TerminalLogger.info(
            "Alpine environment action=install_tools " +
                "outcome=${if (result.exitCode == 0) "succeeded" else "failed"} " +
                "exitCode=${result.exitCode} outputChars=${result.output.length}",
        )
        return result.exitCode == 0 && LinuxEnvironmentPaths.commonToolsReady(rootfs.absolutePath)
    }

    private fun readInstalledVersion(rootfs: File): String? =
        runCatching {
            File(rootfs, LinuxEnvironmentPaths.READY_MARKER)
                .readLines()
                .firstOrNull { line -> line.startsWith("version=") }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { value -> value.matches(Regex("[0-9]+(?:\\.[0-9]+){1,2}")) }
        }.getOrNull()

    companion object {
        private const val ALPINE_VERSION = "3.24.1"
        private const val COMMON_TOOLS_TIMEOUT_SECONDS = 600L
        private const val PREFLIGHT_ROOT_UNAVAILABLE = 40
        private const val PREFLIGHT_BUSYBOX_UNAVAILABLE = 41
        private const val PREFLIGHT_BUSYBOX_INCOMPLETE = 42
        private const val PREFLIGHT_ENVIRONMENT_UNAVAILABLE = 43

        private val installMutex = Mutex()

        internal val AGENT_PACKAGES = listOf(
            "bash",
            "ca-certificates",
            "coreutils",
            "curl",
            "diffutils",
            "fd",
            "file",
            "findutils",
            "gawk",
            "git",
            "grep",
            "gzip",
            "jq",
            "less",
            "openssl",
            "openssh-client-default",
            "patch",
            "procps-ng",
            "ripgrep",
            "rsync",
            "sed",
            "sqlite",
            "tar",
            "unzip",
            "util-linux",
            "wget",
            "xz",
            "zip",
            "zstd",
        )

        private val HEALTH_CHECK_COMMANDS = listOf(
            "bash",
            "curl",
            "diff",
            "fd",
            "git",
            "jq",
            "patch",
            "rg",
            "rsync",
            "sqlite3",
            "ssh",
        )

        internal fun artifactForAbis(abis: List<String>): VerifiedArtifact? =
            abis.firstNotNullOfOrNull { abi ->
                when (abi) {
                    "arm64-v8a" -> VerifiedArtifact(
                        id = "alpine-minirootfs-aarch64",
                        version = ALPINE_VERSION,
                        fileName = "alpine-minirootfs-$ALPINE_VERSION-aarch64.tar.gz",
                        url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/" +
                            "alpine-minirootfs-$ALPINE_VERSION-aarch64.tar.gz",
                        sha256 = "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259",
                        sizeBytes = 4_023_732L,
                    )
                    "x86_64" -> VerifiedArtifact(
                        id = "alpine-minirootfs-x86_64",
                        version = ALPINE_VERSION,
                        fileName = "alpine-minirootfs-$ALPINE_VERSION-x86_64.tar.gz",
                        url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/" +
                            "alpine-minirootfs-$ALPINE_VERSION-x86_64.tar.gz",
                        sha256 = "41f73e3cf5fa919b8aa5ca6b30dc48f0da2720776d7423e2a7748211456fe081",
                        sizeBytes = 3_698_422L,
                    )
                    else -> null
                }
            }

    }
}

internal data class InstallerCommandResult(
    val exitCode: Int,
    val output: String,
)

internal object InstallerShellRunner {
    private const val MAX_OUTPUT_BYTES = 64 * 1024

    suspend fun run(
        command: String,
        timeoutSeconds: Long,
        environment: TerminalEnvironment,
        linuxRootfsPath: String? = null,
    ): InstallerCommandResult = runInterruptible(Dispatchers.IO) {
        val supervisor = ShellProcessSupervisor()
        val process = supervisor.startShellProcess(
            identity = "root",
            command = command,
            mergeStderr = true,
            environment = environment,
            linuxRootfsPath = linuxRootfsPath,
        ) ?: return@runInterruptible InstallerCommandResult(exitCode = -1, output = "")
        val output = ByteArrayOutputStream()
        val reader = thread(name = "rikkahub-linux-installer-output", isDaemon = true) {
            runCatching {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            val remaining = (MAX_OUTPUT_BYTES - output.size()).coerceAtLeast(0)
                            if (remaining > 0) output.write(buffer, 0, count.coerceAtMost(remaining))
                        }
                    }
                }
            }
        }
        runCatching { process.outputStream.close() }
        try {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                supervisor.terminateProcessTree(process)
                reader.join(1_000)
                InstallerCommandResult(exitCode = -2, output = output.text())
            } else {
                reader.join(1_000)
                InstallerCommandResult(exitCode = process.exitValue(), output = output.text())
            }
        } finally {
            if (process.isAlive) {
                supervisor.terminateAndReap(process)
            } else {
                supervisor.reapProcess(process)
            }
            supervisor.unregisterProcess(process)
        }
    }

    private fun ByteArrayOutputStream.text(): String =
        synchronized(this) { toByteArray().decodeToString().trimEnd() }
}
