package me.rerere.rikkahub.data.terminal

import java.nio.file.Files
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidRootTerminalControllerTest {
    private fun controller(): AndroidRootTerminalController =
        AndroidRootTerminalController(Files.createTempDirectory("root-terminal-test-").toFile())

    @Test
    fun `empty command is rejected before su starts`() {
        val controller = controller()
        controller.use {
            assertThrows(IllegalArgumentException::class.java) {
                controller.executeSync("   ")
            }
        }
    }

    @Test
    fun `working directory must be absolute`() {
        val controller = controller()
        controller.use {
            assertThrows(IllegalArgumentException::class.java) {
                controller.executeSync("id", "relative/path")
            }
        }
    }

    @Test
    fun `command length is bounded`() {
        val controller = controller()
        controller.use {
            assertThrows(IllegalArgumentException::class.java) {
                controller.executeSync("x".repeat(16_001))
            }
        }
    }
}
