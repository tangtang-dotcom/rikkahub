package me.rerere.rikkahub.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGesturePolicyTest {
    private val display = AccessibilityGesturePolicy.DisplaySize(1080, 2400)

    @Test fun `swipes accept every physical direction within display bounds`() {
        AccessibilityGesturePolicy.validateSwipe(900, 2100, 100, 300, display)
        AccessibilityGesturePolicy.validateSwipe(100, 300, 900, 2100, display)
    }

    @Test fun `tap areas must be ordered and contained in the display`() {
        val area = AccessibilityGesturePolicy.Rect(100, 200, 300, 600)
        AccessibilityGesturePolicy.validateArea(area, display)
        assertEquals(200, area.centerX())
        assertEquals(400, area.centerY())
        assertCode("ACCESSIBILITY_INVALID_AREA") {
            AccessibilityGesturePolicy.validateArea(AccessibilityGesturePolicy.Rect(300, 200, 100, 600), display)
        }
        assertCode("ACCESSIBILITY_COORDINATE_OUT_OF_BOUNDS") {
            AccessibilityGesturePolicy.validateArea(AccessibilityGesturePolicy.Rect(0, 0, 1080, 600), display)
        }
    }

    @Test fun `invalid swipe endpoints are rejected before a gesture is dispatched`() {
        assertCode("ACCESSIBILITY_INVALID_SWIPE") {
            AccessibilityGesturePolicy.validateSwipe(100, 100, 100, 100, display)
        }
        assertCode("ACCESSIBILITY_COORDINATE_OUT_OF_BOUNDS") {
            AccessibilityGesturePolicy.validateSwipe(-1, 100, 100, 100, display)
        }
    }

    @Test fun `content directions map to platform scroll actions`() {
        assertEquals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id, AccessibilityGesturePolicy.scrollAction("down"))
        assertEquals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, AccessibilityGesturePolicy.scrollAction("up"))
        assertEquals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id, AccessibilityGesturePolicy.scrollAction("left"))
        assertEquals(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id, AccessibilityGesturePolicy.scrollAction("right"))
        assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityGesturePolicy.fallbackScrollAction("down"))
        assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityGesturePolicy.fallbackScrollAction("up"))
        assertTrue(AccessibilityGesturePolicy.fallbackScrollAction("left") == null)
    }

    private fun assertCode(code: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(code, error?.message)
    }
}
