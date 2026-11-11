package com.jm3.core.settings

/**
 * 设置存储的最小原语：Adapter 只负责把「键 + 类型」映射到自己的存储引擎。
 *
 * 这一层不带任何业务规则 —— 默认值、归一化、夹取、去重全部由能力接口
 * （[AppearanceSettings] 等）以默认实现给出，两个 Adapter（DataStore / 内存）
 * 因此共享同一份判定，不会各自漂移。
 */
interface SettingsKeyStore {

    suspend fun readString(key: String): String?

    suspend fun readBoolean(key: String): Boolean?

    suspend fun readInt(key: String): Int?

    suspend fun readLong(key: String): Long?

    suspend fun writeBoolean(key: String, value: Boolean)

    suspend fun writeInt(key: String, value: Int)

    suspend fun writeLong(key: String, value: Long)

    /**
     * 原子读改写一个字符串键；`transform` 返回 null 表示删除该键。
     *
     * 搜索历史这类「读-改-写」必须整段落在一次存储事务里，否则并发两次写入会丢一次。
     */
    suspend fun updateString(key: String, transform: (String?) -> String?)

    suspend fun writeString(key: String, value: String?) {
        updateString(key) { value }
    }
}

/** 设置键名：只有能力接口的默认实现与 Adapter 的键映射需要它。 */
internal object SettingsKeys {

    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"

    /** 界面风格：与 Eta 的 appearance_ui_style 同名，未设置时不写键。 */
    const val UI_SKIN = "appearance_ui_style"
    const val PAGINATION_MODE = "pagination_mode"
    const val BOTTOM_BAR_SIZE = "bottom_bar_size"
    const val BOTTOM_BAR_FORM = "bottom_bar_form"
    const val WIDE_SCREEN_NAVIGATION = "wide_screen_navigation"
    const val API_DOMAINS = "api_domains"
    const val SEARCH_HISTORY = "search_history"
    const val READER_HORIZONTAL = "reader_horizontal"
    const val READER_SPREAD = "reader_spread"
    const val READER_VOLUME_KEY_PAGING = "reader_volume_key_paging"
    const val READER_KEEP_SCREEN_ON = "reader_keep_screen_on"
    const val READER_BRIGHTNESS = "reader_brightness"
    const val READER_IMAGE_QUALITY = "reader_image_quality"
    const val READER_PREFETCH_PAGES = "reader_prefetch_pages"
    const val VOLUME_KEY_HINT = "volume_hint_shown"
    const val NOTIFICATION_ASKED = "notification_permission_asked"
    const val TRACK_PROMPT_MUTE_UNTIL = "track_prompt_mute_until"
    const val NOVEL_FONT_SIZE = "novel_font_size"
    const val NOVEL_LANG = "novel_lang"
    const val AUTO_CHECK_UPDATE = "auto_check_update"
    const val IGNORED_UPDATE_VERSION = "ignored_update_version"
    const val PROMPTED_UPDATE_VERSION = "prompted_update_version"

    /** 每部小说独立成键：避免每次读写都反序列化整张进度表。 */
    fun novelLastChapter(novelId: String): String = "novel_last_" + novelId

    /** 每个章节独立成键：保存该章节上次读到的页码。 */
    fun readerLastPage(photoId: String): String = "reader_last_page_" + photoId

    /** 每个账号独立成键：记录最后确认已签到的本地日期（YYYY-MM-DD）。 */
    fun checkInLastDate(uid: String): String = "check_in_last_date_" + uid
}
