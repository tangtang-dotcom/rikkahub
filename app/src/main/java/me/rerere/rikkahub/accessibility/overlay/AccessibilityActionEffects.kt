package me.rerere.rikkahub.accessibility.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.accessibility.RikkaAccessibilityService

/** Eta-style persistent overlay: glow + orb + expandable bubble + result state. */
object AccessibilityActionEffects {
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var glow: ComposeView? = null
    private var orb: ComposeView? = null
    private var glowOwner: EtaOverlayOwner? = null
    private var orbOwner: EtaOverlayOwner? = null
    private val ui = mutableStateOf(OverlayState())

    fun showAction(c: Context, action: String, x1: Int, y1: Int, x2: Int, y2: Int, ms: Long) {
        showOrb(c)
        ui.value = ui.value.copy(status = when (action) { "swipe" -> "正在滑动"; "long_press" -> "正在长按"; else -> "正在点击" })
        when (action) { "swipe" -> GestureIndicator.showSwipe(c,x1,y1,x2,y2,ms.toInt()); "long_press" -> GestureIndicator.showLongPress(c,x1,y1,ms.toInt()); else -> GestureIndicator.showTap(c,x1,y1) }
    }

    fun showOrb(c: Context) = handler.post {
        val s = RikkaAccessibilityService.current() ?: return@post
        val manager = s.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
        wm = manager
        if (glow == null) {
            val owner = EtaOverlayOwner()
            val v = ComposeView(s).apply { setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner); setContent { EtaGlow(ui.value) } }
            val p = WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT)
                .apply { gravity=Gravity.TOP or Gravity.START; title="EtaGlow" }
            runCatching { manager.addView(v,p) }.onSuccess { glow=v; glowOwner=owner }.onFailure { owner.destroy() }
        }
        if (orb == null) {
            val owner = EtaOverlayOwner()
            val v = ComposeView(s).apply { setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner); setContent { EtaPanel(ui, ::hideOrb) } }
            val d=s.resources.displayMetrics.density
            val p=WindowManager.LayoutParams((238*d).toInt(),(300*d).toInt(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT)
                .apply { gravity=Gravity.TOP or Gravity.END; x=(8*d).toInt(); y=(s.resources.displayMetrics.heightPixels*.40f).toInt(); title="EtaOrb" }
            runCatching { manager.addView(v,p) }.onSuccess { orb=v; orbOwner=owner }.onFailure { owner.destroy() }
        }
    }

    fun hideOrb() = handler.post {
        val m=wm; listOf(glow,orb).forEach { it?.let { v -> runCatching { m?.removeView(v) } } }
        glowOwner?.destroy(); orbOwner?.destroy(); glow=null; orb=null; glowOwner=null; orbOwner=null; wm=null; ui.value=OverlayState()
    }
}

private class EtaOverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lr=LifecycleRegistry(this); private val sc=SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get()=lr
    override val savedStateRegistry get()=sc.savedStateRegistry
    override val viewModelStore=ViewModelStore()
    init { sc.performAttach(); sc.performRestore(null as Bundle?); lr.currentState=Lifecycle.State.RESUMED }
    fun destroy(){ lr.currentState=Lifecycle.State.DESTROYED; viewModelStore.clear() }
}

private data class OverlayState(val status:String="已连接",val phase:Int=0)

@Composable private fun EtaGlow(s:OverlayState) {
    val t=rememberInfiniteTransition(label="glow")
    val a by t.animateFloat(.12f,.34f,infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="alpha")
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Color.Black.copy(alpha=.20f))
        drawRect(Brush.linearGradient(listOf(Color(0xFF6DE7FF).copy(alpha=a),Color(0xFFFF75D6).copy(alpha=a),Color(0xFFFFC857).copy(alpha=a))),style=androidx.compose.ui.graphics.drawscope.Stroke(38f))
    })
}

@Composable private fun EtaPanel(state:MutableState<OverlayState>,close:()->Unit) {
    val s by state; var expanded by remember { mutableStateOf(true) }; var text by remember { mutableStateOf("") }
    val t=rememberInfiniteTransition(label="orb"); val pulse by t.animateFloat(.65f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="pulse")
    Column(Modifier.fillMaxSize().padding(8.dp),horizontalAlignment=Alignment.End) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF35B7FF).copy(alpha=pulse),Color(0xFFAA62FF).copy(alpha=.45f),Color.Transparent))).clickable { expanded=!expanded },contentAlignment=Alignment.Center) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3287FF)))
        }
        AnimatedVisibility(expanded,enter=scaleIn()+fadeIn(),exit=scaleOut()+fadeOut()) {
            Card(Modifier.padding(top=6.dp).width(220.dp),shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.status,color=MaterialTheme.colorScheme.primary,fontSize=13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text,{text=it},singleLine=true,placeholder={Text("补充说明")},modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick={text=""}) { Text("补充") }
                        TextButton(onClick=close) { Text("停止") }
                    }
                }
            }
        }
    }
}
