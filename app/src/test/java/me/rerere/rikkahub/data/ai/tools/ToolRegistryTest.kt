package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    private fun tool(name: String, fail: Boolean = false) = Tool(
        name = name,
        description = "test",
        execute = { _: JsonElement ->
            if (fail) error("boom")
            listOf(UIMessagePart.Text("ok"))
        },
    )

    @Test fun `duplicate names are rejected`() {
        val error = runCatching { normalizeToolRegistry(listOf(tool("x"), tool("x"))) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun `tool failures become structured results`() = runBlocking {
        val result = normalizeToolRegistry(listOf(tool("x", fail = true))).single().execute(kotlinx.serialization.json.JsonObject(emptyMap())).single()
        assertTrue(result is UIMessagePart.Text)
        assertTrue((result as UIMessagePart.Text).text.contains("TOOL_EXECUTION_FAILED"))
        assertTrue(result.text.contains("\"ok\":false"))
    }
    @Test fun `stable first party error codes are preserved`() = runBlocking {
        val coded = Tool(name = "coded", description = "test", execute = { error("ACTION_OUTCOME_UNKNOWN") })
        val result = normalizeToolRegistry(listOf(coded)).single()
            .execute(kotlinx.serialization.json.JsonObject(emptyMap())).single() as UIMessagePart.Text
        assertTrue(result.text.contains("\"code\":\"ACTION_OUTCOME_UNKNOWN\""))
    }

}
