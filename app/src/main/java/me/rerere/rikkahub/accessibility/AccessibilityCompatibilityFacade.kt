package me.rerere.rikkahub.accessibility

import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream

private val compatSnapshots = mutableMapOf<String, RikkaAccessibilityService.NodeSnapshot>()

data class AccessibilityDisplaySize(val width: Int, val height: Int)
data class AccessibilityObservation(val observationId: String, val packageName: String?, val truncated: Boolean, val nodes: List<AccessibilityNodeSnapshot>, val display: AccessibilityDisplaySize)
data class AccessibilityNodeSnapshot(val index: Int, val className: String?, val text: String?, val contentDescription: String?, val clickable: Boolean, val editable: Boolean, val scrollable: Boolean, val enabled: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class AccessibilityActionResult(val ok: Boolean, val action: String, val direction: String? = null, val moved: Boolean? = null, val atBoundary: Boolean? = null, val method: String? = null, val deltaX: Int? = null, val deltaY: Int? = null, val verifiedBy: String? = null, val elapsedMs: Long? = null)
data class AccessibilityScreenshot(val uri: String, val width: Int, val height: Int)
data class AccessibilityTextMatch(val text: String?, val contentDescription: String?, val className: String?, val left: Int, val top: Int, val right: Int, val bottom: Int)

fun compatObserve(maxNodes: Int): AccessibilityObservation {
    val s = current() ?: error("ACCESSIBILITY_UNAVAILABLE")
    val snap = s.captureNodeSnapshot(maxNodes) ?: error("ACCESSIBILITY_NO_ACTIVE_WINDOW")
    synchronized(compatSnapshots) { compatSnapshots[snap.id] = snap; while (compatSnapshots.size > 8) compatSnapshots.remove(compatSnapshots.keys.first()) }
    val d = s.displaySize() ?: (0 to 0)
    return AccessibilityObservation(snap.id, snap.packageName, snap.truncated, snap.nodes.map { n -> AccessibilityNodeSnapshot(n.index,n.className,n.text,n.desc,n.clickable,n.editable,n.scrollable,n.enabled,n.bounds.left,n.bounds.top,n.bounds.right,n.bounds.bottom) }, AccessibilityDisplaySize(d.first,d.second))
}

fun compatNodeBounds(id: String, index: Int): Rect = synchronized(compatSnapshots) { compatSnapshots[id]?.nodes?.getOrNull(index)?.bounds?.let(::Rect) } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")

private fun snap(id: String) = synchronized(compatSnapshots) { compatSnapshots[id] } ?: error("ACCESSIBILITY_OBSERVATION_EXPIRED")
private fun service() = RikkaAccessibilityService.current() ?: error("ACCESSIBILITY_UNAVAILABLE")
private fun RikkaAccessibilityService.NodeActionResult.out(action: String) = AccessibilityActionResult(ok, action, method=method.takeIf { it.isNotBlank() }, verifiedBy=if (verified == true) "action" else null)
private fun RikkaAccessibilityService.ScrollActionResult.out(action: String) = AccessibilityActionResult(ok, action, direction.name.lowercase(), moved, atBoundary, method, deltaX, deltaY, verifiedBy, elapsedMs)

fun compatExecute(id: String, index: Int, action: String, value: String?): AccessibilityActionResult {
    val s = service(); val o = snap(id)
    return when (action) {
        "tap" -> s.clickNode(o,index).out(action)
        "long_press" -> s.longClickNode(o,index,800L).out(action)
        "input" -> s.setTextNode(o,index,value ?: "").out(action)
        "enter" -> s.imeEnter().out(action)
        "scroll" -> s.scrollNode(o,index,ScrollDirection.valueOf((value ?: error("direction is required")).uppercase())).out(action)
        else -> error("ACCESSIBILITY_ACTION_UNSUPPORTED")
    }
}

fun compatInputFocused(text: String) = service().inputTextFocused(text).out("input_text")
fun compatPasteFocused(text: String) = service().pasteText(text).out("paste_text")
fun compatReplaceText(id: String?, index: Int?, text: String) = service().setTextNode(id?.let(::snap),index,text).out("replace_text")
fun compatClearText(id: String?, index: Int?) = service().setTextNode(id?.let(::snap),index,"").out("clear_text")
fun compatPressEnter() = service().imeEnter().out("enter")
fun compatCurrentPackageName() = service().currentPackageName()
fun compatGlobal(action: String) = service().globalActionResult(action).out(action)

fun compatQueryText(text: String, includeDescription: Boolean, matchMode: String): AccessibilityTextMatch? = service().queryNodes(120).firstOrNull { n ->
    listOfNotNull(n.text.takeIf { it.isNotEmpty() }, n.desc.takeIf { includeDescription && it.isNotEmpty() }).any { if (matchMode == "exact") it == text else it.contains(text,true) }
}?.let { n -> AccessibilityTextMatch(n.text,n.desc,n.className,n.bounds.left,n.bounds.top,n.bounds.right,n.bounds.bottom) }

fun compatGesture(action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long, observationId: String?, coordinateSpace: String?) = if (action == "swipe") service().gestureSwipe(x1.toFloat(),y1.toFloat(),x2.toFloat(),y2.toFloat(),durationMs).out(action) else service().gestureTap(x1.toFloat(),y1.toFloat(),durationMs).out(action)

fun compatCaptureScreenshot(observationId: String?): AccessibilityScreenshot {
    val s = service(); val r = s.captureScreenshotExcludingOverlays(); val b = r.bitmap ?: error("ACCESSIBILITY_SCREENSHOT_FAILED")
    val f = File(s.cacheDir,"compat-observe-${System.currentTimeMillis()}.png")
    FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
    val d = s.displaySize() ?: (b.width to b.height); b.recycle(); return AccessibilityScreenshot(f.toURI().toString(),d.first,d.second)
}
