package me.rerere.rikkahub.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

data class AccessibilityNodeIdentity(
    val windowId: Int,
    val packageName: String,
    val className: String,
    val viewId: String,
    val uniqueId: String,
    val text: String,
    val description: String,
    val password: Boolean,
) {
    val strong: Boolean
        get() = uniqueId.isNotBlank() || text.isNotBlank() || description.isNotBlank()

    fun sameSemanticIdentity(other: AccessibilityNodeIdentity): Boolean =
        windowId == other.windowId && packageName == other.packageName &&
            className == other.className && viewId == other.viewId &&
            text == other.text && description == other.description && password == other.password

    fun matches(other: AccessibilityNodeIdentity): Boolean {
        if (windowId != other.windowId || packageName != other.packageName || className != other.className) return false
        if (password != other.password || uniqueId != other.uniqueId) return false
        if (viewId.isNotBlank() && viewId != other.viewId) return false
        return text == other.text && description == other.description
    }

    companion object {
        fun from(node: AccessibilityNodeInfo) = AccessibilityNodeIdentity(
            node.windowId, node.packageName?.toString().orEmpty(),
            node.className?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
            if (Build.VERSION.SDK_INT >= 33) node.uniqueId.orEmpty() else "",
            node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(), node.isPassword,
        )
    }
}

object AccessibilityIdentityFreshnessPolicy {
    /**
     * A confirmation UI may temporarily background and then restore the observed window, producing a
     * harmless content-generation change. Re-resolving is safe when the identity is unique in a complete
     * fresh traversal, or when Android supplied a stable uniqueId. The size of the original model snapshot
     * is irrelevant because the selected node identity itself was retained.
     */
    fun canUseAfterContentChange(hasUniqueId: Boolean, currentTraversalComplete: Boolean, identityMatchCount: Int) =
        identityMatchCount == 1 && (hasUniqueId || currentTraversalComplete)
}
