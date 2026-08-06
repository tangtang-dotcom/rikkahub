package me.rerere.rikkahub.data.quota

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 基于 AndroidKeyStore 的 AES-GCM 256 凭证加解密器。
 * 密钥永不离开 TEE/StrongBox，加密后以 Base64 输出供 DataStore 持久化。
 */
object ProviderCredentialCipher {
    private const val TAG = "CredentialCipher"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "rikkahub_quota_credential"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12 // bytes (96 bits)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
        keyGenerator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    /**
     * 加密明文，返回 Base64(IV + ciphertext)。
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + cipherText
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "encrypt failed", e)
            ""
        }
    }

    /**
     * 解密 Base64(IV + ciphertext)，返回明文。
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateKey()
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH, iv),
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "decrypt failed", e)
            ""
        }
    }

    /**
     * 删除 Keystore 中的密钥。
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteKey failed", e)
        }
    }
}
