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

    @Test fun `device state commands match Eta`() {
        assertEquals("cmd wifi set-wifi-enabled disabled", setDeviceStateCommand("wifi", false))
        assertEquals("cmd bluetooth_manager enable", setDeviceStateCommand("bluetooth", true))
    }

    @Test fun `settings commands match Eta`() {
        assertEquals(
            "settings --user current get 'global' 'airplane_mode_on'",
            getSettingCommand("global", "airplane_mode_on"),
        )
        assertEquals(
            "settings --user current put 'system' 'haptic_feedback_enabled' '1'",
            setSettingCommand("system", "haptic_feedback_enabled", "1"),
        )
    }
}
