package me.rerere.rikkahub.accessibility.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Eta 原版运行时浮层宿主的 Rikka 适配版。 */
internal object AgentOverlayHost {
    private val main = Handler(Looper.getMainLooper())
    private var owner: OverlayOwner? = null
    private var windowManager: WindowManager? = null
    private var glowView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val state = mutableStateOf(AgentOverlayState.Initial.copy(phase = AgentOverlayPhase.PAUSED))
    private val collapsed = mutableStateOf(true)

    fun show(context: Context) {
        main.post {
            val service = RikkaAccessibilityService.current() ?: return@post
            if (orbView != null) return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val lifecycleOwner = OverlayOwner().also { it.start() }
            owner = lifecycleOwner
            windowManager = wm
            val glow = composeView(service) { AgentOverlayGlow(state.value) }
            val glowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, realHeight(service, wm),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            if (!runCatching { wm.addView(glow, glowParams) }.isSuccess) {
                lifecycleOwner.destroy(); owner = null; windowManager = null; return@post
            }
            glowView = glow
            val orb = composeView(service) {
                AgentOverlayOrb(state = state.value, onToggleCollapse = ::toggleCollapse)
            }
            val orbParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                x = dp(service, 8)
                y = (service.resources.displayMetrics.heightPixels * 0.6f).toInt()
            }
            if (!runCatching { wm.addView(orb, orbParams) }.isSuccess) {
                runCatching { wm.removeView(glow) }; glowView = null
                lifecycleOwner.destroy(); owner = null; windowManager = null; return@post
            }
            orbView = orb
            if (!collapsed.value) showBubble(wm, service)
        }
    }

    fun showAction(context: Context, action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        show(context)
        main.post {
            state.value = state.value.copy(phase = AgentOverlayPhase.RUNNING, status = AgentOverlayStatus.RunningTool(action))
            AgentHapticFeedback.perform(context, when (action) {
                "long_press" -> AgentHapticFeedback.Type.LONG_PRESS
                "swipe" -> AgentHapticFeedback.Type.SWIPE
                else -> AgentHapticFeedback.Type.TAP
            })
            main.postDelayed({
                if (orbView != null && state.value.phase == AgentOverlayPhase.RUNNING) {
                    state.value = state.value.copy(phase = AgentOverlayPhase.PAUSED, status = AgentOverlayStatus.ToolCompleted(action))
                }
            }, durationMs.coerceAtLeast(650L) + 650L)
        }
    }

    fun hide() {
        main.post {
            val wm = windowManager
            listOf(bubbleView, orbView, glowView).forEach { it?.let { v -> runCatching { wm?.removeView(v) } } }
            bubbleView = null; orbView = null; glowView = null; bubbleParams = null; windowManager = null
            owner?.destroy(); owner = null
        }
    }

    private fun toggleCollapse() {
        main.post {
            collapsed.value = !collapsed.value
            val wm = windowManager ?: return@post
            val service = RikkaAccessibilityService.current() ?: return@post
            if (collapsed.value) {
                bubbleView?.let { runCatching { wm.removeView(it) } }; bubbleView = null; bubbleParams = null
            } else if (bubbleView == null) showBubble(wm, service)
        }
    }

    private fun showBubble(wm: WindowManager, service: Context) {
        val bubble = composeView(service) {
            AgentOverlayBubble(
                state = state.value,
                onCollapse = ::toggleCollapse,
                onPause = { state.value = state.value.copy(phase = AgentOverlayPhase.PAUSED, status = AgentOverlayStatus.Paused) },
                onResume = { state.value = state.value.copy(phase = AgentOverlayPhase.RUNNING, status = AgentOverlayStatus.Continuing) },
                onStop = ::hide,
                onSupplementModeChange = ::setBubbleFocusable,
                onSupplement = { text -> state.value = state.value.applyEvent(AgentEvent.UserSupplementReceived(1, text)) },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP; x = dp(service, 72)
            y = (service.resources.displayMetrics.heightPixels * 0.6f).toInt(); windowAnimations = 0
        }
        runCatching { wm.addView(bubble, params) }.onSuccess { bubbleView = bubble; bubbleParams = params }
    }

    private fun setBubbleFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val next = if (focusable) params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (params.flags != next) { params.flags = next; runCatching { wm.updateViewLayout(view, params) } }
    }

    private fun composeView(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(context).also { view ->
        view.setViewTreeLifecycleOwner(owner)
        view.setContent {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            MiuixTheme(colors = if (night) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalSquircleEnabled provides false) { content() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun realHeight(context: Context, wm: WindowManager): Int = runCatching {
        val point = Point(); wm.defaultDisplay.getRealSize(point); point.y
    }.getOrDefault(context.resources.displayMetrics.heightPixels)

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class OverlayOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() { registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE); registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }
}
