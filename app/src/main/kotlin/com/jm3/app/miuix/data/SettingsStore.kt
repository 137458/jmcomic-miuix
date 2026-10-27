package com.jm3.app.miuix.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jm3.core.settings.Domains
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.SettingsRepository
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "jm3_settings")

/** 应用设置持久化：主题模式、动态取色 + 自定义 API 线路（换行分隔），与 jm3_session 并列。 */
class SettingsStore(private val context: Context) : SettingsRepository {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val domainsKey = stringPreferencesKey("api_domains")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val paginationKey = stringPreferencesKey("pagination_mode")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val readerHorizontalKey = booleanPreferencesKey("reader_horizontal")
    private val volumeHintKey = booleanPreferencesKey("volume_hint_shown")
    private val trackMuteKey = longPreferencesKey("track_prompt_mute_until")

    override suspend fun themeMode(): ThemeMode =
        context.settingsDataStore.data.first()[themeKey]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    override suspend fun apiDomains(): List<String> =
        context.settingsDataStore.data.first()[domainsKey]
            ?.split('\n')
            ?.let(Domains::normalize)
            ?: emptyList()

    override suspend fun setApiDomains(domains: List<String>) {
        context.settingsDataStore.edit { it[domainsKey] = domains.joinToString("\n") }
    }

    override suspend fun dynamicColor(): Boolean =
        context.settingsDataStore.data.first()[dynamicColorKey] ?: false

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    override suspend fun paginationMode(): PaginationMode =
        context.settingsDataStore.data.first()[paginationKey]
            ?.let { runCatching { PaginationMode.valueOf(it) }.getOrNull() }
            ?: PaginationMode.INFINITE

    override suspend fun setPaginationMode(mode: PaginationMode) {
        context.settingsDataStore.edit { it[paginationKey] = mode.name }
    }

    /** 搜索词按行存储：写入前把换行折叠为空格，保证可逆。 */
    override suspend fun searchHistory(): List<String> =
        context.settingsDataStore.data.first()[searchHistoryKey]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    override suspend fun addSearchHistory(query: String) {
        val normalized = query.replace('\n', ' ').trim()
        if (normalized.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val existing = prefs[searchHistoryKey]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val next = (listOf(normalized) + existing.filterNot { it == normalized })
                .take(SettingsRepository.SEARCH_HISTORY_LIMIT)
            prefs[searchHistoryKey] = next.joinToString("\n")
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        context.settingsDataStore.edit { prefs ->
            val next = prefs[searchHistoryKey]
                ?.split('\n')
                ?.filter { it.isNotBlank() && it != query }
                ?: emptyList()
            prefs[searchHistoryKey] = next.joinToString("\n")
        }
    }

    override suspend fun clearSearchHistory() {
        context.settingsDataStore.edit { it[searchHistoryKey] = "" }
    }

    override suspend fun readerHorizontal(): Boolean =
        context.settingsDataStore.data.first()[readerHorizontalKey] ?: false

    override suspend fun setReaderHorizontal(enabled: Boolean) {
        context.settingsDataStore.edit { it[readerHorizontalKey] = enabled }
    }

    override suspend fun volumeKeyHintShown(): Boolean =
        context.settingsDataStore.data.first()[volumeHintKey] ?: false

    override suspend fun markVolumeKeyHintShown() {
        context.settingsDataStore.edit { it[volumeHintKey] = true }
    }

    override suspend fun trackPromptMuteUntil(): Long =
        context.settingsDataStore.data.first()[trackMuteKey] ?: 0L

    override suspend fun setTrackPromptMuteUntil(epochMillis: Long) {
        context.settingsDataStore.edit { it[trackMuteKey] = epochMillis }
    }
}
