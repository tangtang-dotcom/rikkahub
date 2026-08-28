package me.rerere.rikkahub.data.ai.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentBrowserHistoryTest {
    @Test fun `history target preserves back and forward entries`() {
        assertEquals(0, browserHistoryTargetIndex(currentIndex = 1, size = 2, backwards = true))
        assertEquals(1, browserHistoryTargetIndex(currentIndex = 0, size = 2, backwards = false))
        assertNull(browserHistoryTargetIndex(currentIndex = 0, size = 2, backwards = true))
        assertNull(browserHistoryTargetIndex(currentIndex = 1, size = 2, backwards = false))
    }
}
