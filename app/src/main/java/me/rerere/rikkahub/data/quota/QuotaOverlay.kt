package me.rerere.rikkahub.data.quota

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 额度悬浮窗：常驻红黄绿状态点 + 点击展开额度卡片。
 * 复用 AgentOverlay 的 TYPE_APPLICATION_OVERLAY 模式，
 * 但独立管理（独立生命周期、不下雪影响 AgentOverlay）。
 */
object QuotaOverlay {
    private const val TAG = "QuotaOverlay"

    @Volatile private var rootView: View? = null

    @Volatile private var dotView: TextView? = null

    @Volatile private var expandedView: LinearLayout? = null

    @Volatile private var isExpanded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchDownTime = 0L

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(
        app: Application,
        preferences: QuotaPreferences,
    ) {
        if (!canShow(app)) {
            Log.d(TAG, "show: SYSTEM_ALERT_WINDOW not granted, no-op")
            return
        }
        if (scope != null) return // 已显示
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope?.launch {
            combine(
                preferences.overlayEnabled,
                preferences.providers,
            ) { overlayEnabled, providers ->
                overlayEnabled to providers
            }.collectLatest { (enabled, providers) ->
                if (enabled && providers.isNotEmpty()) {
                    mainHandler.post { showInternal(app) }
                } else {
                    mainHandler.post { hideInternal(app) }
                }
            }
        }
    }

    fun hide(app: Application) {
        scope?.cancel()
        scope = null
        mainHandler.post { hideInternal(app) }
    }

    fun updateSnapshot(snapshot: QuotaAggregate) {
        mainHandler.post {
            val dot = dotView ?: return@post
            val color =
                when (snapshot.overallStatus) {
                    QuotaStatus.GREEN -> Color.rgb(34, 197, 94)
                    QuotaStatus.YELLOW -> Color.rgb(234, 179, 8)
                    QuotaStatus.RED -> Color.rgb(239, 68, 68)
                    QuotaStatus.UNKNOWN -> Color.GRAY
                }
            (dot.background as? GradientDrawable)?.setColor(color)

            // 更新展开卡片内容
            val expanded = expandedView
            if (expanded != null && expanded.childCount > 0) {
                val container = expanded.getChildAt(0) as? LinearLayout ?: return@post
                // 清空旧数据（保留标题行）
                while (container.childCount > 1) {
                    container.removeViewAt(1)
                }
                snapshot.snapshots.forEach { snap ->
                    val row =
                        TextView(app).apply {
                            text =
                                buildString {
                                    append(snap.rawText.take(30))
                                    append(" → ")
                                    append("%.2f".format(snap.numericValue))
                                    if (snap.status == QuotaStatus.UNKNOWN) append(" (?)")
                                }
                            setTextColor(Color.WHITE)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setPadding(0, 4, 0, 4)
                        }
                    container.addView(row)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun showInternal(app: Application) {
        if (rootView != null) return

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = app.resources.displayMetrics.density

        // 根布局
        val root =
            LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

        // 状态点
        val dot =
            TextView(app).apply {
                text = "●"
                setTextColor(Color.GRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                val pad = (8 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.GRAY)
                        setSize((24 * density).toInt(), (24 * density).toInt())
                    }
                // 透明文字、纯色背景圆点
                setTextColor(Color.TRANSPARENT)
            }
        dotView = dot

        // 展开卡片（初始隐藏）
        val expanded =
            LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                val pad = (12 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background =
                    GradientDrawable().apply {
                        cornerRadius = 12f * density
                        setColor(0xDD1A1A2E.toInt())
                    }
            }
        expandedView = expanded

        // 展开卡片标题
        val title =
            TextView(app).apply {
                text = "额度查询"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
        expanded.addView(title)

        root.addView(dot)
        root.addView(expanded)

        // 拖动逻辑
        dot.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (view.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastTouchDownTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    val params = root.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    wm.updateViewLayout(root, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - lastTouchDownTime
                    val moved =
                        kotlin.math.abs(event.rawX - initialTouchX) > 10 ||
                            kotlin.math.abs(event.rawY - initialTouchY) > 10
                    if (!moved && duration < 300) {
                        // 短按：切换展开/收起
                        isExpanded = !isExpanded
                        expanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    } else if (duration > 800 && !moved) {
                        // 长按：关闭悬浮窗（需要重启开关才能再开）
                        hideInternal(app)
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = (16 * density).toInt()
                    y = (120 * density).toInt()
                }

        try {
            wm.addView(root, params)
            rootView = root
        } catch (t: Throwable) {
            Log.w(TAG, "addView failed", t)
        }
    }

    private fun hideInternal(app: Application) {
        val v = rootView ?: return
        rootView = null
        dotView = null
        expandedView = null
        isExpanded = false
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeViewImmediate(v)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed", t)
        }
    }
}
