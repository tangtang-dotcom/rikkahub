package me.rerere.rikkahub.accessibility

import org.junit.Assert.*
import org.junit.Test

class AccessibilityNodeIdentityTest {
    @Test fun identityChangesAreRejected() {
        val a = AccessibilityNodeIdentity(1, "p", "Button", "id", "", "Save", "", false)
        assertTrue(a.matches(a.copy()))
        assertFalse(a.matches(a.copy(text = "Delete")))
    }

    @Test fun truncatedSnapshotsNeedUniqueId() {
        assertTrue(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(true, true, 1))
        assertFalse(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(false, true, 1))
        assertTrue(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(false, false, 1))
        assertFalse(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(true, false, 2))
    }
}
