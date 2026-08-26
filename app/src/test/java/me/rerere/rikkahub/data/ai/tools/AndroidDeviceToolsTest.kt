package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidDeviceToolsTest {
    @Test fun `device action is required and allowlisted`() {
        assertEquals("network", androidDeviceAction(buildJsonObject { put("action", "network") }))
        assertThrows(IllegalStateException::class.java) {
            androidDeviceAction(buildJsonObject {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            androidDeviceAction(buildJsonObject { put("action", "write_settings") })
        }
    }
}
