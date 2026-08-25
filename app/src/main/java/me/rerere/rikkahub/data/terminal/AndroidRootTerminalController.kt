package me.rerere.rikkahub.data.terminal

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class TerminalEnvironment { ANDROID, LINUX }

/** Runs commands in Android's real root namespace through the device su implementation. */
class AndroidRootTerminalController(private val context: Context) : AutoCloseable {
    private val cacheDir get() = context.cacheDir
    companion object {
        private const val DEFAULT_CWD = "/data/local/tmp/rikkahub"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_TIMEOUT_MS = 600_000L
        private const val MAX_COMMAND_CHARS = 16_000
        private const val MAX_CAPTURE_BYTES = 128 * 1024
        private const val MAX_RETURN_CHARS = 16_000
    }

    private val jobs = ConcurrentHashMap<String, TerminalJob>()
    @Volatile private var closing = false

    fun rootStatus(): TerminalResult = executeSync("id -u", DEFAULT_CWD, 10_000, false)

    fun executeSync(command: String, cwd: String? = null, timeoutMs: Long = DEFAULT_TIMEOUT_MS, mergeStderr: Boolean = false, environment: TerminalEnvironment = TerminalEnvironment.ANDROID): TerminalResult {
        check(!closing) { "Root terminal is closed" }
        val running = start(command, cwd, timeoutMs, mergeStderr, environment)
        val finished = running.process.waitFor(running.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) terminate(running)
        running.stdoutThread.join(1_000)
        running.stderrThread.join(1_000)
        cleanup(running)
        return running.result(timedOut = !finished)
    }

    fun executeAsync(command: String, cwd: String? = null, timeoutMs: Long = DEFAULT_TIMEOUT_MS, mergeStderr: Boolean = false, environment: TerminalEnvironment = TerminalEnvironment.ANDROID): String {
        check(!closing) { "Root terminal is closed" }
        val running = start(command, cwd, timeoutMs, mergeStderr, environment)
        val id = "root_" + UUID.randomUUID().toString().take(8)
        val job = TerminalJob(id, running)
        jobs[id] = job
        job.waiter = thread(name = "rikkahub-root-wait-$id", isDaemon = true) {
            val finished = running.process.waitFor(running.timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                job.timedOut = true
                terminate(running)
            }
            running.stdoutThread.join(1_000)
            running.stderrThread.join(1_000)
            cleanup(running)
            job.completed = true
        }
        return id
    }

    fun readJob(id: String, offset: Int = 0, maxChars: Int = MAX_RETURN_CHARS): AsyncTerminalResult {
        val job = jobs[id] ?: error("Unknown root terminal job: $id")
        val result = job.running.result(job.timedOut)
        val all = result.stdout
        val safeOffset = offset.coerceIn(0, all.length)
        val end = (safeOffset + maxChars.coerceIn(1, MAX_RETURN_CHARS)).coerceAtMost(all.length)
        return AsyncTerminalResult(id, !job.completed, result.exitCode, result.timedOut, all.substring(safeOffset, end), result.stderr, end, all.length, result.truncated || end < all.length)
    }

    fun closeJob(id: String): Boolean {
        val job = jobs.remove(id) ?: return false
        if (!job.completed) terminate(job.running)
        job.waiter?.join(1_000)
        cleanup(job.running)
        return true
    }

    override fun close() {
        closing = true
        jobs.keys.toList().forEach(::closeJob)
    }

