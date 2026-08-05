package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import androidx.compose.runtime.Stable

/**
 * 自动任务配置：用户没空时 App 能自动发送消息激活会话继续任务。
 *
 * @param message 要自动发送的回复消息内容（如「继续」）
 * @param mode 触发模式：0 = 固定时间触发，1 = 不定时（空闲）触发
 * @param intervalSeconds 触发间隔（秒）
 */
@Stable
data class AutoTaskConfig(
    val message: String = "继续",
    val mode: Int = 0, // 0: 固定时间, 1: 不定时（空闲）
    val intervalSeconds: Int = 60,
)

// ---- SharedPreferences keys ----
private const val PREF_AUTO_TASK_MESSAGE = "auto_task_message"
private const val PREF_AUTO_TASK_MODE = "auto_task_mode"
private const val PREF_AUTO_TASK_INTERVAL = "auto_task_interval"

/**
 * 从 SharedPreferences 读取已保存的自动任务配置。
 * 可在非 Composable 上下文中使用（如 ChatVM）。
 */
fun readAutoTaskConfig(context: Context): AutoTaskConfig {
    val prefs = context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
    return AutoTaskConfig(
        message = prefs.getString(PREF_AUTO_TASK_MESSAGE, "继续") ?: "继续",
        mode = prefs.getInt(PREF_AUTO_TASK_MODE, 0),
        intervalSeconds = prefs.getInt(PREF_AUTO_TASK_INTERVAL, 60),
    )
}

/**
 * 将自动任务配置持久化到 SharedPreferences。
 */
fun writeAutoTaskConfig(
    context: Context,
    config: AutoTaskConfig,
) {
    context
        .getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_AUTO_TASK_MESSAGE, config.message)
        .putInt(PREF_AUTO_TASK_MODE, config.mode)
        .putInt(PREF_AUTO_TASK_INTERVAL, config.intervalSeconds)
        .apply()
}
