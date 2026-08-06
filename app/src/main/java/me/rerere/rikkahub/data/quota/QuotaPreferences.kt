package me.rerere.rikkahub.data.quota

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "QuotaPreferences"

private val Context.quotaStore by preferencesDataStore(name = "quota_settings")

class QuotaPreferences(
    private val context: Context,
) {
    companion object {
        val QUOTA_ENABLED = booleanPreferencesKey("quota_enabled")
        val QUOTA_OVERLAY_ENABLED = booleanPreferencesKey("quota_overlay_enabled")
        val QUOTA_PROVIDERS = stringPreferencesKey("quota_providers")
    }

    private val dataStore = context.quotaStore

    /** 配额功能总开关 */
    val quotaEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[QUOTA_ENABLED] ?: false
        }

    /** 悬浮窗开关 */
    val overlayEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[QUOTA_OVERLAY_ENABLED] ?: false
        }

    /** 已配置的提供商列表 */
    val providers: Flow<List<QuotaProviderConfig>> =
        dataStore.data.map { prefs ->
            val raw = prefs[QUOTA_PROVIDERS] ?: "[]"
            try {
                JsonInstant.decodeFromString<List<QuotaProviderConfig>>(raw)
            } catch (e: SerializationException) {
                Log.w(TAG, "Failed to decode quota providers: ${e.message}")
                emptyList()
            }
        }

    suspend fun setQuotaEnabled(enabled: Boolean) {
        dataStore.edit { it[QUOTA_ENABLED] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[QUOTA_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setProviders(providers: List<QuotaProviderConfig>) {
        dataStore.edit { it[QUOTA_PROVIDERS] = JsonInstant.encodeToString(providers) }
    }
}
