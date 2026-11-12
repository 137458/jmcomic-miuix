package com.jm3.app.miuix.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jm3.core.SessionCodec
import com.jm3.core.model.Jm3Session
import com.jm3.core.session.SessionStorage
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(
    name = "jm3_session",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("SessionStore", "会话 DataStore 损坏，已重置为空会话", it)
        emptyPreferences()
    },
)

/** 会话持久化：DataStore Preferences 单键 JSON。 */
class SessionStore(private val context: Context) : SessionStorage {

    private val key = stringPreferencesKey("session")

    private val renewFailureKey = intPreferencesKey("renew_failure_count")

    override suspend fun load(): Jm3Session? =
        context.sessionDataStore.data.first()[key]?.let { SessionCodec.decode(it) }

    override suspend fun save(session: Jm3Session) {
        context.sessionDataStore.edit { it[key] = SessionCodec.encode(session) }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }

    override suspend fun renewFailureCount(): Int =
        context.sessionDataStore.data.first()[renewFailureKey] ?: 0

    override suspend fun setRenewFailureCount(count: Int) {
        context.sessionDataStore.edit { it[renewFailureKey] = count }
    }
}
