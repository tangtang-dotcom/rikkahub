package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

/** A root-capable tool for commands outside the PRoot workspace sandbox. */
fun createAndroidRootTerminalTools(
    controller: AndroidRootTerminalController,
): List<Tool> = listOf(
    Tool(
        name = "android_root_terminal",
        description = """
            Execute commands with real root privileges in the Android host environment (not the workspace/PRoot sandbox).
            Use action=run for normal commands. Use action=start for a long-running command, then action=read with job_id,
            and action=close to cancel or discard it. Use action=status to verify that su returns uid 0.
            Android absolute paths such as /data/adb, /data/data, /system and /storage/emulated/0 are supported.
            Approval behavior is controlled by the Root terminal setting.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { listOf("run", "start", "read", "close", "status").forEach { add(it) } })
                        put("description", "Terminal action. Defaults to run.")
                    })
                    put("command", buildJsonObject {
                        put("type", "string")
                        put("description", "Shell command for run/start")
                    })
                    put("cwd", buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute Android working directory; defaults to /data/local/tmp/rikkahub")
                    })
                    put("timeout_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "Timeout from 1000 to 180000 ms")
                    })
                    put("merge_stderr", buildJsonObject { put("type", "boolean") })
                    put("job_id", buildJsonObject { put("type", "string") })
                    put("offset", buildJsonObject { put("type", "integer") })
                    put("max_chars", buildJsonObject { put("type", "integer") })
                }
            )
        },
        execute = { input ->
            val p = input.jsonObject
            val action = p["action"]?.jsonPrimitive?.contentOrNull ?: "run"
            val command = p["command"]?.jsonPrimitive?.contentOrNull
            val cwd = p["cwd"]?.jsonPrimitive?.contentOrNull
            val timeout = (p["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 30_000L).coerceIn(1_000L, 180_000L)
            val merge = p["merge_stderr"]?.jsonPrimitive?.booleanOrNull ?: false
            val payload = withContext(Dispatchers.IO) {
                when (action) {
                    "status" -> {
                        val result = controller.rootStatus()
                        buildJsonObject {
                            put("ok", result.exitCode == 0 && !result.timedOut)
                            result.exitCode?.let { put("exit_code", it) }
                            put("timed_out", result.timedOut)
                            put("stdout", result.stdout)
                            put("stderr", result.stderr)
                            put("truncated", result.truncated)
                            put("root_available", result.exitCode == 0 && result.stdout.trim() == "0")
                        }
                    }
                    "run" -> {
                        val result = controller.executeSync(command ?: error("command is required"), cwd, timeout, merge)
                        buildResult(result.exitCode, result.timedOut, result.stdout, result.stderr, result.truncated)
                    }
                    "start" -> buildJsonObject {
                        put("ok", true)
                        put("job_id", controller.executeAsync(command ?: error("command is required"), cwd, timeout, merge))
                        put("running", true)
                    }
                    "read" -> {
                        val result = controller.readJob(
                            p["job_id"]?.jsonPrimitive?.contentOrNull ?: error("job_id is required"),
                            p["offset"]?.jsonPrimitive?.intOrNull ?: 0,
                            p["max_chars"]?.jsonPrimitive?.intOrNull ?: 16_000,
                        )
                        buildJsonObject {
                            put("ok", true); put("job_id", result.id); put("running", result.running)
                            result.exitCode?.let { put("exit_code", it) }
                            put("timed_out", result.timedOut); put("stdout", result.stdout); put("stderr", result.stderr)
                            put("next_offset", result.nextOffset); put("total_chars", result.totalChars); put("truncated", result.truncated)
                        }
                    }
                    "close" -> buildJsonObject {
                        put("ok", controller.closeJob(p["job_id"]?.jsonPrimitive?.contentOrNull ?: error("job_id is required")))
                    }
                    else -> error("Unsupported action: $action")
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    )
)

private fun buildResult(exitCode: Int?, timedOut: Boolean, stdout: String, stderr: String, truncated: Boolean) = buildJsonObject {
    put("ok", exitCode == 0 && !timedOut)
    exitCode?.let { put("exit_code", it) }
    put("timed_out", timedOut)
    put("stdout", stdout)
    put("stderr", stderr)
    put("truncated", truncated)
}
