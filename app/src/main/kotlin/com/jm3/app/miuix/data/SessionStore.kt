package com.jm3.app.miuix.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jm3.core.SessionCodec
import com.jm3.core.model.Jm3Session
import com.jm3.core.session.SessionStorage
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "jm3_session")

/** 会话持久化：DataStore Preferences 单键 JSON。 */
class SessionStore(private val context: Context) : SessionStorage {

    private val key = stringPreferencesKey("session")

    override suspend fun load(): Jm3Session? =
        context.sessionDataStore.data.first()[key]?.let { SessionCodec.decode(it) }

    override suspend fun save(session: Jm3Session) {
        context.sessionDataStore.edit { it[key] = SessionCodec.encode(session) }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
