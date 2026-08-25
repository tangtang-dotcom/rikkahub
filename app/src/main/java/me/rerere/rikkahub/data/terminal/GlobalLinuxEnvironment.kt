package me.rerere.rikkahub.data.terminal

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LinuxInstallStage { CHECKING, DOWNLOADING, VERIFYING, EXTRACTING, INSTALLING_TOOLS, COMPLETE }
data class LinuxInstallProgress(val stage: LinuxInstallStage, val downloaded: Long = 0, val total: Long = 0)
data class LinuxEnvironmentStatus(val ready: Boolean, val baseReady: Boolean, val version: String?, val sizeBytes: Long)

/** One app-wide Root chroot. It is shared by every assistant and is not a security sandbox. */
class GlobalLinuxEnvironmentManager(
    private val context: Context,
    private val terminal: AndroidRootTerminalController,
) {
    val rootfs: File get() = File(context.filesDir, "terminal/alpine/rootfs")
    private val readyMarker get() = File(rootfs, ".rikkahub-environment-ready")
    private val toolsMarker get() = File(rootfs, ".rikkahub-common-tools-ready")

    fun status(): LinuxEnvironmentStatus {
        val base = readyMarker.isFile && File(rootfs, "bin/busybox").isFile
        val ready = base && toolsMarker.isFile
        val version = runCatching { readyMarker.readLines().firstOrNull { it.startsWith("version=") }?.substringAfter('=') }.getOrNull()
        return LinuxEnvironmentStatus(ready, base, version, if (rootfs.exists()) rootfs.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0)
    }

    suspend fun install(onProgress: suspend (LinuxInstallProgress) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(LinuxInstallProgress(LinuxInstallStage.CHECKING))
            val preflight = terminal.executeSync(
                """bb=''; for p in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /system/xbin/busybox /system/bin/busybox; do [ -x "${'$'}p" ] && bb="${'$'}p" && break; done; [ -n "${'$'}bb" ] || exit 41; [ "${'$'}(id -u)" = 0 ] || exit 40; "${'$'}bb" unshare -m --propagation private "${'$'}bb" chroot / /system/bin/sh -c :""",
                timeoutMs = 15_000,
                mergeStderr = true,
            )
            check(preflight.exitCode == 0) { if (preflight.exitCode == 41) "未找到 Magisk、KernelSU 或 APatch BusyBox" else "Root 环境不支持 chroot/unshare：${preflight.stdout}${preflight.stderr}" }
            if (!status().baseReady) installBase(onProgress)
            onProgress(LinuxInstallProgress(LinuxInstallStage.INSTALLING_TOOLS))
            val tools = terminal.executeSync(TOOLS_COMMAND, cwd = "/workspace", timeoutMs = 600_000, mergeStderr = true, environment = TerminalEnvironment.LINUX)
            check(tools.exitCode == 0 && !tools.timedOut) { tools.stdout.ifBlank { tools.stderr }.ifBlank { "Linux 工具安装失败" } }
            onProgress(LinuxInstallProgress(LinuxInstallStage.COMPLETE))
        }
    }

    suspend fun remove(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = terminal.executeSync("rm -rf ${quote(rootfs.parentFile!!.absolutePath)}", timeoutMs = 180_000, mergeStderr = true)
            check(result.exitCode == 0) { result.stdout + result.stderr }
        }
    }

    private suspend fun installBase(onProgress: suspend (LinuxInstallProgress) -> Unit) {
        val artifact = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> Artifact("aarch64", "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259", 4_023_732)
            Build.SUPPORTED_ABIS.contains("x86_64") -> Artifact("x86_64", "41f73e3cf5fa919b8aa5ca6b30dc48f0da2720776d7423e2a7748211456fe081", 3_698_422)
            else -> error("不支持的 CPU 架构：${Build.SUPPORTED_ABIS.joinToString()}")
        }
        val cache = File(context.cacheDir, "linux-installer/alpine-$VERSION-${artifact.arch}.tar.gz").apply { parentFile?.mkdirs() }
        try {
            onProgress(LinuxInstallProgress(LinuxInstallStage.DOWNLOADING, 0, artifact.size))
            val connection = (URL("https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/${artifact.arch}/alpine-minirootfs-$VERSION-${artifact.arch}.tar.gz").openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
            }
            connection.inputStream.use { input -> cache.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var done = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); done += count; onProgress(LinuxInstallProgress(LinuxInstallStage.DOWNLOADING, done, connection.contentLengthLong.takeIf { it > 0 } ?: artifact.size)) }
            } }
            onProgress(LinuxInstallProgress(LinuxInstallStage.VERIFYING))
            check(cache.sha256() == artifact.sha256) { "Alpine SHA-256 校验失败" }
            onProgress(LinuxInstallProgress(LinuxInstallStage.EXTRACTING))
            val parent = rootfs.parentFile!!
            val temp = File(parent, "rootfs.installing")
            val command = """
                bb=''; for p in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /system/xbin/busybox /system/bin/busybox; do [ -x "${'$'}p" ] && bb="${'$'}p" && break; done
                [ -n "${'$'}bb" ] || exit 41
                "${'$'}bb" rm -rf ${quote(temp.absolutePath)} && "${'$'}bb" mkdir -p ${quote(temp.absolutePath)}
                "${'$'}bb" tar -xzf ${quote(cache.absolutePath)} -C ${quote(temp.absolutePath)} || exit 67
                "${'$'}bb" mkdir -p ${quote(temp.absolutePath)}/proc ${quote(temp.absolutePath)}/sys ${quote(temp.absolutePath)}/dev ${quote(temp.absolutePath)}/workspace ${quote(temp.absolutePath)}/storage/emulated/0 ${quote(temp.absolutePath)}/data/local/tmp ${quote(temp.absolutePath)}/tmp
                "${'$'}bb" chmod 1777 ${quote(File(temp, "tmp").absolutePath)}
                printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > ${quote(File(temp, "etc/resolv.conf").absolutePath)}
                printf 'https://dl-cdn.alpinelinux.org/alpine/v3.24/main\nhttps://dl-cdn.alpinelinux.org/alpine/v3.24/community\n' > ${quote(File(temp, "etc/apk/repositories").absolutePath)}
                printf 'version=$VERSION\nsha256=${artifact.sha256}\n' > ${quote(File(temp, readyMarker.name).absolutePath)}
                "${'$'}bb" rm -rf ${quote(rootfs.absolutePath)} && "${'$'}bb" mv ${quote(temp.absolutePath)} ${quote(rootfs.absolutePath)}
            """.trimIndent()
            val extracted = terminal.executeSync(command, timeoutMs = 180_000, mergeStderr = true)
            check(extracted.exitCode == 0) { extracted.stdout + extracted.stderr }
        } finally { cache.delete() }
    }

    private fun File.sha256(): String { val digest = MessageDigest.getInstance("SHA-256"); inputStream().use { input -> val b = ByteArray(64 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }; return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private data class Artifact(val arch: String, val sha256: String, val size: Long)

    companion object {
        const val VERSION = "3.24.1"
        private val PACKAGES = listOf("bash", "ca-certificates", "coreutils", "curl", "diffutils", "fd", "file", "findutils", "gawk", "git", "grep", "gzip", "jq", "less", "openssl", "openssh-client-default", "patch", "procps-ng", "ripgrep", "rsync", "sed", "sqlite", "tar", "unzip", "util-linux", "wget", "xz", "zip", "zstd")
        private val TOOLS_COMMAND = "apk update && apk add --no-cache ${PACKAGES.joinToString(" ")} && printf 'toolset=1\\n' > /.rikkahub-common-tools-ready"
    }
}
