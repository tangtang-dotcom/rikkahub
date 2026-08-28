package me.rerere.rikkahub.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCoordinateSpaceTest {
    @Test fun `screenshot pixels scale into physical display coordinates`() {
        val space = AccessibilityCoordinateSpace.Space(1080, 2400, 540, 1200)
        assertEquals(AccessibilityCoordinateSpace.Point(400, 1000), space.toScreen(200, 500))
        assertEquals(AccessibilityCoordinateSpace.Point(1078, 2398), space.toScreen(539, 1199))
    }

    @Test fun `screenshot points are range checked before conversion`() {
        val space = AccessibilityCoordinateSpace.Space(1080, 2400, 540, 1200)
        val error = runCatching { space.toScreen(540, 0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("ACCESSIBILITY_SCREENSHOT_COORDINATE_OUT_OF_BOUNDS", error?.message)
    }
}
