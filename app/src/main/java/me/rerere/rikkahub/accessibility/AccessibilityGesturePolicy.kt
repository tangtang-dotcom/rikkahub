package me.rerere.rikkahub.accessibility

/** Keeps tool coordinates aligned with the physical display used by accessibility gestures. */
object AccessibilityGesturePolicy {
    data class DisplaySize(val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) { "ACCESSIBILITY_DISPLAY_UNAVAILABLE" }
        }
    }

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun centerX(): Int = left + (right - left) / 2
        fun centerY(): Int = top + (bottom - top) / 2
    }

    fun validateArea(area: Rect, display: DisplaySize) {
        require(area.right >= area.left && area.bottom >= area.top) { "ACCESSIBILITY_INVALID_AREA" }
        validatePoint(area.left, area.top, display)
        validatePoint(area.right, area.bottom, display)
    }

    fun validateSwipe(x1: Int, y1: Int, x2: Int, y2: Int, display: DisplaySize) {
        validatePoint(x1, y1, display)
        validatePoint(x2, y2, display)
        require(x1 != x2 || y1 != y2) { "ACCESSIBILITY_INVALID_SWIPE" }
    }

    fun validatePoint(x: Int, y: Int, display: DisplaySize) {
        require(x in 0 until display.width && y in 0 until display.height) {
            "ACCESSIBILITY_COORDINATE_OUT_OF_BOUNDS"
        }
    }

    fun scrollAction(direction: String): Int = when (direction) {
        "down" -> android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        "up" -> android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        "left" -> android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        "right" -> android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        else -> error("ACCESSIBILITY_INVALID_SCROLL_DIRECTION")
    }

    /** Generic actions are only safe fallbacks for vertical scrolling. */
    fun fallbackScrollAction(direction: String): Int? = when (direction) {
        "down" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        "up" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        else -> null
    }
}
