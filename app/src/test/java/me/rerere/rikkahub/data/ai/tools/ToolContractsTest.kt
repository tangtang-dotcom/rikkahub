package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertThrows
import org.junit.Test

class ToolContractsTest {
    @Test fun `capability metadata is validated`() {
        validateCapability(ToolCapability("android.device_info"))
        assertThrows(IllegalArgumentException::class.java) { validateCapability(ToolCapability("bad name")) }
        assertThrows(IllegalArgumentException::class.java) { validateCapability(ToolCapability("x", version = 0)) }
    }
}
