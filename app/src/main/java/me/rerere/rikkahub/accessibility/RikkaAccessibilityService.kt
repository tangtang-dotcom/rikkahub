package me.rerere.rikkahub.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RikkaAccessibilityService : AccessibilityService() {
    private val serviceToken = SERVICE_TOKENS.incrementAndGet()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> synchronized(lock) { windowGenerations.clear() }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                recordScrollEvent(event)
                synchronized(lock) {
                    windowGenerations[event.windowId] = (windowGenerations[event.windowId] ?: 0L) + 1L
                }
            }
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
        latestObservationId = null
        windowGenerations.clear()
        scrollEventLock.withLock { recentScrollSignals.clear() }
    }

    companion object {
        private const val SCREENSHOT_TIMEOUT_MS = 4_000L
        private const val MAX_SCREENSHOT_FILES = 8
        private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "rikka-accessibility-screenshot").apply { isDaemon = true }
        }
        private val SERVICE_TOKENS = AtomicLong(0)
        @Volatile private var instance: RikkaAccessibilityService? = null
        private val lock = Any()
        private val observations = LinkedHashMap<String, ObservationRecord>()
        private var latestObservationId: String? = null
        private val windowGenerations = mutableMapOf<Int, Long>()
        private val scrollEventLock = ReentrantLock()
        private val scrollEventArrived = scrollEventLock.newCondition()
        private val recentScrollSignals = ArrayDeque<ScrollSignal>()
        private var scrollEventSequence = 0L

        fun isAvailable(): Boolean {
            val service = instance ?: return false
            return runCatching { service.runOnMainSync {
                service.rootInActiveWindow?.let { root -> root.recycle(); true } ?: false
            } }.getOrDefault(false)
        }

        fun observe(maxNodes: Int = 120): AccessibilityObservation {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            return service.runOnMainSync {
                val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
                try {
                    val limit = maxNodes.coerceIn(1, 120)
                    val nodes = ArrayList<NodeRef>()
                    collect(root, nodes, limit, 0)
                    val id = "a11y-${UUID.randomUUID()}"
                    val packageName = root.packageName?.toString()
                    val windowId = root.windowId
                    val display = service.displaySize()
                    val observation = AccessibilityObservation(
                        id, packageName, nodes.size >= limit, nodes.map { it.snapshot }, display,
                    )
                    synchronized(lock) {
                        observations[id] = ObservationRecord(
                            service.serviceToken, packageName, windowId,
                            windowGenerations[windowId] ?: 0L, nodes.size >= limit,
                            nodes.map { it.identity }, display,
                        )
                        latestObservationId = id
                        while (observations.size > 8) observations.remove(observations.keys.first())
                    }
                    observation
                } finally {
                    root.recycle()
                }
            }
        }

        fun execute(observationId: String, index: Int, action: String, text: String? = null): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val record = synchronized(lock) { observations[observationId] }
                ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            if (record.serviceToken != service.serviceToken) error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            val direction = when (action) {
                "scroll" -> AccessibilityScrollDirection.parse(text ?: error("ACCESSIBILITY_INVALID_SCROLL_DIRECTION"))
                "scroll_forward" -> AccessibilityScrollDirection.DOWN
                "scroll_backward" -> AccessibilityScrollDirection.UP
                else -> null
            }
            if (direction != null) return executeVerifiedScroll(service, record, index, action, direction)
            return service.runOnMainSync {
                withResolvedNode(service, record, index) { node ->
                    val ok = when (action) {
                        "tap" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_CLICK) { it.isClickable }
                        "long_press" -> performOnActionable(node, AccessibilityNodeInfo.ACTION_LONG_CLICK) { it.isLongClickable }
                        "input" -> {
                            require(node.isEditable) { "ACCESSIBILITY_NODE_NOT_EDITABLE" }
                            Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text ?: "")
                            }.let { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, it) }
                        }
                        "enter" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        else -> error("ACCESSIBILITY_ACTION_UNSUPPORTED")
                    }
                    if (!ok) error("ACTION_OUTCOME_UNKNOWN")
                    AccessibilityActionResult(true, action)
                }
            }
        }

        fun inputFocused(text: String): AccessibilityActionResult = editFocused("input_text", text, false, false)
        fun pasteFocused(text: String): AccessibilityActionResult = editFocused("paste_text", text, false, true)

        fun replaceText(observationId: String?, index: Int?, text: String): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            return if (index != null) {
                val id = observationId ?: error("NO_OBSERVATION")
                val record = synchronized(lock) { observations[id] } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
                if (record.serviceToken != service.serviceToken) error("ACCESSIBILITY_OBSERVATION_EXPIRED")
                service.runOnMainSync {
                    withResolvedNode(service, record, index) { node ->
                        require(node.isEditable && node.isEnabled) { "ACCESSIBILITY_NODE_NOT_EDITABLE" }
                        setNodeText(node, text, text.length, "replace_text")
                    }
                }
            } else editFocused("replace_text", text, true, false)
        }

        fun clearText(observationId: String?, index: Int?): AccessibilityActionResult =
            replaceText(observationId, index, "").copy(action = "clear_text")

        fun pressEnter(): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            return service.runOnMainSync {
                val node = focusedEditable(service) ?: error("NO_FOCUSED_EDITABLE")
                try {
                    val accepted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    if (!accepted) error("ACTION_OUTCOME_UNKNOWN")
                    AccessibilityActionResult(true, "press_key", method = "ACTION_IME_ENTER")
                } finally { node.recycle() }
            }
        }

        fun currentPackageName(): String? {
            val service = instance ?: return null
            return runCatching { service.runOnMainSync {
                service.rootInActiveWindow?.let { root -> try { root.packageName?.toString() } finally { root.recycle() } }
            } }.getOrNull()
        }

        fun queryText(text: String, includeDescription: Boolean, matchMode: String): AccessibilityTextMatch? {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val regex = if (matchMode == "regex") runCatching { Regex(text) }.getOrElse { error("INVALID_REGEX") } else null
            fun matches(value: String?): Boolean {
                val candidate = value ?: return false
                return when (matchMode) {
                    "contains" -> candidate.contains(text, true)
                    "exact" -> candidate.equals(text, true)
                    "prefix" -> candidate.startsWith(text, true)
                    "regex" -> regex!!.containsMatchIn(candidate)
                    else -> error("INVALID_MATCH_MODE")
                }
            }
            return service.runOnMainSync {
                val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
                try {
                    val queue = ArrayDeque<AccessibilityNodeInfo>()
                    queue.add(AccessibilityNodeInfo.obtain(root))
                    var visited = 0
                    while (queue.isNotEmpty() && visited < 240) {
                        val node = queue.removeFirst()
                        try {
                            visited++
                            val nodeText = node.text?.toString()
                            val description = node.contentDescription?.toString()
                            if (matches(nodeText) || (includeDescription && matches(description))) {
                                val bounds = Rect().also { node.getBoundsInScreen(it) }
                                return@runOnMainSync AccessibilityTextMatch(nodeText, description, node.className?.toString(), bounds.left, bounds.top, bounds.right, bounds.bottom)
                            }
                            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
                        } finally { node.recycle() }
                    }
                    null
                } finally { root.recycle() }
            }
        }

        private fun editFocused(action: String, insertedText: String, replace: Boolean, allowPasteFallback: Boolean): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            return service.runOnMainSync {
                val node = focusedEditable(service) ?: error("NO_FOCUSED_EDITABLE")
                try {
                    if (replace) return@runOnMainSync setNodeText(node, insertedText, insertedText.length, action)
                    if (node.isPassword || node.text == null) error("TEXT_CONTENT_UNAVAILABLE")
                    val plan = AccessibilityTextEditPlanner.insertAtSelection(node.text.toString(), insertedText, node.textSelectionStart, node.textSelectionEnd)
                        ?: error("TEXT_SELECTION_UNAVAILABLE")
                    runCatching { setNodeText(node, plan.text, plan.cursor, action) }.getOrElse { failure ->
                        if (!allowPasteFallback || failure.message != "ACTION_OUTCOME_UNKNOWN") throw failure
                        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val original = runCatching { clipboard.primaryClip }.getOrNull()
                        try {
                            clipboard.setPrimaryClip(ClipData.newPlainText("RikkaHub temporary input", insertedText))
                            if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) error("ACTION_OUTCOME_UNKNOWN")
                            AccessibilityActionResult(true, action, method = "ACTION_PASTE")
                        } finally {
                            runCatching { if (original != null) clipboard.setPrimaryClip(original) else clipboard.clearPrimaryClip() }
                        }
                    }
                } finally { node.recycle() }
            }
        }

        private fun focusedEditable(service: RikkaAccessibilityService): AccessibilityNodeInfo? {
            val root = service.rootInActiveWindow ?: return null
            return try {
                val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
                if (focused != null && focused.isEditable && focused.isEnabled && focused.isVisibleToUser) focused
                else { focused?.recycle(); null }
            } finally { root.recycle() }
        }

        private fun setNodeText(node: AccessibilityNodeInfo, text: String, cursor: Int, action: String): AccessibilityActionResult {
            val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) error("ACTION_OUTCOME_UNKNOWN")
            val safeCursor = cursor.coerceIn(0, text.length)
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, safeCursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, safeCursor)
            }
            val refreshed = runCatching { node.refresh() }.getOrDefault(false)
            if (!node.isPassword && (!refreshed || node.text?.toString() != text)) error("ACTION_OUTCOME_UNKNOWN")
            val selected = refreshed && node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            return AccessibilityActionResult(true, action, method = if (selected) "ACTION_SET_TEXT_AND_SELECTION" else "ACTION_SET_TEXT")
        }

        fun gesture(
            action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long,
            observationId: String? = null, coordinateSpace: String? = null,
        ): AccessibilityActionResult {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            val durationRange = when (action) {
                "long_press" -> 300L..3_000L
                "tap", "tap_area", "swipe" -> 100L..2_000L
                else -> error("ACCESSIBILITY_GESTURE_UNSUPPORTED")
            }
            require(durationMs in durationRange) { "ACCESSIBILITY_INVALID_GESTURE_DURATION" }
            val display = service.runOnMainSync { service.displaySize() }
            val effectiveObservationId = observationId ?: synchronized(lock) { latestObservationId }
            val record = effectiveObservationId?.let { id ->
                synchronized(lock) { observations[id] } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            }
            if (record != null && record.serviceToken != service.serviceToken) error("ACCESSIBILITY_OBSERVATION_EXPIRED")
            val requestedSpace = coordinateSpace?.trim()?.lowercase().orEmpty()
            require(requestedSpace.isBlank() || requestedSpace == "screen" || requestedSpace == "screenshot") {
                "ACCESSIBILITY_INVALID_COORDINATE_SPACE"
            }
            val useScreenshot = requestedSpace == "screenshot" || (requestedSpace.isBlank() && record?.screenshot != null)
            fun point(x: Int, y: Int): AccessibilityCoordinateSpace.Point {
                if (!useScreenshot) {
                    AccessibilityGesturePolicy.validatePoint(x, y, display)
                    return AccessibilityCoordinateSpace.Point(x, y)
                }
                val screenshot = record?.screenshot ?: error("ACCESSIBILITY_SCREENSHOT_COORDINATE_SPACE_UNAVAILABLE")
                return AccessibilityCoordinateSpace.Space(display.width, display.height, screenshot.width, screenshot.height).toScreen(x, y)
            }
            val first = point(x1, y1)
            val second = point(x2, y2)
            val points = when (action) {
                "swipe" -> {
                    AccessibilityGesturePolicy.validateSwipe(first.x, first.y, second.x, second.y, display)
                    intArrayOf(first.x, first.y, second.x, second.y)
                }
                "tap_area" -> {
                    val area = AccessibilityGesturePolicy.Rect(first.x, first.y, second.x, second.y)
                    AccessibilityGesturePolicy.validateArea(area, display)
                    intArrayOf(area.centerX(), area.centerY(), area.centerX(), area.centerY())
                }
                "tap", "long_press" -> intArrayOf(first.x, first.y, first.x, first.y)
                else -> error("ACCESSIBILITY_GESTURE_UNSUPPORTED")
            }
            val path = Path().apply {
                moveTo(points[0].toFloat(), points[1].toFloat())
                lineTo(points[2].toFloat(), points[3].toFloat())
            }
            val description = GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, durationMs)
            ).build()
            val latch = CountDownLatch(1)
            var completed = false
            val accepted = service.runOnMainSync {
                service.dispatchGesture(description, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { completed = true; latch.countDown() }
                    override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
                }, null)
            }
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
            if (!service.runOnMainSync { service.performGlobalAction(global) }) error("ACTION_OUTCOME_UNKNOWN")
            return AccessibilityActionResult(true, action)
        }

        fun captureScreenshot(observationId: String? = null): AccessibilityScreenshot {
            val service = instance ?: error("ACCESSIBILITY_UNAVAILABLE")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) error("ACCESSIBILITY_SCREENSHOT_UNSUPPORTED")
            if (Looper.myLooper() == Looper.getMainLooper()) error("ACCESSIBILITY_SCREENSHOT_MAIN_THREAD")
            val screenshot = service.captureScreenshot()
            observationId?.let { id -> synchronized(lock) {
                val record = observations[id] ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
                if (record.serviceToken != service.serviceToken) error("ACCESSIBILITY_OBSERVATION_EXPIRED")
                record.screenshot = screenshot
            } }
            return screenshot
        }

        private fun executeVerifiedScroll(
            service: RikkaAccessibilityService, record: ObservationRecord, index: Int,
            action: String, direction: AccessibilityScrollDirection,
        ): AccessibilityActionResult {
            val startedAt = SystemClock.elapsedRealtime()
            val beforeSequence = scrollEventLock.withLock { scrollEventSequence }
            val preparation = service.runOnMainSync {
                withResolvedNode(service, record, index) { node ->
                    val target = findActionable(node) { it.isScrollable }
                        ?: error("ACCESSIBILITY_NODE_NOT_SCROLLABLE")
                    try {
                        val before = collectAnchors(target)
                        val exact = AccessibilityGesturePolicy.scrollAction(direction.name.lowercase())
                        val fallback = AccessibilityGesturePolicy.fallbackScrollAction(direction.name.lowercase())
                        val actions = target.actionList.map { it.id }.toSet()
                        var method = "ACTION_DIRECTIONAL"
                        var accepted = target.performAction(exact)
                        if (!accepted && fallback != null) {
                            method = "ACTION_GENERIC"
                            accepted = target.performAction(fallback)
                        }
                        val boundary = !accepted && exact !in actions &&
                            (fallback == null || fallback !in actions) &&
                            AccessibilityGesturePolicy.scrollAction(direction.opposite().name.lowercase()) in actions
                        ScrollPreparation(accepted, boundary, before, method)
                    } finally { target.recycle() }
                }
            }
            if (!preparation.accepted) {
                if (preparation.atBoundary) return AccessibilityActionResult(
                    ok = true, action = action, direction = direction.name.lowercase(), moved = false,
                    atBoundary = true, method = preparation.method,
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
                error("ACCESSIBILITY_ACTION_FAILED")
            }
            val signal = awaitScrollSignal(beforeSequence, record.packageName.orEmpty(), record.windowId, 1_200L)
            val afterAnchors = service.runOnMainSync {
                val root = service.rootInActiveWindow ?: return@runOnMainSync emptyList()
                try { collectAnchors(root) } finally { root.recycle() }
            }
            val eventDelta = signal?.axisDelta(direction.axis)?.takeIf { it != 0 }
            val anchorDelta = inferAnchorDelta(preparation.beforeAnchors, afterAnchors, direction)
            val delta = eventDelta ?: anchorDelta
            val verifiedBy = if (eventDelta != null) "scroll_event" else if (anchorDelta != null) "anchor_motion" else null
            return when (AccessibilityScrollPolicy.classify(direction, delta, verifiedBy, false)) {
                AccessibilityScrollEvidence.MOVED_BY_EVENT,
                AccessibilityScrollEvidence.MOVED_BY_ANCHOR_MOTION -> AccessibilityActionResult(
                    ok = true, action = action, direction = direction.name.lowercase(), moved = true,
                    atBoundary = false, method = preparation.method,
                    deltaX = delta.takeIf { direction.axis == AccessibilityScrollAxis.HORIZONTAL },
                    deltaY = delta.takeIf { direction.axis == AccessibilityScrollAxis.VERTICAL },
                    verifiedBy = verifiedBy, elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
                AccessibilityScrollEvidence.DIRECTION_MISMATCH -> error("DIRECTION_MISMATCH")
                AccessibilityScrollEvidence.AT_BOUNDARY -> AccessibilityActionResult(
                    ok = true, action = action, direction = direction.name.lowercase(), moved = false,
                    atBoundary = true, method = preparation.method,
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
                AccessibilityScrollEvidence.UNVERIFIED -> error("ACTION_OUTCOME_UNKNOWN")
            }
        }

        private fun awaitScrollSignal(afterSequence: Long, packageName: String, windowId: Int, timeoutMs: Long): ScrollSignal? {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            scrollEventLock.withLock {
                while (true) {
                    recentScrollSignals.firstOrNull {
                        it.sequence > afterSequence && it.windowId == windowId && it.packageName == packageName
                    }?.let { return it }
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) return null
                    try { scrollEventArrived.await(remaining, TimeUnit.MILLISECONDS) }
                    catch (_: InterruptedException) { Thread.currentThread().interrupt(); return null }
                }
            }
        }

        private fun <T> withResolvedNode(
            service: RikkaAccessibilityService, record: ObservationRecord, index: Int,
            block: (AccessibilityNodeInfo) -> T,
        ): T {
            val expected = record.nodes.getOrNull(index) ?: error("ACCESSIBILITY_NODE_INDEX_INVALID")
            val root = service.rootInActiveWindow ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
            val current = ArrayList<AccessibilityNodeInfo>()
            try {
                collectCurrent(root, current, 240, 0)
                val matches = current.filter { AccessibilityNodeIdentity.from(it).matches(expected) }
                val windowChanged = record.packageName != root.packageName?.toString() || record.windowId != root.windowId
                val generationChanged = synchronized(lock) {
                    (windowGenerations[root.windowId] ?: 0L) != record.contentGeneration
                }
                val fresh = matches.size == 1 && (!generationChanged ||
                    AccessibilityIdentityFreshnessPolicy.canUseAfterContentChange(
                        expected.uniqueId.isNotBlank(), record.truncated, matches.size,
                    ))
                if (windowChanged || !fresh) error("ACCESSIBILITY_STALE_ACTION_TARGET")
                return block(matches.single())
            } finally {
                current.forEach { it.recycle() }
                root.recycle()
            }
        }

        private fun findActionable(node: AccessibilityNodeInfo, accepts: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            while (current != null) {
                if (current.isEnabled && accepts(current)) return current
                val parent = current.parent
                current.recycle()
                current = parent
            }
            return null
        }

        private fun collectAnchors(root: AccessibilityNodeInfo): List<ScrollAnchor> {
            val result = ArrayList<ScrollAnchor>(40)
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 16 || result.size >= 40 || !node.isVisibleToUser) return
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val key = listOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId.orEmpty() else "",
                    node.className?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
                    node.text?.toString().orEmpty().take(80), node.contentDescription?.toString().orEmpty().take(80),
                ).joinToString("|")
                if (key.any { it != '|' } && !bounds.isEmpty) result += ScrollAnchor(key, bounds.centerX(), bounds.centerY())
                for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                    try { visit(child, depth + 1) } finally { child.recycle() }
                }
            }
            visit(root, 0)
            return result
        }

        private fun inferAnchorDelta(
            before: List<ScrollAnchor>, after: List<ScrollAnchor>, direction: AccessibilityScrollDirection,
        ): Int? {
            fun unique(items: List<ScrollAnchor>) = items.groupBy { it.key }
                .mapNotNull { (key, matches) -> matches.singleOrNull()?.let { key to it } }.toMap()
            val old = unique(before)
            val fresh = unique(after)
            val deltas = old.mapNotNull { (key, first) ->
                val second = fresh[key] ?: return@mapNotNull null
                if (direction.axis == AccessibilityScrollAxis.VERTICAL) second.centerY - first.centerY
                else second.centerX - first.centerX
            }
            return AccessibilityScrollPolicy.inferAnchorDelta(deltas)
        }

        private data class ScrollPreparation(
            val accepted: Boolean, val atBoundary: Boolean,
            val beforeAnchors: List<ScrollAnchor>, val method: String,
        )
        private data class ScrollAnchor(val key: String, val centerX: Int, val centerY: Int)
        private data class ScrollSignal(
            val sequence: Long, val packageName: String, val windowId: Int,
            val deltaX: Int?, val deltaY: Int?,
        ) {
            fun axisDelta(axis: AccessibilityScrollAxis): Int? =
                if (axis == AccessibilityScrollAxis.VERTICAL) deltaY else deltaX
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
            out += NodeRef(AccessibilityNodeIdentity.from(node), AccessibilityNodeSnapshot(out.size, node.className?.toString(), node.text?.toString(), node.contentDescription?.toString(), node.isClickable, node.isEditable, node.isScrollable, node.isEnabled, bounds.left, bounds.top, bounds.right, bounds.bottom))
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> collect(child, out, max, depth + 1); child.recycle() }
        }
    }

    private fun recordScrollEvent(event: AccessibilityEvent) {
        val deltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX.takeUnless { it == -1 } else null
        val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY.takeUnless { it == -1 } else null
        scrollEventLock.withLock {
            scrollEventSequence += 1L
            recentScrollSignals.addLast(ScrollSignal(
                scrollEventSequence, event.packageName?.toString().orEmpty(), event.windowId, deltaX, deltaY,
            ))
            while (recentScrollSignals.size > 16) recentScrollSignals.removeFirst()
            scrollEventArrived.signalAll()
        }
    }

    private fun <T> runOnMainSync(timeoutMs: Long = 3_000L, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        val value = arrayOfNulls<Any?>(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try { value[0] = block() } catch (error: Throwable) { failure = error } finally { latch.countDown() }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) error("ACCESSIBILITY_SERVICE_TIMEOUT")
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value[0] as T
    }

    private fun displaySize(): AccessibilityGesturePolicy.DisplaySize {
        val point = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(point)
        return AccessibilityGesturePolicy.DisplaySize(point.x, point.y)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshot(): AccessibilityScreenshot {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        runOnMainSync {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?: error("ACCESSIBILITY_SCREENSHOT_FAILED")
                        try {
                            bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            hardware.recycle()
                        }
                    } finally {
                        buffer.close()
                    }
                } catch (_: Throwable) {
                    // The tool only exposes a stable failure code; platform error values vary by API level.
                } finally {
                    latch.countDown()
                }
            }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            })
        }
        if (!latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) error("ACCESSIBILITY_SCREENSHOT_TIMEOUT")
        val captured = bitmap ?: error("ACCESSIBILITY_SCREENSHOT_FAILED")
        return try {
            val file = saveScreenshot(captured)
            AccessibilityScreenshot(file.toURI().toString(), captured.width, captured.height)
        } finally {
            captured.recycle()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): File {
        val directory = File(cacheDir, "accessibility-screenshots")
        if (!directory.exists() && !directory.mkdirs()) error("ACCESSIBILITY_SCREENSHOT_STORAGE_FAILED")
        directory.listFiles()?.sortedByDescending(File::lastModified)?.drop(MAX_SCREENSHOT_FILES - 1)
            ?.forEach(File::delete)
        val file = File.createTempFile("observe-", ".png", directory)
        try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("ACCESSIBILITY_SCREENSHOT_STORAGE_FAILED")
                }
            }
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private data class NodeRef(val identity: AccessibilityNodeIdentity, val snapshot: AccessibilityNodeSnapshot)
    private data class ObservationRecord(
        val serviceToken: Long, val packageName: String?, val windowId: Int,
        val contentGeneration: Long, val truncated: Boolean, val nodes: List<AccessibilityNodeIdentity>,
        val display: AccessibilityGesturePolicy.DisplaySize,
        var screenshot: AccessibilityScreenshot? = null,
    )
}

data class AccessibilityObservation(
    val observationId: String,
    val packageName: String?,
    val truncated: Boolean,
    val nodes: List<AccessibilityNodeSnapshot>,
    val display: AccessibilityGesturePolicy.DisplaySize,
)
data class AccessibilityScreenshot(val uri: String, val width: Int, val height: Int)
data class AccessibilityNodeSnapshot(val index: Int, val className: String?, val text: String?, val contentDescription: String?, val clickable: Boolean, val editable: Boolean, val scrollable: Boolean, val enabled: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class AccessibilityActionResult(
    val ok: Boolean,
    val action: String,
    val direction: String? = null,
    val moved: Boolean? = null,
    val atBoundary: Boolean? = null,
    val method: String? = null,
    val deltaX: Int? = null,
    val deltaY: Int? = null,
    val verifiedBy: String? = null,
    val elapsedMs: Long? = null,
)

data class AccessibilityTextMatch(val text: String?, val contentDescription: String?, val className: String?, val left: Int, val top: Int, val right: Int, val bottom: Int)
