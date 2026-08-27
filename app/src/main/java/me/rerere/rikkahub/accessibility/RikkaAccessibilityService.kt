package me.rerere.rikkahub.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import java.util.UUID

class RikkaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { instance = this; contentGeneration++ }
    override fun onInterrupt() = Unit
    override fun onDestroy() { synchronized(lock) { if (instance === this) instance = null; observations.clear() }; super.onDestroy() }
    override fun onServiceConnected() { instance = this; contentGeneration++ }

    companion object {
        @Volatile private var instance: RikkaAccessibilityService? = null
        @Volatile private var contentGeneration: Long = 0
        private val lock = Any()
        private val observations = LinkedHashMap<String, ObservationRecord>()

        fun isAvailable(): Boolean = instance?.rootInActiveWindow != null

        fun observe(maxNodes: Int = 120): AccessibilityObservation {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
            val limit = maxNodes.coerceIn(1, 120)
            val nodes = ArrayList<NodeRef>()
            collect(root, nodes, limit, 0)
            val id = "a11y-${UUID.randomUUID()}"
            synchronized(lock) {
                observations[id] = ObservationRecord(contentGeneration, root.packageName?.toString(), nodes.size >= limit, nodes.map { it.identity })
                while (observations.size > 8) observations.remove(observations.keys.first())
            }
            return AccessibilityObservation(id, root.packageName?.toString(), nodes.size >= limit, nodes.map { it.snapshot })
        }

        fun execute(observationId: String, index: Int, action: String, text: String? = null): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val record = synchronized(lock) { observations[observationId] } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            val expected = record.nodes.getOrNull(index) ?: error("ACCESSIBILITY_NODE_INDEX_INVALID")
            val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
            val current = ArrayList<AccessibilityNodeInfo>()
            collectCurrent(root, current, 240, 0)
            val matches = current.filter { AccessibilityNodeIdentity.from(it).matches(expected) }
            if (!AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(expected.uniqueId.isNotBlank(), record.truncated, matches.size)) {
                current.forEach { it.recycle() }
                error("ACCESSIBILITY_STALE_ACTION_TARGET")
            }
            val node = matches.single()
            return try {
                val ok = when (action) {
                    "tap" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    "long_press" -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    "input" -> {
                        require(node.isEditable) { "ACCESSIBILITY_NODE_NOT_EDITABLE" }
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text ?: "") }
                            .let { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, it) }
                    }
                    "scroll_forward" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    "scroll_backward" -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    "enter" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> error("ACCESSIBILITY_ACTION_UNSUPPORTED")
                }
                AccessibilityActionResult(ok, action)
            } finally { current.forEach { it.recycle() } }
        }

        fun global(action: String): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val global = when (action) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                "recents" -> GLOBAL_ACTION_RECENTS
                else -> error("ACCESSIBILITY_GLOBAL_ACTION_UNSUPPORTED")
            }
            return AccessibilityActionResult(service.performGlobalAction(global), action)
        }

        private fun collectCurrent(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, max: Int, depth: Int) {
            if (out.size >= max || depth > 32) return
            out += node
            for (i in 0 until node.childCount) node.getChild(i)?.let { collectCurrent(it, out, max, depth + 1) }
        }

        private fun collect(node: AccessibilityNodeInfo, out: MutableList<NodeRef>, max: Int, depth: Int) {
            if (out.size >= max || depth > 32) return
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            out += NodeRef(node, AccessibilityNodeIdentity.from(node), AccessibilityNodeSnapshot(out.size, node.className?.toString(), node.text?.toString(), node.contentDescription?.toString(), node.isClickable, node.isEditable, node.isEnabled, bounds.left, bounds.top, bounds.right, bounds.bottom))
            for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, max, depth + 1) }
        }
    }

    private data class NodeRef(val node: AccessibilityNodeInfo, val identity: AccessibilityNodeIdentity, val snapshot: AccessibilityNodeSnapshot)
    private data class ObservationRecord(val generation: Long, val packageName: String?, val truncated: Boolean, val nodes: List<AccessibilityNodeIdentity>)
}

data class AccessibilityObservation(val observationId: String, val packageName: String?, val truncated: Boolean, val nodes: List<AccessibilityNodeSnapshot>)
data class AccessibilityNodeSnapshot(val index: Int, val className: String?, val text: String?, val contentDescription: String?, val clickable: Boolean, val editable: Boolean, val enabled: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class AccessibilityActionResult(val ok: Boolean, val action: String)
