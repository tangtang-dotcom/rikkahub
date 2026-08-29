package me.rerere.rikkahub.accessibility

import kotlin.math.abs

enum class AccessibilityScrollAxis { HORIZONTAL, VERTICAL }

enum class AccessibilityScrollDirection(val axis: AccessibilityScrollAxis, val expectedDeltaSign: Int) {
    UP(AccessibilityScrollAxis.VERTICAL, -1), DOWN(AccessibilityScrollAxis.VERTICAL, 1),
    LEFT(AccessibilityScrollAxis.HORIZONTAL, -1), RIGHT(AccessibilityScrollAxis.HORIZONTAL, 1);

    fun opposite(): AccessibilityScrollDirection = when (this) {
        UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT
    }

    companion object {
        fun parse(value: String): AccessibilityScrollDirection =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: error("ACCESSIBILITY_INVALID_SCROLL_DIRECTION")
    }
}

enum class AccessibilityScrollEvidence {
    MOVED_BY_EVENT, MOVED_BY_ANCHOR_MOTION, DIRECTION_MISMATCH, AT_BOUNDARY, UNVERIFIED,
}

object AccessibilityScrollPolicy {
    fun classify(direction: AccessibilityScrollDirection, delta: Int?, source: String?, atBoundary: Boolean): AccessibilityScrollEvidence {
        if (delta != null && delta != 0 && delta.sign() != direction.expectedDeltaSign) return AccessibilityScrollEvidence.DIRECTION_MISMATCH
        if (delta != null && delta != 0) return if (source == "scroll_event") AccessibilityScrollEvidence.MOVED_BY_EVENT else AccessibilityScrollEvidence.MOVED_BY_ANCHOR_MOTION
        if (atBoundary) return AccessibilityScrollEvidence.AT_BOUNDARY
        return AccessibilityScrollEvidence.UNVERIFIED
    }

    /** Converts common on-screen content motion into scroll-position motion. */
    fun inferAnchorDelta(contentDeltas: List<Int>, minimumMotionPx: Int = 2): Int? {
        val meaningful = contentDeltas.filter { abs(it) >= minimumMotionPx }
        if (meaningful.size < 2) return null
        val positive = meaningful.filter { it > 0 }
        val negative = meaningful.filter { it < 0 }
        val dominant = when { positive.size > negative.size -> positive.sorted(); negative.size > positive.size -> negative.sorted(); else -> return null }
        if (dominant.size < 2 || dominant.size * 3 < meaningful.size * 2) return null
        val median = dominant[dominant.size / 2]
        val tolerance = maxOf(8, abs(median) / 2)
        val consistent = dominant.filter { abs(it - median) <= tolerance }.sorted()
        if (consistent.size < 2 || consistent.size * 3 < meaningful.size * 2) return null
        return -consistent[consistent.size / 2]
    }

    private fun Int.sign(): Int = when { this > 0 -> 1; this < 0 -> -1; else -> 0 }
}
