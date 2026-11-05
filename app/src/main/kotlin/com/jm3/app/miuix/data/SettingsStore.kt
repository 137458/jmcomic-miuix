package com.jm3.app.miuix.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jm3.core.CredentialBlobStore
import com.jm3.core.settings.SettingsKeyStore
import com.jm3.core.settings.SettingsRepository
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "jm3_settings")

/** 凭据密文与设置同库：一次 write 就是一次 DataStore 事务，不会像 apply() 那样丢写。 */
private const val CREDENTIALS_BLOB = "credentials_blob"

/**
 * 设置的 DataStore Adapter。
 *
 * 只实现 [SettingsKeyStore] 的键值原语：键名是纯名称（DataStore 的键身份只看名字），
 * 值类型由调用该原语的能力接口决定，默认值、归一与约束全部来自 core 的默认方法。
 * 因此这里没有第二份业务规则，也不会与内存 Adapter 漂移。
 *
 * 键名与旧版逐字一致，升级不丢已存数据。
 */
class SettingsStore(private val context: Context) : SettingsRepository, CredentialBlobStore {

    override suspend fun readString(key: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun readBoolean(key: String): Boolean? =
        context.settingsDataStore.data.first()[booleanPreferencesKey(key)]

    override suspend fun readInt(key: String): Int? =
        context.settingsDataStore.data.first()[intPreferencesKey(key)]

    override suspend fun readLong(key: String): Long? =
        context.settingsDataStore.data.first()[longPreferencesKey(key)]

    override suspend fun writeBoolean(key: String, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun writeInt(key: String, value: Int) {
        context.settingsDataStore.edit { it[intPreferencesKey(key)] = value }
    }

    override suspend fun writeLong(key: String, value: Long) {
        context.settingsDataStore.edit { it[longPreferencesKey(key)] = value }
    }

    /** 读改写整段在一次 DataStore 事务内完成，两次并发写入不会互相覆盖。 */
    override suspend fun updateString(key: String, transform: (String?) -> String?) {
        context.settingsDataStore.edit { prefs ->
            val prefKey = stringPreferencesKey(key)
            val next = transform(prefs[prefKey])
            if (next == null) prefs.remove(prefKey) else prefs[prefKey] = next
        }
    }

    override suspend fun readCredentialBlob(): String? = readString(CREDENTIALS_BLOB)

    override suspend fun writeCredentialBlob(blob: String?) {
        updateString(CREDENTIALS_BLOB) { blob }
    }
}
