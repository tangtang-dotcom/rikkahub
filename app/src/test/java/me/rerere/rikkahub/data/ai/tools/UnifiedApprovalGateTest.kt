package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonNull
import me.rerere.ai.core.Tool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedApprovalGateTest {
    private fun tool(name: String, approval: Boolean) = Tool(
        name = name, description = name, needsApproval = { approval }, execute = { emptyList() },
    )

    @Test fun `disabled gate bypasses every non-root approval`() {
        val tools = applyNonRootApprovalGate(
            listOf(tool("launch_app", true), tool("mcp__server__write", true), tool("safe_read", false)), false,
        )
        tools.forEach { assertFalse(it.needsApproval(JsonNull)) }
    }

    @Test fun `enabled gate preserves conditional non-root approvals`() {
        val tools = applyNonRootApprovalGate(listOf(tool("launch_app", true), tool("safe_read", false)), true)
        assertTrue(tools[0].needsApproval(JsonNull))
        assertFalse(tools[1].needsApproval(JsonNull))
    }

    @Test fun `root terminal approvals stay independent`() {
        val tools = applyNonRootApprovalGate(
            listOf(tool("terminal", true), tool("run_command", true), tool("write_file", true)), false,
        )
        tools.forEach { assertTrue(it.needsApproval(JsonNull)) }
    }
}
