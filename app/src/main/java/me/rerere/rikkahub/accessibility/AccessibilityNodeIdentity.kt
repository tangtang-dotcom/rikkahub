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
    fun canUseAfterContentChange(hasUniqueId: Boolean, snapshotTruncated: Boolean, identityMatchCount: Int) =
        identityMatchCount == 1 && (hasUniqueId || !snapshotTruncated)
}
