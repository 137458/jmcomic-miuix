package com.jm3.core.settings

/** 主题模式：跟随系统/浅色/深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 列表分页模式：无限滚动 / 点击翻页（原版设置项）。 */
enum class PaginationMode(val label: String) {
    INFINITE("上拉加载更多"),
    CLICK("点击切换分页"),
}

/** 应用设置持久化：主题模式与自定义 API 线路。缺省值：主题 SYSTEM、线路空表（库默认解析）。 */
interface SettingsRepository {
    suspend fun themeMode(): ThemeMode

    suspend fun setThemeMode(mode: ThemeMode)

    /** 已归一化的自定义 API 线路域名；空表 = 库默认自动解析。 */
    suspend fun apiDomains(): List<String>

    suspend fun setApiDomains(domains: List<String>)

    /**
     * 动态取色（Material You）开关。默认实现返回 false，
     * 保证既有实现（app 的 SettingsStore）在 UI 接线前仍可编译；接线后由实现覆盖。
     */
    suspend fun dynamicColor(): Boolean = false

    /** 默认空实现，理由同 [dynamicColor]。 */
    suspend fun setDynamicColor(enabled: Boolean) {}

    /** 列表分页模式，默认无限滚动。 */
    suspend fun paginationMode(): PaginationMode = PaginationMode.INFINITE

    suspend fun setPaginationMode(mode: PaginationMode) {}

    /** 搜索历史，最近一次在前，上限 [SEARCH_HISTORY_LIMIT]。 */
    suspend fun searchHistory(): List<String> = emptyList()

    /** 记录一次搜索；空词忽略，重复词前移并截断。 */
    suspend fun addSearchHistory(query: String) {}

    suspend fun removeSearchHistory(query: String) {}

    suspend fun clearSearchHistory() {}

    /** 阅读器左右翻页模式（原版「左右阅读/直向阅读」），默认直向。 */
    suspend fun readerHorizontal(): Boolean = false

    suspend fun setReaderHorizontal(enabled: Boolean) {}

    /** 音量键翻页提示是否已展示过（原版只提示一次）。 */
    suspend fun volumeKeyHintShown(): Boolean = false

    suspend fun markVolumeKeyHintShown() {}

    /** 末页追更引导的静默截止时间（epoch 毫秒，0 表示未静默）。 */
    suspend fun trackPromptMuteUntil(): Long = 0L

    suspend fun setTrackPromptMuteUntil(epochMillis: Long) {}

    /** 小说阅读字号（sp），默认 [DEFAULT_NOVEL_FONT_SIZE]。 */
    suspend fun novelFontSize(): Int = DEFAULT_NOVEL_FONT_SIZE

    suspend fun setNovelFontSize(sizeSp: Int) {}

    /** 小说正文语言：tw（繁体）/ cn（简体）。 */
    suspend fun novelLang(): String = DEFAULT_NOVEL_LANG

    suspend fun setNovelLang(lang: String) {}

    /** 某部小说最后阅读的章节 id（原版 novelRead 进度）；未读过为 null。 */
    suspend fun novelLastChapter(novelId: String): String? = null

    suspend fun setNovelLastChapter(novelId: String, chapterId: String) {}

    companion object {
        const val SEARCH_HISTORY_LIMIT = 10

        const val NOVEL_FONT_SIZE_MIN = 14
        const val NOVEL_FONT_SIZE_MAX = 26
        const val DEFAULT_NOVEL_FONT_SIZE = 18
        const val DEFAULT_NOVEL_LANG = "tw"
    }
}

/** 纯内存实现，供测试与缺省装配。 */
class InMemorySettingsRepository : SettingsRepository {

    private var mode: ThemeMode = ThemeMode.SYSTEM
    private var domains: List<String> = emptyList()
    private var dynamic = false
    private var pagination = PaginationMode.INFINITE
    private val searchHistory = mutableListOf<String>()
    private var readerHorizontal = false
    private var volumeHintShown = false
    private var trackPromptMuteUntil = 0L
    private var novelFontSize = SettingsRepository.DEFAULT_NOVEL_FONT_SIZE
    private var novelLang = SettingsRepository.DEFAULT_NOVEL_LANG
    private val novelProgress = mutableMapOf<String, String>()

    override suspend fun themeMode(): ThemeMode = mode

    override suspend fun setThemeMode(mode: ThemeMode) {
        this.mode = mode
    }

    override suspend fun apiDomains(): List<String> = domains

    override suspend fun setApiDomains(domains: List<String>) {
        this.domains = domains
    }

    override suspend fun dynamicColor(): Boolean = dynamic

    override suspend fun setDynamicColor(enabled: Boolean) {
        this.dynamic = enabled
    }

    override suspend fun paginationMode(): PaginationMode = pagination

    override suspend fun setPaginationMode(mode: PaginationMode) {
        pagination = mode
    }

    override suspend fun searchHistory(): List<String> = searchHistory.toList()

    override suspend fun addSearchHistory(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        searchHistory.remove(normalized)
        searchHistory.add(0, normalized)
        while (searchHistory.size > SettingsRepository.SEARCH_HISTORY_LIMIT) {
            searchHistory.removeAt(searchHistory.lastIndex)
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        searchHistory.remove(query)
    }

    override suspend fun clearSearchHistory() {
        searchHistory.clear()
    }

    override suspend fun readerHorizontal(): Boolean = readerHorizontal

    override suspend fun setReaderHorizontal(enabled: Boolean) {
        readerHorizontal = enabled
    }

    override suspend fun volumeKeyHintShown(): Boolean = volumeHintShown

    override suspend fun markVolumeKeyHintShown() {
        volumeHintShown = true
    }

    override suspend fun trackPromptMuteUntil(): Long = trackPromptMuteUntil

    override suspend fun setTrackPromptMuteUntil(epochMillis: Long) {
        trackPromptMuteUntil = epochMillis
    }

    override suspend fun novelFontSize(): Int = novelFontSize

    override suspend fun setNovelFontSize(sizeSp: Int) {
        novelFontSize = sizeSp.coerceIn(
            SettingsRepository.NOVEL_FONT_SIZE_MIN,
            SettingsRepository.NOVEL_FONT_SIZE_MAX,
        )
    }

    override suspend fun novelLang(): String = novelLang

    override suspend fun setNovelLang(lang: String) {
        novelLang = if (lang == "cn") "cn" else "tw"
    }

    override suspend fun novelLastChapter(novelId: String): String? = novelProgress[novelId]

    override suspend fun setNovelLastChapter(novelId: String, chapterId: String) {
        novelProgress[novelId] = chapterId
    }
}
