package me.rerere.rikkahub.utils

import java.util.Locale

/**
 * 日志脱敏工具：避免在请求日志中暴露明文 API key（如 `Authorization: Bearer sk-xxx`）。
 *
 * 策略：敏感请求头 / URL query 参数 / 自由文本中的 token 一律「前 3 后 3 + ***」脱敏，
 * 过短的值完全隐藏。
 */
object LogRedactor {
    /** 请求头中的敏感字段：匹配时对值做脱敏 */
    private val SENSITIVE_HEADER_KEYS =
        setOf(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "api-key",
            "apikey",
            "x-auth-token",
            "x-access-token",
            "token",
            "cookie",
            "set-cookie",
        )

    /** URL query 中的敏感参数名：匹配时对值做脱敏 */
    private val SENSITIVE_QUERY_KEYS =
        setOf(
            "key",
            "api_key",
            "apikey",
            "token",
            "access_token",
            "secret",
            "sign",
            "sig",
        )

    /** 明文 token 模式（sk-xxx / xai-xxx / Bearer xxx），用于 body / 文本兜底替换 */
    private val SECRET_TOKEN_REGEX =
        Regex(
            """(?i)(sk-[A-Za-z0-9_-]{6,}|xai-[A-Za-z0-9_-]{6,}|Bearer\s+[A-Za-z0-9._~+/=-]{8,})""",
        )

    /**
     * 对敏感值脱敏：前 3 后 3 + ***；长度不足 6 时完全隐藏。
     */
    fun maskSecret(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length <= 6) return "***"
        return trimmed.take(3) + "***" + trimmed.takeLast(3)
    }

    /**
     * 判断请求头是否敏感（大小写不敏感）。
     */
    fun isSensitiveHeader(key: String): Boolean = key.lowercase(Locale.getDefault()) in SENSITIVE_HEADER_KEYS

    /**
     * 单个请求头脱敏：敏感字段的值替换为脱敏后的值。
     */
    fun maskHeader(
        key: String,
        value: String,
    ): String = if (isSensitiveHeader(key)) maskSecret(value) else value

    /**
     * 对请求头 map 脱敏。
     */
    fun maskHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (key, value) -> maskHeader(key, value) }

    /**
     * 对 URL 脱敏：query 中敏感参数的值替换为 `***`。
     */
    fun maskUrl(url: String): String {
        val queryIndex = url.indexOf('?')
        if (queryIndex < 0) return url
        val base = url.substring(0, queryIndex)
        val query = url.substring(queryIndex + 1)
        val maskedQuery =
            query.split('&').joinToString("&") { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) {
                    pair
                } else {
                    val key = pair.substring(0, eq)
                    if (key.lowercase(Locale.getDefault()) in SENSITIVE_QUERY_KEYS) {
                        "$key=***"
                    } else {
                        pair
                    }
                }
            }
        return "$base?$maskedQuery"
    }

    /**
     * 对自由文本脱敏：替换 `sk-xxx` / `xai-xxx` / `Bearer xxx` 明文 token。
     * 不改变文本结构，可安全用于 JSON body。
     */
    fun maskText(text: String): String =
        SECRET_TOKEN_REGEX.replace(text) { match ->
            val raw = match.value
            if (raw.startsWith("Bearer ", ignoreCase = true)) {
                "Bearer " + maskSecret(raw.substringAfter(' '))
            } else {
                maskSecret(raw)
            }
        }
}
