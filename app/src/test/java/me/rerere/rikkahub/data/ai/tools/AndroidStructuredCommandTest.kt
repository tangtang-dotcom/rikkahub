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

    @Test
    fun `settings command has cmd fallback and normalizes output`() {
        assertEquals(
            "value=\$(/system/bin/settings get global airplane_mode_on 2>/dev/null); if [ -n \"\$value\" ] && [ \"\$value\" != null ]; then printf '%s\\n' \"\$value\"; else /system/bin/cmd settings get global airplane_mode_on 2>/dev/null; fi",
            getSettingCommand("global", "airplane_mode_on"),
        )
        assertEquals("0", normalizeSettingValue("0\n"))
        assertEquals("1", normalizeSettingValue("warning\n1\n"))
        assertEquals(null, normalizeSettingValue("null\n"))
    }
}
