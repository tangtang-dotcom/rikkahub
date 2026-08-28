package me.rerere.rikkahub.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityTextEditPlannerTest {
    @Test fun insertsAtCursor() {
        assertEquals(AccessibilityTextEditPlanner.Plan("abXYcd", 4), AccessibilityTextEditPlanner.insertAtSelection("abcd", "XY", 2, 2))
    }
    @Test fun replacesSelectionInEitherDirection() {
        assertEquals(AccessibilityTextEditPlanner.Plan("aZd", 2), AccessibilityTextEditPlanner.insertAtSelection("abcd", "Z", 3, 1))
    }
    @Test fun rejectsUnavailableSelection() {
        assertNull(AccessibilityTextEditPlanner.insertAtSelection("abc", "x", -1, -1))
        assertNull(AccessibilityTextEditPlanner.insertAtSelection("abc", "x", 0, 4))
    }
}
