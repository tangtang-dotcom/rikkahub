package me.rerere.rikkahub.data.log

import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 应用层日志记录器：通过 logcat 流式读取当前进程的应用层日志（OcrTransformer / ChatService 等），
 * 缓存在 App 内供「应用层日志」页面查看 / 导出 / 搜索 / 复制。
 *
 * 开关状态持久化在 SharedPreferences（key = "rikkahub.preferences" / "app_log_enabled"）。
 * 仅记录本进程（--pid）日志，不涉及其他应用。
 */
object AppLogRecorder {
    private const val TAG = "AppLogRecorder"

    private const val PREFS_NAME = "rikkahub.preferences"
    private const val PREF_APP_LOG_ENABLED = "app_log_enabled"

    /** 内存缓存上限：超过后丢弃最旧的日志 */
    private const val MAX_APP_LOGS = 500

    /** 单条消息最大长度，防止超长消息撑爆内存 */
    private const val MAX_MESSAGE_LENGTH = 2000

    /** 一行日志：时间戳 / 级别 / tag / 消息 */
    data class Entry(
        val timestamp: Long,
        val level: Char,
        val tag: String,
        val message: String,
    )

    private val logs = mutableListOf<Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun isEnabled(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_APP_LOG_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_APP_LOG_ENABLED, enabled)
            .apply()
        if (enabled) {
            start()
        } else {
            stop()
        }
    }

    /** 应用启动时调用：开关开启则开始记录 */
    fun startIfEnabled(context: Context) {
        if (isEnabled(context)) start()
    }

    /** 启动流式 logcat 监听（仅当前进程），幂等。 */
    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                var process: Process? = null
                try {
                    val pid = Process.myPid()
                    process =
                        Runtime.getRuntime().exec(
                            arrayOf("logcat", "--pid=$pid", "-v", "time"),
                        )
                    val reader = process!!.inputStream.bufferedReader(Charsets.UTF_8)
                    var pending: Entry? = null
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val entry = parseLine(line)
                        if (entry != null) {
                            // 上一行是完整日志，先落库；当前行作为新的待处理日志
                            pending?.let { add(it) }
                            pending = entry
                        } else {
                            // 多行消息的续行（无时间戳前缀）：追加到上一条日志
                            pending =
                                pending?.let { last ->
                                    last.copy(
                                        message =
                                            if (last.message.length >= MAX_MESSAGE_LENGTH) {
                                                last.message
                                            } else {
                                                last.message + "\n" + line
                                            },
                                    )
                                }
                        }
                    }
                    pending?.let { add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "logcat 监听异常，已停止", e)
                } finally {
                    process?.destroy()
                }
            }
    }

    /** 停止流式 logcat 监听。 */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** 当前缓存的应用层日志快照（最新在前）。 */
    fun getLogs(): List<Entry> =
        synchronized(logs) {
            logs.reversed()
        }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }

    /**
     * 按关键字过滤并导出为纯文本。
     *
     * @param keyword 关键字（null / 空白表示不过滤），大小写不敏感
     * @return 过滤后的日志文本（每行含时间、级别、TAG、消息）
     */
    fun exportText(keyword: String? = null): String {
        val filter = keyword?.trim().orEmpty().lowercase(Locale.getDefault())
        return buildString {
            getLogs().forEach { entry ->
                if (filter.isEmpty() || entry.tag.lowercase(Locale.getDefault()).contains(filter) ||
                    entry.message.lowercase(Locale.getDefault()).contains(filter)
                ) {
                    append(formatLine(entry))
                    append('\n')
                }
            }
        }
    }

    private fun add(entry: Entry) {
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > MAX_APP_LOGS) {
                repeat(logs.size - MAX_APP_LOGS) {
                    logs.removeAt(0)
                }
            }
        }
    }

    private fun formatLine(entry: Entry): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(entry.timestamp)
        return "$time ${entry.level} ${entry.tag}: ${entry.message}"
    }

    // logcat -v time 行格式：MM-DD HH:MM:SS.mmm  pid  tid  LEVEL tag: message
    private val LINE_REGEX =
        Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s?(.*)$""")

    private fun parseLine(line: String): Entry? {
        val match = LINE_REGEX.matchEntire(line) ?: return null
        val timeText = match.groupValues[1]
        val level = match.groupValues[2].first()
        val tag = match.groupValues[3]
        val message = match.groupValues[4]
        val timestamp =
            try {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .parse("$year-$timeText")
                    ?.time
                    ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        return Entry(
            timestamp = timestamp,
            level = level,
            tag = tag.take(64),
            message = message.take(MAX_MESSAGE_LENGTH),
        )
    }
}
