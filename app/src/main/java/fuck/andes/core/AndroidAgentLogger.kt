package fuck.andes.core

import android.util.Log

object AndroidAgentLogger {
    fun debug(message: () -> String) { Log.d("EtaAccessibility", message()) }
    fun info(message: String) { Log.i("EtaAccessibility", message) }
    fun info(message: () -> String) { Log.i("EtaAccessibility", message()) }
    fun warn(message: String) { Log.w("EtaAccessibility", message) }
    fun warn(message: () -> String) { Log.w("EtaAccessibility", message()) }
    fun warnThrottled(key: String, message: () -> String) { Log.w("EtaAccessibility", "[$key] ${message()}") }
}
