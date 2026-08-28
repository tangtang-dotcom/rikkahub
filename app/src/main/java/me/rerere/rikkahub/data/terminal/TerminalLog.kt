package me.rerere.rikkahub.data.terminal

import android.util.Log

interface TerminalLog {
    fun info(message: String)
    fun warn(message: String)
}

internal object TerminalLogger : TerminalLog {
    private const val TAG = "RikkaHubTerminal"

    // Android's host-side unit-test stub throws here. Logging must never change a
    // terminal command's result or prevent cancellation cleanup from completing.
    override fun info(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    override fun warn(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
