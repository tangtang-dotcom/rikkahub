package me.rerere.rikkahub.accessibility.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

object AccessibilityActionEffects {
    private val h = Handler(Looper.getMainLooper())
    private var orb: Orb? = null
    private var glow: Glow? = null
    private var wm: WindowManager? = null
    private var hide: Runnable? = null

    fun showAction(c: Context, action: String, x1: Int, y1: Int, x2: Int, y2: Int, ms: Long) {
        showOrb(c)
        when (action) {
            "swipe" -> GestureIndicator.showSwipe(c, x1, y1, x2, y2, ms.toInt())
            "long_press" -> GestureIndicator.showLongPress(c, x1, y1, ms.toInt())
            else -> GestureIndicator.showTap(c, x1, y1)
        }

    }

    fun showOrb(c: Context) {
        h.post {
            if (orb != null) return@post
            val s = me.rerere.rikkahub.accessibility.RikkaAccessibilityService.current() ?: return@post
            val manager = s.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val d = s.resources.displayMetrics.density
            if (glow == null) {
                val g = Glow(s)
                val gp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.START; title = "RikkaHubAccessibilityGlow" }
                runCatching { manager.addView(g, gp) }.onSuccess { glow = g }
            }
            val v = Orb(s)
            v.setOnClickListener { hideOrb() }
            val p = WindowManager.LayoutParams((72*d).toInt(), (72*d).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.END; x = (12*d).toInt()
                y = (s.resources.displayMetrics.heightPixels*.58f).toInt(); title = "RikkaHubAccessibilityOrb"
            }
            runCatching { manager.addView(v, p) }.onSuccess {
                orb = v; wm = manager; v.alpha=0f; v.scaleX=.65f; v.scaleY=.65f
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    fun hideOrb() {
        h.post {
            hide?.let(h::removeCallbacks)
            val v = orb ?: return@post
            orb = null; val manager = wm; wm = null
            v.animate().alpha(0f).scaleX(.7f).scaleY(.7f).setDuration(180)
                .withEndAction { runCatching { manager?.removeView(v) } }.start()
        }
    }

    private class Glow(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private val tick = object : Runnable { override fun run() { phase = (phase + .006f) % 1f; invalidate(); postDelayed(this, 16L) } }
        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null); post(tick) }
        override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
        override fun onDraw(c: Canvas) {
            val e = 34f * resources.displayMetrics.density
            p.style = Paint.Style.STROKE; p.strokeWidth = e
            p.alpha = (45 * (.65f + .35f * ((kotlin.math.sin(phase * Math.PI * 2) + 1) / 2).toFloat())).toInt()
            p.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(0xFF2879FB.toInt(), 0xFFB14CFF.toInt(), 0xFF20D9C2), null, Shader.TileMode.MIRROR)
            val i = e / 2f; c.drawRect(i, i, width - i, height - i, p); p.shader = null
        }
    }

    private class Orb(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = resources.displayMetrics.density
        private var phase = 0.0
        private val tick = object : Runnable { override fun run() { phase += .12; invalidate(); postDelayed(this,16) } }
        init { post(tick) }
        override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
        override fun onDraw(c: Canvas) {
            val x=width/2f; val pulse=.72f+.28f*((kotlin.math.sin(phase)+1)/2).toFloat()
            p.style=Paint.Style.FILL; p.color=0x442879FB; c.drawCircle(x,x,31*d*pulse.toFloat(),p)
            p.color=0xFF2879FB.toInt(); c.drawCircle(x,x,18*d,p)
            p.color=0xFFFFFFFF.toInt(); c.drawCircle(x-5*d,x-6*d,4*d,p)
        }
    }
}
