package me.rerere.rikkahub.accessibility.overlay

import android.content.Context

/** Eta 动作反馈入口：Compose 光效/光球由 AgentOverlayHost 统一托管。 */
internal object AccessibilityActionEffects {
    fun showAction(context: Context, action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        AgentOverlayHost.showAction(context, action, x1, y1, x2, y2, durationMs)
        when (action) {
            "swipe" -> GestureIndicator.showSwipe(context, x1, y1, x2, y2, durationMs.toInt())
            "long_press" -> GestureIndicator.showLongPress(context, x1, y1, durationMs.toInt())
            else -> GestureIndicator.showTap(context, x1, y1)
        }
    }

    fun showOrb(context: Context) = AgentOverlayHost.show(context)

    fun showOperation(context: Context, action: String) = AgentOverlayHost.showOperation(context, action)

    fun hideOrb() = AgentOverlayHost.hide()
}
