package me.rerere.rikkahub.data.quota

import android.util.Log
import android.webkit.WebView

private const val TAG = "QuotaParser"

/**
 * 通过 WebView.evaluateJavascript 执行 JS 选择器抽取文本，
 * 再通过正则解析出数值，结合总配额计算百分比和状态。
 */
object QuotaParser {
    /**
     * 对单个 provider 的 WebView 执行 JS 提取并返回 [QuotaSnapshot]。
     * 调用方应在页面加载完成后再调用。
     */
    fun evaluate(
        webView: WebView,
        config: QuotaProviderConfig,
        onResult: (QuotaSnapshot) -> Unit,
    ) {
        val jsCode =
            config.jsSelector.ifBlank {
                // 默认：尝试读 body 文本
                "(function() { return document.body ? document.body.innerText.substring(0, 200) : ''; })()"
            }
        webView.evaluateJavascript(jsCode) { result ->
            val rawText = parseJsResult(result)
            Log.d(TAG, "evaluateJavascript raw: $rawText (config=${config.label})")

            val numericValue = extractNumeric(rawText, config.regexPattern)
            val percentage =
                if (config.totalQuota > 0.0) {
                    (numericValue / config.totalQuota * 100.0).coerceIn(0.0, 100.0)
                } else {
                    // 未设总额度：优先认为 100%，让用户手动设 totalQuota
                    100.0
                }
            val status = computeQuotaStatus(percentage)

            onResult(
                QuotaSnapshot(
                    providerId = config.id,
                    rawText = rawText,
                    numericValue = numericValue,
                    percentage = percentage,
                    status = status,
                ),
            )
        }
    }

    /**
     * evaluateJavascript 回调返回的 JSON 字符串（含引号），去掉外层引号并反转义。
     */
    private fun parseJsResult(raw: String): String {
        var s = raw.trim()
        if (s == "null") return ""
        // JS 返回 JSON 字符串，如 "\"123.45\"" 或 "null"
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * 从文本中用正则提取第一个数值。
     * 示例： "$12.34" → 12.34； "剩余 1,234 条" → 1234.0
     */
    private fun extractNumeric(
        text: String,
        regexPattern: String,
    ): Double {
        if (text.isBlank()) return 0.0
        val pattern =
            try {
                Regex(regexPattern)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid regex pattern: $regexPattern")
                Regex("""[\d,.]+""")
            }
        val match = pattern.find(text) ?: return 0.0
        return match.value
            .replace(",", "")
            .replace(" ", "")
            .toDoubleOrNull() ?: 0.0
    }
}
