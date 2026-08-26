package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRootTerminalToolsTest {
    @Test
    fun `action is required and validated`() {
        val missing = runCatching { rootTerminalAction(buildJsonObject {}) }.exceptionOrNull()
        assertTrue(missing is IllegalStateException)

        val invalid = runCatching {
            rootTerminalAction(buildJsonObject { put("action", "unknown") })
        }.exceptionOrNull()
        assertTrue(invalid is IllegalArgumentException)
    }

    @Test
    fun `only command actions require approval`() {
        assertTrue(rootTerminalNeedsApproval(buildJsonObject { put("action", "run") }, true))
        assertTrue(rootTerminalNeedsApproval(buildJsonObject { put("action", "start") }, true))
        assertFalse(rootTerminalNeedsApproval(buildJsonObject { put("action", "status") }, true))
        assertFalse(rootTerminalNeedsApproval(buildJsonObject { put("action", "read") }, true))
        assertFalse(rootTerminalNeedsApproval(buildJsonObject { put("action", "run") }, false))
        assertEquals("close", rootTerminalAction(buildJsonObject { put("action", "close") }))
    }
}
