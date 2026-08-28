package me.rerere.rikkahub.accessibility

import android.util.Log

internal object AccessibilityLog {
    fun debug(message: () -> String) = Log.d("RikkaAccessibility", message())
    fun warnThrottled(key: String, message: () -> String) = Log.w("RikkaAccessibility", message())
}
