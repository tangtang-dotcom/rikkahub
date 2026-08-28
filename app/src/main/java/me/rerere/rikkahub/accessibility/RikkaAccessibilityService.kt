package me.rerere.rikkahub.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class RikkaAccessibilityService : AccessibilityService() {
    private val serviceToken = SERVICE_TOKENS.incrementAndGet()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> synchronized(lock) { windowGenerations.clear() }
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> synchronized(lock) {
                windowGenerations[event.windowId] = (windowGenerations[event.windowId] ?: 0L) + 1L
            }
        }
    }
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean { clearCurrentInstance(); return super.onUnbind(intent) }
    override fun onDestroy() { clearCurrentInstance(); super.onDestroy() }
    override fun onServiceConnected() { instance = this; synchronized(lock) { windowGenerations.clear() } }

    private fun clearCurrentInstance() = synchronized(lock) {
        if (instance === this) instance = null
        observations.clear()
        windowGenerations.clear()
    }

    companion object {
        private val SERVICE_TOKENS = AtomicLong(0)
        @Volatile private var instance: RikkaAccessibilityService? = null
        private val lock = Any()
        private val observations = LinkedHashMap<String, ObservationRecord>()
        private val windowGenerations = mutableMapOf<Int, Long>()

        fun isAvailable(): Boolean = instance?.rootInActiveWindow != null

        fun observe(maxNodes: Int = 120): AccessibilityObservation {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
            val limit = maxNodes.coerceIn(1, 120)
            val nodes = ArrayList<NodeRef>()
            collect(root, nodes, limit, 0)
            val id = "a11y-${UUID.randomUUID()}"
            val packageName = root.packageName?.toString()
            val windowId = root.windowId
            val observation = AccessibilityObservation(id, packageName, nodes.size >= limit, nodes.map { it.snapshot })
            synchronized(lock) {
                observations[id] = ObservationRecord(service.serviceToken, packageName, windowId,
                    windowGenerations[windowId] ?: 0L, nodes.size >= limit, nodes.map { it.identity })
                while (observations.size > 8) observations.remove(observations.keys.first())
            }
            return observation
        }

        fun execute(observationId: String, index: Int, action: String, text: String? = null): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val record = synchronized(lock) { observations[observationId] } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            if (record.serviceToken != service.serviceToken) error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            val expected = record.nodes.getOrNull(index) ?: error("ACCESSIBILITY_NODE_INDEX_INVALID")
            val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
            val current = ArrayList<AccessibilityNodeInfo>()
            collectCurrent(root, current, 240, 0)
            val matches = current.filter { AccessibilityNodeIdentity.from(it).matches(expected) }
            val windowChanged = record.packageName != root.packageName?.toString() || record.windowId != root.windowId
            val generationChanged = synchronized(lock) { (windowGenerations[root.windowId] ?: 0L) != record.contentGeneration }
            val fresh = matches.size == 1 && (!generationChanged ||
                AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(expected.uniqueId.isNotBlank(), record.truncated, matches.size))
            if (windowChanged || !fresh) {
                current.forEach { it.recycle() }
                error("ACCESSIBILITY_STALE_ACTION_TARGET")
            }
            val node = matches.single()
            return try {
                val ok = when (action) {
                    "tap" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_CLICK) { it.isClickable }
                    "long_press" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_LONG_CLICK) { it.isLongClickable }
                    "input" -> {
                        require(node.isEditable) { "ACCESSIBILITY_NODE_NOT_EDITABLE" }
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text ?: "") }
                            .let { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, it) }
                    }
                    "scroll_forward" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { it.isScrollable }
                    "scroll_backward" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { it.isScrollable }
                    "enter" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> error("ACCESSIBILITY_ACTION_UNSUPPORTED")
                }
                if (!ok) error("ACTION_OUTCOME_UNKNOWN")
                AccessibilityActionResult(true, action)
            } finally { current.forEach { it.recycle() } }
        }

        fun gesture(action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            require(durationMs in 100..2000) { "ACCESSIBILITY_INVALID_GESTURE_DURATION" }
            val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            val description = GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, durationMs)
            ).build()
            val latch = CountDownLatch(1)
            var completed = false
            val accepted = service.dispatchGesture(description, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { completed = true; latch.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
            }, null)
            if (!accepted) error("ACCESSIBILITY_GESTURE_REJECTED")
            if (!latch.await(durationMs + 3000, TimeUnit.MILLISECONDS) || !completed) error("ACTION_OUTCOME_UNKNOWN")
            return AccessibilityActionResult(true, action)
        }

        fun global(action: String): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val global = when (action) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                "recents" -> GLOBAL_ACTION_RECENTS
                "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
                else -> error("ACCESSIBILITY_GLOBAL_ACTION_UNSUPPORTED")
            }
            if (!service.performGlobalAction(global)) error("ACTION_OUTCOME_UNKNOWN")
            return AccessibilityActionResult(true, action)
        }

        private fun performOnActionable(node: AccessibilityNodeInfo, action: Int, accepts: (AccessibilityNodeInfo) -> Boolean): Boolean {
            var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            try {
                while (current != null) {
                    if (current.isEnabled && accepts(current)) return current.performAction(action)
                    val parent = current.parent
                    current.recycle()
                    current = parent
                }
                return false
            } finally {
                current?.recycle()
            }
        }

        private fun collectCurrent(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, max: Int, depth: Int) {
            if (out.size >= max || depth > 32) return
            out += AccessibilityNodeInfo.obtain(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> collectCurrent(child, out, max, depth + 1); child.recycle() }
        }

        private fun collect(node: AccessibilityNodeInfo, out: MutableList<NodeRef>, max: Int, depth: Int) {
            if (out.size >= max || depth > 32) return
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            out += NodeRef(AccessibilityNodeIdentity.from(node), AccessibilityNodeSnapshot(out.size, node.className?.toString(), node.text?.toString(), node.contentDescription?.toString(), node.isClickable, node.isEditable, node.isEnabled, bounds.left, bounds.top, bounds.right, bounds.bottom))
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> collect(child, out, max, depth + 1); child.recycle() }
        }
    }

    private data class NodeRef(val identity: AccessibilityNodeIdentity, val snapshot: AccessibilityNodeSnapshot)
    private data class ObservationRecord(
        val serviceToken: Long, val packageName: String?, val windowId: Int,
        val contentGeneration: Long, val truncated: Boolean, val nodes: List<AccessibilityNodeIdentity>,
    )
}

data class AccessibilityObservation(val observationId: String, val packageName: String?, val truncated: Boolean, val nodes: List<AccessibilityNodeSnapshot>)
data class AccessibilityNodeSnapshot(val index: Int, val className: String?, val text: String?, val contentDescription: String?, val clickable: Boolean, val editable: Boolean, val enabled: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class AccessibilityActionResult(val ok: Boolean, val action: String)
