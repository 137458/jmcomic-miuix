package com.jm3.app.miuix.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jm3.core.settings.Domains
import com.jm3.core.settings.SettingsRepository
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "jm3_settings")

/** 应用设置持久化：主题模式、动态取色 + 自定义 API 线路（换行分隔），与 jm3_session 并列。 */
class SettingsStore(private val context: Context) : SettingsRepository {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val domainsKey = stringPreferencesKey("api_domains")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

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
}
