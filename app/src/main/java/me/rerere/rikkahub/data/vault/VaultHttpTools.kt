package me.rerere.rikkahub.data.vault

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 凭证输出掩码器（P0）。
 *
 * 目标：AI 工具输出中即使意外出现凭证明文，也不让其进入 LLM 上下文。
 * 两层防护：
 * 1. 主动 secret 值替换（本次调用解密的凭证值，内存态，用完即弃）
 * 2. 已知密钥格式正则兜底（sk- / ghp_ / AKIA / Bearer ...）
 *
 * 注意：掩码是「最后一道防线」，不是凭证管理的替代品——凭证仍只在 App 进程内解密。
 */
object SecretMasker {
    private const val MASK = "***"

    // 常见密钥格式（长度下限 + 字符集限制，降低误伤）
    private val PATTERNS = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{16,}"""), // OpenAI / DeepSeek / SiliconFlow 等
        Regex("""ghp_[A-Za-z0-9]{20,}"""), // GitHub PAT
        Regex("""gho_[A-Za-z0-9]{20,}"""), // GitHub OAuth
        Regex("""AKIA[A-Z0-9]{16}"""), // AWS access key id
        Regex("""xox[baprs]-[A-Za-z0-9\-]{10,}"""), // Slack
        Regex("""AIza[A-Za-z0-9_\-]{20,}"""), // Google API
        Regex("""ya29\.[A-Za-z0-9_\-]{20,}"""), // Google OAuth token
        Regex("""eyJ[A-Za-z0-9_\-]{20,}"""), // JWT
        Regex("""Bearer\s+[A-Za-z0-9_\-\.]{16,}"""), // Bearer token
        Regex("""token[:=]\s*[A-Za-z0-9_\-]{16,}"""), // token= 形式
    )

    /** 掩码文本：先替换主动 secret 值，再按已知格式正则兜底。 */
    fun mask(text: String, activeSecrets: List<String> = emptyList()): String {
        var out = text
        activeSecrets.filter { it.length >= 6 }.forEach { secret ->
            out = out.replace(secret, MASK)
        }
        PATTERNS.forEach { p ->
            out = p.replace(out, MASK)
        }
        return out
    }
}

/**
 * vault_http_exec — 带 Vault 凭证调用 HTTP(S) API（P1）。
 *
 * 安全模型（与 vault_ssh_exec 一致）：
 * - 凭证在 App 进程内解密并注入请求头；AI 只拿到响应
 * - 每次调用需人工审批（needsApproval = true），审批面展示 URL + 凭证名
 * - 响应体经 SecretMasker 掩码（防响应中夹带其他凭证明文）
 * - 审计记录（logAccess）
 */
fun vaultHttpExecTool(
    context: Context,
    repository: CredentialVaultRepository,
): Tool = Tool(
    name = "vault_http_exec",
    description = buildString {
        append("Call an HTTP(S) API using a credential stored in the vault. ")
        append("The credential value is injected as a request header inside the app process (default Authorization: <scheme> <value>); ")
        append("the AI only receives the response. Use for backend services / APIs that require a token or key. ")
        append("The response body is masked against credential patterns before returning. Approval required before making the request.")
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("method", buildJsonObject {
                    put("type", "string")
                    put("description", "HTTP method: GET/POST/PUT/PATCH/DELETE (default GET)")
                })
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "Full http(s) URL of the API endpoint")
                })
                put("credential_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Vault credential name whose value becomes the request header (see vault_credential_names)")
                })
                put("auth_scheme", buildJsonObject {
                    put("type", "string")
                    put("description", "Header value scheme: Bearer / Token / raw (default Bearer; raw = value used as-is)")
                })
                put("header_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Header name to inject the credential into (default Authorization)")
                })
                put("extra_headers", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional JSON object of extra request headers, e.g. {\"Accept\":\"application/json\"}. Cannot override the credential header.")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional request body (sent as JSON)")
                })
                put("timeout_seconds", buildJsonObject {
                    put("type", "integer")
                    put("description", "Timeout in seconds (default 30, max 120)")
                })
            },
            required = listOf("url", "credential_name"),
        )
    },
    needsApproval = { true },
    execute = { params -> runVaultHttpExec(context, repository, params.jsonObject) },
)

private const val MAX_RESPONSE_BODY = 8192
private const val MAX_TIMEOUT_SECONDS = 120L

private suspend fun runVaultHttpExec(
    context: Context,
    repository: CredentialVaultRepository,
    o: kotlinx.serialization.json.JsonObject,
): List<UIMessagePart> {
    val fail: (String) -> List<UIMessagePart> = { msg -> listOf(UIMessagePart.Text("❌ $msg")) }

    val method = (o["method"]?.jsonPrimitive?.contentOrNull ?: "GET").uppercase()
        .takeIf { it in setOf("GET", "POST", "PUT", "PATCH", "DELETE") }
        ?: return fail("method 只支持 GET/POST/PUT/PATCH/DELETE")
    val url = o["url"]?.jsonPrimitive?.contentOrNull?.trim() ?: return fail("url 必填")
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return fail("url 必须是 http(s):// 开头")
    }
    val credName = o["credential_name"]?.jsonPrimitive?.contentOrNull ?: return fail("credential_name 必填")
    val scheme = o["auth_scheme"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
    val headerName = o["header_name"]?.jsonPrimitive?.contentOrNull ?: "Authorization"
    val body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val timeout = (o["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: 30).coerceIn(5L, MAX_TIMEOUT_SECONDS)

    val extraHeaders = runCatching {
        val raw = o["extra_headers"]?.jsonPrimitive?.contentOrNull
        if (raw.isNullOrBlank()) emptyMap()
        else kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject.entries.mapNotNull { (k, v) ->
            (k to (v.jsonPrimitive.contentOrNull ?: return@mapNotNull null))
        }.toMap()
    }.getOrElse { return fail("extra_headers 必须是合法 JSON 对象") }

    val entry = repository.getByName(credName) ?: return fail("凭证不存在: $credName（用 vault_credential_names 查看可用名称）")
    val secret = repository.decryptValue(entry) ?: return fail("凭证解密失败: $credName")
    repository.logAccess(credName, "ai-tool", "http_exec")

    return try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .callTimeout(timeout * 2, TimeUnit.SECONDS)
                .build()
            val reqBuilder = Request.Builder().url(url)
            val headerValue = if (scheme == "raw") secret else "$scheme $secret"
            reqBuilder.header(headerName, headerValue)
            extraHeaders.forEach { (k, v) ->
                if (!k.equals(headerName, ignoreCase = true)) reqBuilder.header(k, v)
            }
            if (body.isNotBlank()) {
                reqBuilder.method(method, body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                reqBuilder.method(method, null)
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val code = resp.code
                val rawBody = resp.body?.string() ?: ""
                val truncated = rawBody.length > MAX_RESPONSE_BODY
                val bodyOut = if (truncated) rawBody.take(MAX_RESPONSE_BODY) + "\n...[截断 ${rawBody.length - MAX_RESPONSE_BODY} chars]" else rawBody
                val maskedBody = SecretMasker.mask(bodyOut, listOfNotNull(secret))
                val contentType = resp.header("Content-Type") ?: ""
                listOf(
                    UIMessagePart.Text(
                        buildString {
                            append("status=$code")
                            if (contentType.isNotBlank()) append("\ncontent-type=$contentType")
                            if (maskedBody.isNotBlank()) append("\n--- body ---\n$maskedBody")
                            append("\n（凭证已注入请求头，未出现在本次输出中）")
                        },
                    ),
                )
            }
        }
    } catch (e: Exception) {
        listOf(UIMessagePart.Text("❌ HTTP 请求失败: ${e.message}"))
    }
}
