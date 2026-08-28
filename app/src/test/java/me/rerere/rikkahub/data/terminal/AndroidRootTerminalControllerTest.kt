package me.rerere.rikkahub.data.terminal

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRootTerminalControllerTest {
    private fun dirs() = Files.createTempDirectory("root-terminal-test-").toFile()
    private fun controller(root: File = dirs(), logger: TerminalLog = NoopLogger) =
        AndroidRootTerminalController(root, root, logger = logger)

    @Test fun `invalid commands and environment values are rejected before process start`() {
        controller().use { terminal ->
            assertThrows(IllegalArgumentException::class.java) { terminal.executeSync("   ") }
            assertThrows(IllegalArgumentException::class.java) { terminal.executeSync("x".repeat(4_001)) }
            assertThrows(IllegalArgumentException::class.java) {
                terminal.action("open_and_exec", command = "true", environment = "unknown")
            }
        }
    }

    @Test fun `persistent session keeps shell state`() {
        val root = dirs()
        controller(root).use { terminal ->
            val opened = JSONObject(terminal.action("open", cwd = root.absolutePath, identity = "user"))
            assertTrue(opened.toString(), opened.getBoolean("ok"))
            val id = opened.getString("session_id")
            val exported = JSONObject(terminal.action("exec", command = "export RIKKA_TEST_VALUE=streaming", sessionId = id, identity = "user"))
            val echoed = JSONObject(terminal.action("exec", command = "printf %s \"\$RIKKA_TEST_VALUE\"", sessionId = id, identity = "user"))
            assertTrue(exported.toString(), exported.getBoolean("ok"))
            assertEquals("streaming", echoed.getString("stdout"))
        }
    }

    @Test fun `async command cannot reuse persistent session`() {
        val root = dirs()
        controller(root).use { terminal ->
            val opened = JSONObject(terminal.action("open", cwd = root.absolutePath, identity = "user"))
            val result = JSONObject(terminal.action("exec", command = "sleep 1", sessionId = opened.getString("session_id"), identity = "user", async = true))
            assertFalse(result.toString(), result.getBoolean("ok"))
            assertEquals("ASYNC_SESSION_UNSUPPORTED", result.getString("code"))
        }
    }

    @Test fun `terminal logs never contain commands or working directories`() {
        val root = dirs()
        val logger = RecordingLogger()
        val command = "printf sensitive_command_marker"
        controller(root, logger).use { terminal ->
            val result = JSONObject(terminal.terminalOpenAndExec(command, root.absolutePath, 5_000, "user", false))
            assertTrue(result.toString(), result.getBoolean("ok"))
        }
        val logs = logger.messages.joinToString("\n")
        assertTrue(logs, logs.contains("action=open_and_exec"))
        assertTrue(logs, logs.contains("commandChars=${command.length}"))
        assertFalse(logs, logs.contains("sensitive_command_marker"))
        assertFalse(logs, logs.contains(root.absolutePath))
    }

    @Test fun `linux environment reports missing installation and rejects user identity`() {
        val root = dirs()
        controller(root).use { terminal ->
            val missing = JSONObject(terminal.action("open_and_exec", command = "true", cwd = root.absolutePath, environment = "linux"))
            assertFalse(missing.toString(), missing.getBoolean("ok"))
            assertEquals("LINUX_ENVIRONMENT_NOT_READY", missing.getString("code"))

            File(root, "terminal/alpine/rootfs/bin").mkdirs()
            File(root, "terminal/alpine/rootfs/bin/busybox").writeText("busybox")
            File(root, "terminal/alpine/rootfs/${LinuxEnvironmentPaths.READY_MARKER}").writeText("ready")
            val user = JSONObject(terminal.action("open_and_exec", command = "true", cwd = root.absolutePath, identity = "user", environment = "linux"))
            assertFalse(user.toString(), user.getBoolean("ok"))
            assertEquals("LINUX_ENVIRONMENT_REQUIRES_ROOT", user.getString("code"))
        }
    }

    private fun AndroidRootTerminalController.action(
        action: String, command: String = "", cwd: String? = null, identity: String = "root",
        environment: String = "android", sessionId: String? = null, jobId: String? = null, async: Boolean = false,
    ) = terminalAction(action, command, cwd, 5_000, identity, false, sessionId, jobId, async, 0, 8_000, false, environment)

    private object NoopLogger : TerminalLog {
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
    }

    private class RecordingLogger : TerminalLog {
        val messages = mutableListOf<String>()
        override fun info(message: String) { messages += message }
        override fun warn(message: String) { messages += message }
    }
}
