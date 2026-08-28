package me.rerere.rikkahub.data.ai.tools

import java.nio.file.Files
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRootTerminalToolsTest {
    @Test
    fun `catalog exposes the complete Eta terminal contract`() {
        val root = Files.createTempDirectory("terminal-tools-").toFile()
        AndroidRootTerminalController(root, root).use { controller ->
            assertEquals(
                listOf("terminal", "run_command", "read_file", "write_file", "list_directory"),
                createAndroidRootTerminalTools(controller, requireApproval = true).map { it.name },
            )
        }
    }

    @Test
    fun `only terminal command execution requires approval`() {
        assertTrue(terminalNeedsApproval("exec", true))
        assertTrue(terminalNeedsApproval("open_and_exec", true))
        assertFalse(terminalNeedsApproval("open", true))
        assertFalse(terminalNeedsApproval("read_async_result", true))
        assertFalse(terminalNeedsApproval("close", true))
        assertFalse(terminalNeedsApproval("exec", false))
    }
}
