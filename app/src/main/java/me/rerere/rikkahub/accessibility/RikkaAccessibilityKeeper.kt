package me.rerere.rikkahub.accessibility

import android.content.Context
import android.os.SystemClock
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

/** Eta accessibility lifecycle contract, adapted to RikkaHub's root recovery backend. */
object RikkaAccessibilityKeeper {
    fun ensureAvailable(context: Context, protectionEnabled: Boolean, rootController: AndroidRootTerminalController?) {
        val result = ensure(RikkaAccessibilityService::isAvailable, { protectionEnabled }, {
            val controller = rootController ?: return@ensure false
            val component = "${context.packageName}/${RikkaAccessibilityService::class.java.name}"
            val command = """
                current=\$(settings get secure enabled_accessibility_services 2>/dev/null)
                case ":\$current:" in
                  *":$component:"*) next="\$current" ;;
                  ":null:"|"::") next="$component" ;;
                  *) next="\$current:$component" ;;
                esac
                settings put secure enabled_accessibility_services "\$next" && settings put secure accessibility_enabled 1
            """.trimIndent()
            runCatching { controller.executeSync(command, timeoutMs = 5_000, mergeStderr = true) }
                .getOrNull()?.let { it.exitCode == 0 && !it.timedOut } == true
        }, {
            repeat(60) { if (RikkaAccessibilityService.isAvailable()) return@repeat; SystemClock.sleep(100) }
            RikkaAccessibilityService.isAvailable()
        })
        if (!result.available) error(result.code)
    }

    internal fun ensure(serviceAvailable: () -> Boolean, protectionEnabled: () -> Boolean, requestRecovery: () -> Boolean, awaitServiceBinding: () -> Boolean): AccessibilityEnableResult {
        if (serviceAvailable()) return AccessibilityEnableResult.available(false)
        if (!protectionEnabled()) return AccessibilityEnableResult.failure("ACCESSIBILITY_UNAVAILABLE", false)
        if (!requestRecovery()) return AccessibilityEnableResult.failure("ACCESSIBILITY_PROTECTION_UNAVAILABLE", true)
        if (!awaitServiceBinding()) return AccessibilityEnableResult.failure("ACCESSIBILITY_REPAIR_TIMEOUT", true)
        return AccessibilityEnableResult.available(true)
    }
}

internal data class AccessibilityEnableResult(val available: Boolean, val code: String = "", val recoveryRequested: Boolean) {
    companion object {
        fun available(recoveryRequested: Boolean) = AccessibilityEnableResult(true, recoveryRequested = recoveryRequested)
        fun failure(code: String, recoveryRequested: Boolean) = AccessibilityEnableResult(false, code, recoveryRequested)
    }
}
