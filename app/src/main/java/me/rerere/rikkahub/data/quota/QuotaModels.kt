package me.rerere.rikkahub.data.quota

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

/**
 * 鉴权方式。
 */
@Serializable
enum class QuotaAuthType(
    val displayName: String,
) {
    NONE("无鉴权"),
    BEARER("Bearer Token"),
    BASIC("Basic Auth"),
    COOKIE("Cookie"),
    CUSTOM_HEADER("自定义 Header"),
    QUERY_PARAM("Query 参数"),
}

/**
 * 已加密的凭证快照（存入 DataStore，实际值由 ProviderCredentialCipher 加密）。
 */
@Serializable
data class QuotaCredential(
    val authType: QuotaAuthType = QuotaAuthType.NONE,
    val encryptedValue: String = "",
    val keyName: String = "",
    val usernameMasked: String = "",
    val capturedAtMillis: Long = 0L,
)

/**
 * 预设平台模板 — 一键填入控制台 URL 和解析规则。
 */
@Serializable
enum class QuotaPlatform(
    val label: String,
    val consoleUrl: String,
    val jsSelector: String, // 用于 evaluateJavascript 读取 DOM 的 JS selector
    val regexPattern: String, // 从提取文本中匹配数值的正则
) {
    DEEPSEEK(
        label = "DeepSeek",
        consoleUrl = "https://platform.deepseek.com/usage",
        jsSelector =
            """
            (function() {
                var el = document.querySelector('.usage-item-value');
                return el ? el.textContent.trim() : '';
            })()
            """.trimIndent(),
        regexPattern = """[\d,.]+""",
    ),
    OPENROUTER(
        label = "OpenRouter",
        consoleUrl = "https://openrouter.ai/account/credits",
        jsSelector =
            """
            (function() {
                var el = document.querySelector('.text-2xl.font-bold');
                if (!el) el = document.querySelector('[data-testid="credit-balance"]');
                return el ? el.textContent.trim() : '';
            })()
            """.trimIndent(),
        regexPattern = """[\d,.]+""",
    ),
    SILICONFLOW(
        label = "SiliconFlow",
        consoleUrl = "https://siliconflow.cn/zh-cn/account/usage",
        jsSelector =
            """
            (function() {
                var el = document.querySelector('.balance-amount');
                if (!el) el = document.querySelector('.remaining-quota');
                return el ? el.textContent.trim() : '';
            })()
            """.trimIndent(),
        regexPattern = """[\d,.]+""",
    ),
}

/**
 * 单个提供商的额度配置（用户可编辑）。
 */
@Serializable
data class QuotaProviderConfig(
    val id: String = Uuid.random().toString(),
    val enabled: Boolean = false,
    val label: String = "",
    val consoleUrl: String = "",
    val jsSelector: String = "",
    val regexPattern: String = """[\d,.]+""",
    /** 总额度（用户手动设置，用于计算百分比；0 表示未设） */
    val totalQuota: Double = 0.0,
    // ── 多鉴权 ──
    val authType: QuotaAuthType = QuotaAuthType.NONE,
    /** 已加密凭证快照（仅 COOKIE/BEARER 等自动捕获模式才有值） */
    val credential: QuotaCredential? = null,
    /** 手动输入的未加密凭证（仅用于直传），永不持久化到磁盘 */
    @Transient
    val manualAuthValue: String = "",
    @Transient
    val manualAuthKeyName: String = "",
    @Transient
    val manualAuthUsername: String = "",
) {
    @Transient
    val platform: QuotaPlatform? =
        QuotaPlatform.entries.firstOrNull {
            it.consoleUrl == consoleUrl && it.label == label
        }
}

/**
 * 解析结果：从 WebView DOM 中提取的额度信息快照。
 */
data class QuotaSnapshot(
    val providerId: String,
    val rawText: String, // JS selector 返回的原始文本
    val numericValue: Double, // 从 rawText 解析出的数值（余额/使用量）
    val percentage: Double, // numericValue / totalQuota * 100（totalQuota=0 时回退 100）
    val status: QuotaStatus,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

enum class QuotaStatus {
    /** >= 30% — 充足 */
    GREEN,

    /** 10% ~ 30% — 紧张 */
    YELLOW,

    /** <= 10% — 危险 */
    RED,

    /** 解析失败或未获取到数据 */
    UNKNOWN,
}

/**
 * 聚合所有已启用提供商的快照，计算整体状态（取最低）。
 */
data class QuotaAggregate(
    val snapshots: List<QuotaSnapshot>,
    val overallStatus: QuotaStatus,
)

fun computeQuotaStatus(percentage: Double): QuotaStatus =
    when {
        percentage > 30.0 -> QuotaStatus.GREEN
        percentage in 10.0..30.0 -> QuotaStatus.YELLOW
        percentage < 10.0 -> QuotaStatus.RED
        else -> QuotaStatus.UNKNOWN
    }

fun aggregateQuotaStatus(snapshots: List<QuotaSnapshot>): QuotaAggregate {
    val statuses = snapshots.map { it.status }.filter { it != QuotaStatus.UNKNOWN }
    val overall =
        when {
            statuses.isEmpty() -> QuotaStatus.UNKNOWN
            statuses.any { it == QuotaStatus.RED } -> QuotaStatus.RED
            statuses.any { it == QuotaStatus.YELLOW } -> QuotaStatus.YELLOW
            statuses.all { it == QuotaStatus.GREEN } -> QuotaStatus.GREEN
            else -> QuotaStatus.UNKNOWN
        }
    return QuotaAggregate(snapshots, overall)
}
