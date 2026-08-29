package me.rerere.rikkahub.accessibility.overlay

import android.graphics.*
import android.view.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlin.math.*

/** Eta 风格全屏氛围光：穿透触摸并且不参与无障碍截图。 */
internal object EtaGlowOverlay {
    private val handler=Handler(Looper.getMainLooper())
    private var view: GlowView?=null
    private var wm: WindowManager?=null
    fun show(context: Context) { handler.post {
        if(view!=null) return@post
        val manager=context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
        val v=GlowView(context)
        val lp=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; title="RikkaHubEtaAgentGlow" }
        runCatching { manager.addView(v,lp) }.onSuccess { view=v; wm=manager }
    } }
    fun hide() { handler.post { val v=view; view=null; val m=wm; wm=null; v?.let { runCatching { m?.removeView(it) } } } }
    private class GlowView(c: Context): View(c) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private var angle=0f
        private val tick=object:Runnable { override fun run(){ angle=(angle+2.4f)%360f; invalidate(); postDelayed(this,33) } }
        init { setLayerType(View.LAYER_TYPE_SOFTWARE,null); post(tick) }
        override fun onDetachedFromWindow(){ removeCallbacks(tick); super.onDetachedFromWindow() }
        override fun onDraw(canvas:Canvas){
            val w=width.toFloat(); val h=height.toFloat(); val cx=w/2; val cy=h/2
            canvas.drawColor(Color.argb(79,0,0,0))
            val colors=intArrayOf(0xFFB0F2FF.toInt(),0xFFFAFAA3.toInt(),0xFFFFB472.toInt(),0xFFFB8DFF.toInt(),0xFFB0F2FF.toInt())
            val shader=SweepGradient(cx,cy,colors,floatArrayOf(0f,.27f,.5f,.75f,1f))
            shader.setLocalMatrix(Matrix().apply{setRotate(angle,cx,cy)})
            paint.style=Paint.Style.STROKE; paint.strokeWidth=40f; paint.maskFilter=BlurMaskFilter(40f,BlurMaskFilter.Blur.NORMAL); paint.shader=shader
            canvas.drawRoundRect(RectF(0f,0f,w,h),30f,30f,paint); paint.shader=null
        }
    }
}
