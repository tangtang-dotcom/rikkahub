package me.rerere.rikkahub.accessibility

/** Converts model-visible screenshot pixels back to physical display pixels. */
object AccessibilityCoordinateSpace {
    data class Space(
        val screenWidth: Int,
        val screenHeight: Int,
        val screenshotWidth: Int,
        val screenshotHeight: Int,
    ) {
        init {
            require(screenWidth > 0 && screenHeight > 0) { "ACCESSIBILITY_DISPLAY_UNAVAILABLE" }
            require(screenshotWidth > 0 && screenshotHeight > 0) { "ACCESSIBILITY_SCREENSHOT_COORDINATE_SPACE_UNAVAILABLE" }
        }

        fun toScreen(x: Int, y: Int): Point {
            require(x in 0 until screenshotWidth && y in 0 until screenshotHeight) {
                "ACCESSIBILITY_SCREENSHOT_COORDINATE_OUT_OF_BOUNDS"
            }
            return Point(
                x = (x.toLong() * screenWidth / screenshotWidth).toInt().coerceIn(0, screenWidth - 1),
                y = (y.toLong() * screenHeight / screenshotHeight).toInt().coerceIn(0, screenHeight - 1),
            )
        }
    }

    data class Point(val x: Int, val y: Int)
}
