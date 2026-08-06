package me.rerere.rikkahub.data.quota

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 凭证生命周期管理：加密持久化 / 解密还原 / 脱敏展示 / 清除。
 * 依赖 [ProviderCredentialCipher] 做 AES-GCM 加解密。
 */
class QuotaCredentialManager(
    private val preferences: QuotaPreferences,
) {
    private val cipher = ProviderCredentialCipher

    /**
     * 返回 provider 的解密后原始凭证值，供 WebView Cookie 注入或 Header 注入使用。
     */
    suspend fun getDecryptedValue(providerId: String): String {
        val providers = preferences.providers.first()
        val provider = providers.find { it.id == providerId } ?: return ""
        val cred = provider.credential ?: return ""
        if (cred.encryptedValue.isBlank()) return ""
        return cipher.decrypt(cred.encryptedValue)
    }

    /**
     * 捕获登录凭证并加密保存。
     * @param rawValue 明文凭证（token / cookie / password）
     */
    suspend fun captureAndSave(
        providerId: String,
        authType: QuotaAuthType,
        rawValue: String,
        keyName: String = "",
        usernameMasked: String = "",
    ) {
        if (rawValue.isBlank()) return
        val encrypted = cipher.encrypt(rawValue)
        if (encrypted.isBlank()) {
            Log.w("QuotaCredential", "encrypt returned empty, skipping save")
            return
        }
        val credential =
            QuotaCredential(
                authType = authType,
                encryptedValue = encrypted,
                keyName = keyName,
                usernameMasked = usernameMasked,
                capturedAtMillis = System.currentTimeMillis(),
            )
        val providers = preferences.providers.first()
        val updated =
            providers.map {
                if (it.id == providerId) it.copy(credential = credential) else it
            }
        preferences.setProviders(updated)
    }

    /**
     * 脱敏展示凭证信息：前4 + "***" + 后4
     */
    fun maskValue(raw: String): String {
        if (raw.length <= 8) return "***"
        return raw.take(4) + "***" + raw.takeLast(4)
    }

    /**
     * 返回脱敏后的凭证字符串供 UI 展示。
     */
    suspend fun getMaskedValue(providerId: String): String {
        val decrypted = getDecryptedValue(providerId)
        return if (decrypted.isNotBlank()) maskValue(decrypted) else ""
    }

    /**
     * 清除指定 provider 的凭证。
     */
    suspend fun clearCredential(providerId: String) {
        val providers = preferences.providers.first()
        val updated =
            providers.map {
                if (it.id == providerId) {
                    it.copy(credential = null)
                } else {
                    it
                }
            }
        preferences.setProviders(updated)
    }
}
