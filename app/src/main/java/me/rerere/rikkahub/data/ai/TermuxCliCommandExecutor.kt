package me.rerere.rikkahub.data.ai

import android.content.Context
import me.rerere.ai.provider.providers.reasonix.CliCommandExecutor
import me.rerere.rikkahub.data.ai.tools.local.CaptureResult
import me.rerere.rikkahub.data.ai.tools.local.runCommandCapture
import me.rerere.rikkahub.data.preferences.TermuxDefaults

private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"

/**
 * [CliCommandExecutor] 的 Termux 本地实现——通过 Termux RUN_COMMAND 服务执行 CLI 命令。
 *
 * CLI 后端（backendType=cli）的「生成」= 在手机 Termux 里执行一条命令行工具，拿标准输出
 * 作为回复。后续可扩展 SSH 远程执行（复用 SshHostRepository）。
 */
class TermuxCliCommandExecutor(
    private val context: Context,
) : CliCommandExecutor {
    override suspend fun execute(command: String, prompt: String): String {
        val result =
            runCommandCapture(
                ctx = context,
                executable = TERMUX_BASH,
                arguments = arrayOf("-c", command),
                workingDir = TermuxDefaults.DEFAULT_WORKING_DIR,
            )
        return when (result) {
            is CaptureResult.Success ->
                if (result.stdout.isNotBlank()) result.stdout.trim() else result.stderr.trim()
            is CaptureResult.Timeout -> "CLI 命令执行超时"
            is CaptureResult.Denied -> "CLI 命令执行被拒绝（Termux 未授权）"
        }
    }
}
