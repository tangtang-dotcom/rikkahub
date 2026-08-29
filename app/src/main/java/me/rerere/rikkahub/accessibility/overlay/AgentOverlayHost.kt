package me.rerere.rikkahub.accessibility.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService
import me.rerere.rikkahub.accessibility.internal.AndroidAgentLogger
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private var actionGeneration = 0L

    fun show(context: Context) {
        if (orbView != null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { show(context) }
            return
        }
        try {
            val service = RikkaAccessibilityService.current() ?: return
            if (orbView != null) return
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
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
                lifecycleOwner.destroy(); owner = null; windowManager = null; return
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
                lifecycleOwner.destroy(); owner = null; windowManager = null; return
            }
            orbView = orb
            // Match Eta: an action reveals only the collapsed orb. The bubble is
            // opened exclusively by the user's tap on the orb, never by a tool.
        } catch (error: Throwable) {
            // A transient overlay must never bring down AccessibilityService.
            // In particular, Compose attaches asynchronously during traversal,
            // so all setup failures need a guarded cleanup path here.
            AndroidAgentLogger.warn {
                    "Agent overlay setup failed; accessibility service remains alive " +
                        "error=${error.javaClass.simpleName}: ${error.message}"
                }
            listOf(bubbleView, orbView, glowView).forEach { view ->
                view?.let { runCatching { windowManager?.removeView(it) } }
            }
            bubbleView = null
            orbView = null
            glowView = null
            bubbleParams = null
            windowManager = null
            owner?.destroy()
            owner = null
        }
    }

    private fun runOnMainBlocking(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        main.post { try { block() } finally { latch.countDown() } }
        latch.await(1_500L, TimeUnit.MILLISECONDS)
    }

    fun showOperation(context: Context, action: String) {
        runOnMainBlocking {
            ++actionGeneration
            if (orbView == null && bubbleView == null) collapsed.value = true
            show(context)
            state.value = state.value.copy(
                phase = AgentOverlayPhase.RUNNING,
                status = AgentOverlayStatus.RunningTool(action),
            )
        }
    }

    fun showAction(context: Context, action: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        runOnMainBlocking {
            val generation = ++actionGeneration
            // Rikka has no Eta AgentRuntimeService run boundary, so establish the
            // same collapsed-at-operation-start invariant here. A foreground
            // action must not inherit a stale expanded bubble from an earlier run.
            if (orbView == null && bubbleView == null) collapsed.value = true
            show(context)
            state.value = state.value.copy(phase = AgentOverlayPhase.RUNNING, status = AgentOverlayStatus.RunningTool(action))
            AgentHapticFeedback.perform(context, when (action) {
                "long_press" -> AgentHapticFeedback.Type.LONG_PRESS
                "swipe" -> AgentHapticFeedback.Type.SWIPE
                else -> AgentHapticFeedback.Type.TAP
            })
            main.postDelayed({
                if (generation == actionGeneration && orbView != null && state.value.phase == AgentOverlayPhase.RUNNING) {
                    state.value = state.value.copy(phase = AgentOverlayPhase.PAUSED, status = AgentOverlayStatus.ToolCompleted(action))
                }
            }, durationMs.coerceAtLeast(650L) + 650L)
        }
    }

    fun hide() {
        main.post {
            ++actionGeneration
            val wm = windowManager
            listOf(bubbleView, orbView, glowView).forEach { it?.let { v -> runCatching { wm?.removeView(v) } } }
            bubbleView = null; orbView = null; glowView = null; bubbleParams = null; windowManager = null
            collapsed.value = true
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
        owner?.let { setSavedStateOwnerTag(view, it) }
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

    private fun setSavedStateOwnerTag(view: View, owner: SavedStateRegistryOwner) {
        // The AndroidX savedstate-android artifact contains the correct setter, but
        // its Android-only facade is not exposed to this Kotlin/KMP source set.
        // The tiny Java bridge still calls the official API; do not guess a tag id.
        SavedStateOwnerCompat.set(view, owner)
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = savedStateController.savedStateRegistry

        init {
            savedStateController.performAttach()
            // This host is a transient, non-Activity window, so it has no
            // previously persisted state. The registry still must be marked
            // restored before LifecycleRegistry dispatches ON_CREATE; otherwise
            // Recreator throws from consumeRestoredStateForKey().
            savedStateController.performRestore(Bundle())
        }

        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }
}
