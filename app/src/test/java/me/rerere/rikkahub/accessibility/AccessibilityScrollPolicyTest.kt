package me.rerere.rikkahub.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityScrollPolicyTest {
    @Test fun `directions describe newly revealed content rather than finger motion`() {
        assertEquals(1, AccessibilityScrollDirection.parse("down").expectedDeltaSign)
        assertEquals(-1, AccessibilityScrollDirection.parse("up").expectedDeltaSign)
        assertEquals(AccessibilityScrollDirection.LEFT, AccessibilityScrollDirection.RIGHT.opposite())
    }

    @Test fun `opposite verified motion is rejected`() {
        assertEquals(AccessibilityScrollEvidence.DIRECTION_MISMATCH,
            AccessibilityScrollPolicy.classify(AccessibilityScrollDirection.DOWN, -30, "scroll_event", false))
    }

    @Test fun `event motion anchor motion and boundary remain distinguishable`() {
        assertEquals(AccessibilityScrollEvidence.MOVED_BY_EVENT,
            AccessibilityScrollPolicy.classify(AccessibilityScrollDirection.DOWN, 30, "scroll_event", false))
        assertEquals(AccessibilityScrollEvidence.MOVED_BY_ANCHOR_MOTION,
            AccessibilityScrollPolicy.classify(AccessibilityScrollDirection.RIGHT, 15, "anchor_motion", false))
        assertEquals(AccessibilityScrollEvidence.AT_BOUNDARY,
            AccessibilityScrollPolicy.classify(AccessibilityScrollDirection.UP, null, null, true))
    }

    @Test fun `consistent content movement is inverted into scroll delta`() {
        assertEquals(100, AccessibilityScrollPolicy.inferAnchorDelta(listOf(-98, -100, -104)))
        assertEquals(-80, AccessibilityScrollPolicy.inferAnchorDelta(listOf(79, 80, 83)))
        assertEquals(null, AccessibilityScrollPolicy.inferAnchorDelta(listOf(-100, 100, -90, 90)))
    }
}
