package me.rerere.rikkahub.data.terminal

import org.junit.Test

class TerminalLogTest {
    @Test
    fun `platform logging never changes terminal control flow on host JVM`() {
        TerminalLogger.info("safe info")
        TerminalLogger.warn("safe warning")
    }
}
