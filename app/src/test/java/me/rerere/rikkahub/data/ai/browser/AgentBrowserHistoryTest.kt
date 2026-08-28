package me.rerere.rikkahub.data.ai.browser

import org.junit.Assert.*
import org.junit.Test

class AgentBrowserHistoryTest {
    @Test fun `history target preserves back and forward entries`() {
        assertEquals(0, browserHistoryTargetIndex(1, 2, true))
        assertEquals(1, browserHistoryTargetIndex(0, 2, false))
        assertNull(browserHistoryTargetIndex(0, 2, true))
        assertNull(browserHistoryTargetIndex(1, 2, false))
    }

    @Test fun `history completion accepts index or committed target url`() {
        val target = BrowserHistoryTarget(0, "https://example.com/")
        assertTrue(browserHistoryReached(target, 0, "https://other.test/"))
        assertTrue(browserHistoryReached(target, 1, "https://example.com/"))
        assertFalse(browserHistoryReached(target, 1, "https://other.test/"))
    }
}
