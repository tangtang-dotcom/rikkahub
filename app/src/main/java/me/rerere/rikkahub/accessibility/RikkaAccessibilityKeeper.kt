package me.rerere.rikkahub.accessibility

import android.content.Context
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController

object RikkaAccessibilityKeeper {
    fun ensureAvailable(
        context: Context,
        protectionEnabled: Boolean,
        rootController: AndroidRootTerminalController?,
    ) {
        if (RikkaAccessibilityService.isAvailable()) return
        if (!protectionEnabled) error("ACCESSIBILITY_UNAVAILABLE")
        val controller = rootController ?: error("ACCESSIBILITY_PROTECTION_UNAVAILABLE")
        val component = "${context.packageName}/${RikkaAccessibilityService::class.java.name}"
        val dollar = '$'
        val command = """
            current=${dollar}(settings get secure enabled_accessibility_services 2>/dev/null)
            case ":${dollar}current:" in
              *":$component:"*) next="${dollar}current" ;;
              ":null:"|"::") next="$component" ;;
              *) next="${dollar}current:$component" ;;
            esac
            settings put secure enabled_accessibility_services "${dollar}next" &&
            settings put secure accessibility_enabled 1
        """.trimIndent()
        val result = runCatching { controller.executeSync(command, timeoutMs = 5_000, mergeStderr = true) }
            .getOrElse { error("ACCESSIBILITY_PROTECTION_UNAVAILABLE") }
        if (result.exitCode != 0 || result.timedOut) error("ACCESSIBILITY_PROTECTION_UNAVAILABLE")
        repeat(60) {
            if (RikkaAccessibilityService.isAvailable()) return
            Thread.sleep(100)
        }
        error("ACCESSIBILITY_REPAIR_TIMEOUT")
    }
}
