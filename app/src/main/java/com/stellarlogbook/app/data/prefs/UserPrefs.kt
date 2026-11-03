package com.stellarlogbook.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Commander credentials + sync bookkeeping.
 * The EDSM API key lives in EncryptedSharedPreferences (Keystore-backed);
 * everything else sits in plain DataStore.
 */
class UserPrefs(context: Context) {

    private val appContext = context.applicationContext
    private val ds: DataStore<Preferences> get() = appContext.dataStore

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _apiKey = MutableStateFlow<String?>(null)

    val commanderName: Flow<String?> = ds.data.map { it[KEY_COMMANDER] }
    val lastSyncAt: Flow<Long> = ds.data.map { it[KEY_LAST_SYNC] ?: 0L }
    val syncIntervalHours: Flow<Int> = ds.data.map { it[KEY_SYNC_INTERVAL] ?: DEFAULT_INTERVAL }
    val wifiOnly: Flow<Boolean> = ds.data.map { it[KEY_WIFI_ONLY] ?: true }

    /** API key: read-through cache from encrypted storage (no flow — encrypted). */
    suspend fun apiKey(): String? {
        _apiKey.value?.let { return it }
        val stored = securePrefs.getString(KEY_API_KEY, null)
        _apiKey.value = stored
        return stored
    }

    suspend fun setCredentials(commander: String, apiKey: String) {
        securePrefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
        _apiKey.value = apiKey.trim()
        ds.edit { it[KEY_COMMANDER] = commander.trim() }
    }

    suspend fun setSyncPreferences(intervalHours: Int, wifiOnly: Boolean) {
        ds.edit { prefs ->
            prefs[KEY_SYNC_INTERVAL] = intervalHours
            prefs[KEY_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun markSynced(epochMillis: Long) {
        ds.edit { it[KEY_LAST_SYNC] = epochMillis }
    }

    companion object {
        const val DEFAULT_INTERVAL = 6
        private const val KEY_API_KEY = "edsm_api_key" // key inside EncryptedSharedPreferences
        private val KEY_COMMANDER = stringPreferencesKey("commander_name")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_at")
        private val KEY_SYNC_INTERVAL = intPreferencesKey("sync_interval_hours")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }
}
