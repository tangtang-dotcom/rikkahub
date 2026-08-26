package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.Serializable

@Serializable
enum class ToolRisk { READ_ONLY, LOW_RISK_WRITE, EXTERNAL_SIDE_EFFECT, DESTRUCTIVE }

@Serializable
data class ToolCapability(
    val name: String,
    val version: Int = 1,
    val risk: ToolRisk = ToolRisk.READ_ONLY,
    val requiredPermissions: List<String> = emptyList(),
    val supportsCancellation: Boolean = true,
    val supportsPagination: Boolean = false,
    val idempotent: Boolean = false,
)

@Serializable
data class ToolError(val code: String, val message: String, val retryable: Boolean = false)

@Serializable
data class ToolEnvelope<T>(val ok: Boolean, val data: T? = null, val error: ToolError? = null, val truncated: Boolean = false)

internal fun validateCapability(capability: ToolCapability) {
    require(capability.name.matches(Regex("[a-zA-Z0-9_.-]+"))) { "invalid capability name" }
    require(capability.version > 0) { "capability version must be positive" }
    require(capability.requiredPermissions.distinct().size == capability.requiredPermissions.size) { "duplicate required permission" }
}
