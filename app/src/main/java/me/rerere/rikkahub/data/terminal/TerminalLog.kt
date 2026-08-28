package me.rerere.rikkahub.data.terminal

import android.util.Log

interface TerminalLog {
    fun info(message: String)
    fun warn(message: String)
}

internal object TerminalLogger : TerminalLog {
    private const val TAG = "RikkaHubTerminal"
    override fun info(message: String) = Log.i(TAG, message).let { Unit }
    override fun warn(message: String) = Log.w(TAG, message).let { Unit }
}
