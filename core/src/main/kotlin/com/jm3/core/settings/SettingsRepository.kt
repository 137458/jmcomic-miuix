package com.jm3.core.settings

/** 主题模式：跟随系统/浅色/深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 应用设置持久化：主题模式与自定义 API 线路。缺省值：主题 SYSTEM、线路空表（库默认解析）。 */
interface SettingsRepository {
    suspend fun themeMode(): ThemeMode

    suspend fun setThemeMode(mode: ThemeMode)

    /** 已归一化的自定义 API 线路域名；空表 = 库默认自动解析。 */
    suspend fun apiDomains(): List<String>

    suspend fun setApiDomains(domains: List<String>)
}

/** 纯内存实现，供测试与缺省装配。 */
class InMemorySettingsRepository : SettingsRepository {

    private var mode: ThemeMode = ThemeMode.SYSTEM
    private var domains: List<String> = emptyList()

    override suspend fun themeMode(): ThemeMode = mode

    override suspend fun setThemeMode(mode: ThemeMode) {
        this.mode = mode
    }

    override suspend fun apiDomains(): List<String> = domains

    override suspend fun setApiDomains(domains: List<String>) {
        this.domains = domains
    }
}
