package me.rerere.rikkahub.accessibility

import org.junit.Assert.*
import org.junit.Test

class AccessibilityNodeIdentityTest {
    @Test fun identityChangesAreRejected() {
        val a = AccessibilityNodeIdentity(1, "p", "Button", "id", "", "Save", "", false)
        assertTrue(a.matches(a.copy()))
        assertFalse(a.matches(a.copy(text = "Delete")))
    }

    @Test fun blankViewIdDoesNotBecomeAnAccidentalIdentityRequirement() {
        val observed = AccessibilityNodeIdentity(1, "p", "Button", "", "u", "Save", "", false)
        assertTrue(observed.matches(observed.copy(viewId = "generated:id")))
        assertFalse(observed.copy(viewId = "stable:id").matches(observed.copy(viewId = "other:id")))
    }

    @Test fun truncatedSnapshotsNeedUniqueId() {
        assertTrue(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(true, true, 1))
        assertFalse(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(false, true, 1))
        assertTrue(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(false, false, 1))
        assertFalse(AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(true, false, 2))
    }
}
