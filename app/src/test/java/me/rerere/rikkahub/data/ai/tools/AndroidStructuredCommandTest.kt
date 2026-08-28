package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStructuredCommandTest {
    @Test fun `memory command bypasses BusyBox ps option incompatibility`() {
        val command = topMemoryAppsCommand(10)
        assertTrue(command.startsWith("/system/bin/ps -A -o rss,comm"))
        assertTrue(command.endsWith("head -n 10"))
    }

    @Test fun `settings read uses Android settings binary`() {
        assertEquals(
            "/system/bin/settings get global bluetooth_on",
            getSettingCommand("global", "bluetooth_on"),
        )
    }

    @Test fun `device state commands use Android service entry points`() {
        assertEquals(
            "/system/bin/cmd wifi set-wifi-enabled disabled",
            setDeviceStateCommand("wifi", false),
        )
        assertEquals(
            "/system/bin/cmd bluetooth_manager enable",
            setDeviceStateCommand("bluetooth", true),
        )
    }
}
