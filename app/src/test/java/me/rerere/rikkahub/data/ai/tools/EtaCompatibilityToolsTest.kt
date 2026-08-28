package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantMemory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaCompatibilityToolsTest {
    @Test
    fun `strict Eta compatibility catalog exposes split names`() {
        assertEquals(
            listOf(
                "observe_screen", "tap", "tap_area", "tap_element", "long_press", "long_press_element",
                "swipe", "scroll", "scroll_element", "search_apps", "launch_app", "open_uri", "read_image",
                "get_current_context", "skills_list", "skills_read",
            ),
            EtaCompatibilityToolNames.all,
        )
    }
    @Test
    fun `memory catalog exposes strict Eta read entry point`() {
        val tools = buildMemoryTools(
            json = Json,
            onCreation = { AssistantMemory(1, it) },
            onUpdate = { id, content -> AssistantMemory(id, content) },
            onDelete = {},
            onRead = { listOf(AssistantMemory(7, "Eta durable fact")) },
        )
        assertEquals(listOf("memory_get", "memory_tool"), tools.map { it.name })
    }

    @Test
    fun `memory_get reads real records with query and stable revision`() = runBlocking {
        val tool = buildMemoryTools(
            json = Json,
            onCreation = { AssistantMemory(1, it) },
            onUpdate = { id, content -> AssistantMemory(id, content) },
            onDelete = {},
            onRead = { listOf(AssistantMemory(7, "Eta durable fact"), AssistantMemory(8, "Other note")) },
        ).first { it.name == "memory_get" }
        val text = (tool.execute(buildJsonObject { put("query", "durable"); put("max_chars", 3000) }).single() as UIMessagePart.Text).text
        val result = JSONObject(text)
        assertTrue(result.getBoolean("ok"))
        assertEquals(1, result.getInt("matched_lines"))
        assertTrue(result.getString("content").contains("Eta durable fact"))
        assertEquals(64, result.getString("revision").length)
        assertFalse(result.getBoolean("has_more"))
    }
}