    private fun start(command: String, cwd: String?, timeoutMs: Long, mergeStderr: Boolean, environment: TerminalEnvironment): RunningCommand {
        val trimmed = command.trim()
        require(trimmed.isNotEmpty()) { "command is required" }
        require(trimmed.length <= MAX_COMMAND_CHARS) { "command is too long" }
        val workingDir = cwd?.trim()?.takeIf(String::isNotEmpty) ?: if (environment == TerminalEnvironment.LINUX) "/workspace" else DEFAULT_CWD
        require(workingDir.startsWith('/')) { "cwd must be an absolute path" }
        require(workingDir.indexOf('\u0000') < 0) { "cwd contains NUL" }

        val ownerDir = File(cacheDir, "root_terminal").apply { mkdirs() }
        val ownerFile = File(ownerDir, UUID.randomUUID().toString() + ".owner")
        val token = UUID.randomUUID().toString().replace("-", "")
        val commandBody = if (environment == TerminalEnvironment.LINUX) buildLinuxCommand(trimmed, workingDir) else trimmed
        val body = (if (environment == TerminalEnvironment.ANDROID) "mkdir -p ${quote(workingDir)} && cd ${quote(workingDir)} || exit 126\n" else "") +
            "export TERM=dumb NO_COLOR=1 RIKKAHUB_PROCESS_OWNER=${quote(token)}\n" +
            "$commandBody\nrikkahub_status=\$?\nwait\nexit \$rikkahub_status"
        val groupBody = "printf '%s group\\n' \"\$\$\" > ${quote(ownerFile.absolutePath)}; $body"
        val treeBody = "printf '%s tree\\n' \"\$\$\" > ${quote(ownerFile.absolutePath)}; $body"
        val launcher = "export RIKKAHUB_PROCESS_OWNER=${quote(token)}; " +
            "if command -v setsid >/dev/null 2>&1; then exec setsid sh -c ${quote(groupBody)}; else $treeBody; fi"
        val process = ProcessBuilder("su", "-c", launcher).redirectErrorStream(mergeStderr).start()
        val stdout = BoundedCollector(MAX_CAPTURE_BYTES)
        val stderr = BoundedCollector(MAX_CAPTURE_BYTES)
        val outThread = thread(name = "rikkahub-root-stdout", isDaemon = true) { process.inputStream.use(stdout::readFrom) }
        val errThread = thread(name = "rikkahub-root-stderr", isDaemon = true) { process.errorStream.use(stderr::readFrom) }
        val running = RunningCommand(process, stdout, stderr, outThread, errThread, ownerFile, token, timeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS), mergeStderr)
        resolveOwnership(running)
        return running
    }

    private fun buildLinuxCommand(command: String, cwd: String): String {
        val rootfs = File(context.filesDir, "terminal/alpine/rootfs")
        require(File(rootfs, ".rikkahub-environment-ready").isFile) { "全局 Linux 环境尚未安装" }
        val inner = """
            bb=${'$'}1; root=${'$'}2
            "${'$'}bb" mount -t proc proc "${'$'}root/proc" || exit 125
            "${'$'}bb" mount -o rbind /dev "${'$'}root/dev" || exit 125
            "${'$'}bb" mount -o rbind /sys "${'$'}root/sys" 2>/dev/null || true
            "${'$'}bb" mount -o bind /storage/emulated/0 "${'$'}root/storage/emulated/0" 2>/dev/null || true
            "${'$'}bb" mount -o bind /data/local/tmp "${'$'}root/data/local/tmp" || exit 125
            "${'$'}bb" mkdir -p /data/local/tmp/rikkahub
            "${'$'}bb" mount -o bind /data/local/tmp/rikkahub "${'$'}root/workspace" || exit 125
            exec "${'$'}bb" chroot "${'$'}root" /usr/bin/env -i HOME=/root USER=root LOGNAME=root TERM=dumb NO_COLOR=1 LANG=C.UTF-8 LC_ALL=C.UTF-8 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/sh -lc ${quote("cd -- ${quote(cwd)} && $command")}
        """.trimIndent()
        val discovery = "bb=''; for p in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /system/xbin/busybox /system/bin/busybox; do [ -x \"\$p\" ] && bb=\"\$p\" && break; done"
        return "$discovery; [ -n \"\$bb\" ] || { echo BUSYBOX_MISSING >&2; exit 127; }; \"\$bb\" unshare -m --propagation private \"\$bb\" sh -c ${quote(inner)} linux \"\$bb\" ${quote(rootfs.absolutePath)}"
    }

    private fun resolveOwnership(running: RunningCommand) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750)
        while (System.nanoTime() < deadline) {
            val text = runCatching { running.ownerFile.readText().trim() }.getOrDefault("")
            val parts = text.split(Regex("\\s+"))
            val pid = parts.getOrNull(0)?.toLongOrNull()
            val mode = parts.getOrNull(1)
            if (pid != null && pid > 1 && (mode == "group" || mode == "tree")) {
                running.ownerPid = pid
                running.isProcessGroup = mode == "group"
                running.ownerFile.delete()
                return
            }
            if (!running.process.isAlive) return
            Thread.sleep(10)
        }
    }

    private fun terminate(running: RunningCommand) {
        val pid = running.ownerPid
        if (pid != null) {
            val proof = "[ -r /proc/$pid/environ ] && tr '\\000' '\\n' < /proc/$pid/environ | grep -Fqx " + quote("RIKKAHUB_PROCESS_OWNER=${running.token}")
            val signal = if (running.isProcessGroup) {
                "kill -TERM -$pid 2>/dev/null; sleep 0.2; kill -KILL -$pid 2>/dev/null"
            } else {
                "kill_tree() { children=\$(ps -A -o PID,PPID 2>/dev/null | awk -v p=\"\$1\" '\$2 == p {print \$1}'); " +
                    "for child in \$children; do kill_tree \"\$child\"; done; kill -KILL \"\$1\" 2>/dev/null; }; kill_tree $pid"
            }
            val kill = "if $proof; then $signal; fi; true"
            runCatching {
                ProcessBuilder("su", "-c", kill).start().also {
                    it.outputStream.close()
                    it.waitFor(2, TimeUnit.SECONDS)
                    if (it.isAlive) it.destroyForcibly()
                }
            }
        }
        runCatching { running.process.destroy() }
        if (running.process.isAlive) runCatching { running.process.destroyForcibly() }
        runCatching { running.process.waitFor(1, TimeUnit.SECONDS) }
    }

    private fun cleanup(running: RunningCommand) {
        running.ownerFile.delete()
        runCatching { running.process.outputStream.close() }
        runCatching { running.process.inputStream.close() }
        runCatching { running.process.errorStream.close() }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private class RunningCommand(
        val process: Process, val stdout: BoundedCollector, val stderr: BoundedCollector,
        val stdoutThread: Thread, val stderrThread: Thread, val ownerFile: File, val token: String,
        val timeoutMs: Long, val mergeStderr: Boolean,
    ) {
        @Volatile var ownerPid: Long? = null
        @Volatile var isProcessGroup: Boolean = false
        fun result(timedOut: Boolean) = TerminalResult(
            if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull(), timedOut,
            stdout.text(), if (mergeStderr) "" else stderr.text(), stdout.truncated || stderr.truncated,
        )
    }

    private class TerminalJob(val id: String, val running: RunningCommand) {
        @Volatile var completed = false
        @Volatile var timedOut = false
        @Volatile var waiter: Thread? = null
    }

    private class BoundedCollector(private val limit: Int) {
        private val bytes = ByteArrayOutputStream()
        @Volatile var truncated = false
            private set
        fun readFrom(input: InputStream) {
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                synchronized(this) {
                    val available = (limit - bytes.size()).coerceAtLeast(0)
                    val keep = count.coerceAtMost(available)
                    if (keep > 0) bytes.write(buffer, 0, keep)
                    if (keep < count) truncated = true
                }
            }
        }
        @Synchronized fun text(): String = bytes.toByteArray().toString(Charsets.UTF_8)
    }
}

data class TerminalResult(val exitCode: Int?, val timedOut: Boolean, val stdout: String, val stderr: String, val truncated: Boolean)
data class AsyncTerminalResult(val id: String, val running: Boolean, val exitCode: Int?, val timedOut: Boolean, val stdout: String, val stderr: String, val nextOffset: Int, val totalChars: Int, val truncated: Boolean)
