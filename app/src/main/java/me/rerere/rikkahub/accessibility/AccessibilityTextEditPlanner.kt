package me.rerere.rikkahub.accessibility

internal object AccessibilityTextEditPlanner {
    data class Plan(val text: String, val cursor: Int)

    fun insertAtSelection(currentText: String, insertedText: String, selectionStart: Int, selectionEnd: Int): Plan? {
        if (selectionStart !in 0..currentText.length || selectionEnd !in 0..currentText.length) return null
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val result = currentText.substring(0, start) + insertedText + currentText.substring(end)
        return Plan(result, start + insertedText.length)
    }
}
