package com.jm3.core.settings

/** 主题模式：跟随系统/浅色/深色。 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 列表分页模式：无限滚动 / 点击翻页（原版设置项）。 */
enum class PaginationMode(val label: String, val displayName: String) {
    INFINITE("上拉加载更多", "无限滚动"),
    CLICK("点击切换分页", "点击翻页"),
}

/**
 * 应用设置的完整能力面。
 *
 * 这是一个组合接口，本身不承载任何行为：默认值、归一与约束都在各能力接口的默认实现里。
 * 调用方只依赖自己需要的那一块（在 function 参数上标注 [NovelSettings] 之类），
 * 装配处才需要完整的 [SettingsRepository]。
 */
interface SettingsRepository :
    AppearanceSettings,
    SkinSettings,
    PaginationSettings,
    BottomBarSettings,
    DomainSettings,
    SearchHistorySettings,
    ReaderSettings,
    NovelSettings,
    UpdateSettings,
    NotificationPermissionSettings {

    companion object {
        const val SEARCH_HISTORY_LIMIT = SearchHistory.LIMIT

        const val NOVEL_FONT_SIZE_MIN = NovelPreferences.MIN_FONT_SIZE
        const val NOVEL_FONT_SIZE_MAX = NovelPreferences.MAX_FONT_SIZE
        const val DEFAULT_NOVEL_FONT_SIZE = NovelPreferences.DEFAULT_FONT_SIZE
        const val DEFAULT_NOVEL_LANG = NovelPreferences.DEFAULT_LANG
    }
}

/**
 * 纯内存实现，供测试与缺省装配。
 *
 * 只实现键值原语：所有设置语义都来自能力接口的默认方法，因此它与 DataStore Adapter
 * 走的是同一份判定 —— 测试里验证过的行为在真机上同样成立。
 */
class InMemorySettingsRepository : SettingsRepository {

    private val values = mutableMapOf<String, Any>()

    override suspend fun readString(key: String): String? = synchronized(values) { values[key] as? String }

    override suspend fun readBoolean(key: String): Boolean? = synchronized(values) { values[key] as? Boolean }

    override suspend fun readInt(key: String): Int? = synchronized(values) { values[key] as? Int }

    override suspend fun readLong(key: String): Long? = synchronized(values) { values[key] as? Long }

    override suspend fun writeBoolean(key: String, value: Boolean) {
        synchronized(values) { values[key] = value }
    }

    override suspend fun writeInt(key: String, value: Int) {
        synchronized(values) { values[key] = value }
    }

    override suspend fun writeLong(key: String, value: Long) {
        synchronized(values) { values[key] = value }
    }

    override suspend fun updateString(key: String, transform: (String?) -> String?) {
        synchronized(values) {
            val next = transform(values[key] as? String)
            if (next == null) values.remove(key) else values[key] = next
        }
    }
}
