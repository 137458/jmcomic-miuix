package com.jm3.core.settings

import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.decodeReaderProgress
import com.jm3.core.reader.encodeReaderProgress

/**
 * 设置按能力切分：每个接口只描述一类设置的默认值与归一规则，实现落在默认方法里。
 *
 * Adapter 不再复刻这些判定 —— 它只实现 [SettingsKeyStore] 的存取原语，
 * 于是「DataStore 里存了什么」和「设置语义是什么」彻底分开。
 */

/** 外观：主题模式与动态取色。 */
interface AppearanceSettings : SettingsKeyStore {

    suspend fun themeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == readString(SettingsKeys.THEME_MODE) }
            ?: ThemeMode.SYSTEM

    suspend fun setThemeMode(mode: ThemeMode) {
        writeString(SettingsKeys.THEME_MODE, mode.name)
    }

    suspend fun dynamicColor(): Boolean = readBoolean(SettingsKeys.DYNAMIC_COLOR) ?: false

    suspend fun setDynamicColor(enabled: Boolean) {
        writeBoolean(SettingsKeys.DYNAMIC_COLOR, enabled)
    }
}

/** 列表分页语义：无限滚动 / 点击翻页。 */
interface PaginationSettings : SettingsKeyStore {

    suspend fun paginationMode(): PaginationMode =
        PaginationMode.entries.firstOrNull { it.name == readString(SettingsKeys.PAGINATION_MODE) }
            ?: PaginationMode.INFINITE

    suspend fun setPaginationMode(mode: PaginationMode) {
        writeString(SettingsKeys.PAGINATION_MODE, mode.name)
    }
}

/**
 * 底栏尺寸与形态：读回 null 表示用户从未显式设置，调用方按窗口断点解析默认值。
 *
 * 非法持久化值也读成 null，与「未设置」走同一条断点回落。
 */
interface BottomBarSettings : SettingsKeyStore {

    suspend fun bottomBarSizeOrNull(): BottomBarSize? =
        BottomBarSize.entries.firstOrNull { it.name == readString(SettingsKeys.BOTTOM_BAR_SIZE) }

    suspend fun setBottomBarSize(size: BottomBarSize) {
        writeString(SettingsKeys.BOTTOM_BAR_SIZE, size.name)
    }

    /** 清空显式尺寸选择，回到按设备自适应。 */
    suspend fun clearBottomBarSize() {
        writeString(SettingsKeys.BOTTOM_BAR_SIZE, null)
    }

    suspend fun bottomBarFormOrNull(): BottomBarForm? =
        BottomBarForm.entries.firstOrNull { it.name == readString(SettingsKeys.BOTTOM_BAR_FORM) }

    suspend fun setBottomBarForm(form: BottomBarForm) {
        writeString(SettingsKeys.BOTTOM_BAR_FORM, form.name)
    }

    /** 宽屏导航：读回 null 表示未显式设置，调用方默认按底栏解析。 */
    suspend fun wideScreenNavigationOrNull(): WideScreenNavigation? =
        WideScreenNavigation.entries.firstOrNull { it.name == readString(SettingsKeys.WIDE_SCREEN_NAVIGATION) }

    suspend fun setWideScreenNavigation(navigation: WideScreenNavigation) {
        writeString(SettingsKeys.WIDE_SCREEN_NAVIGATION, navigation.name)
    }

    /** 清空显式选择，回到默认底栏。 */
    suspend fun clearWideScreenNavigation() {
        writeString(SettingsKeys.WIDE_SCREEN_NAVIGATION, null)
    }
}

/**
 * 自定义 API 线路：读与写都走 [Domains]，对称归一。
 *
 * 调用方只管交出用户输入的行，不需要在写之前自己补一遍归一化。
 */
interface DomainSettings : SettingsKeyStore {

    /** 归一化后的线路表；空表 = 库默认自动解析。 */
    suspend fun apiDomains(): List<String> = Domains.decode(readString(SettingsKeys.API_DOMAINS))

    suspend fun setApiDomains(domains: List<String>) {
        writeString(SettingsKeys.API_DOMAINS, Domains.encode(domains))
    }
}

/** 搜索历史：折行归一、去重前移、按 [SearchHistory.LIMIT] 截断。 */
interface SearchHistorySettings : SettingsKeyStore {

    /** 最近一次在前。 */
    suspend fun searchHistory(): List<String> = SearchHistory.decode(readString(SettingsKeys.SEARCH_HISTORY))

    /** 记录一次搜索；空词忽略，重复词前移并截断。 */
    suspend fun addSearchHistory(query: String) {
        updateString(SettingsKeys.SEARCH_HISTORY) { raw ->
            val existing = SearchHistory.decode(raw)
            val next = SearchHistory.add(existing, query)
            if (next == existing) raw else SearchHistory.encode(next)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        updateString(SettingsKeys.SEARCH_HISTORY) { raw ->
            SearchHistory.encode(SearchHistory.remove(SearchHistory.decode(raw), query))
        }
    }

    suspend fun clearSearchHistory() {
        updateString(SettingsKeys.SEARCH_HISTORY) { null }
    }
}

/**
 * 漫画阅读进度：按漫画 id 记住上次读到的页码。
 *
 * 编解码规则收敛在 [com.jm3.core.reader.decodeReaderProgress]，
 * Adapter 只需实现键值原语即可获得进度语义。
 */
interface ReaderProgressSettings : SettingsKeyStore {

    /** 某部漫画上次读到的进度；没有记录或记录损坏时为 null。 */
    suspend fun readerLastPage(photoId: String): ReaderPageProgress? =
        decodeReaderProgress(readString(SettingsKeys.readerLastPage(photoId)))

    suspend fun setReaderLastPage(photoId: String, progress: ReaderPageProgress) {
        writeString(SettingsKeys.readerLastPage(photoId), encodeReaderProgress(progress))
    }
}

/** 阅读器：左右翻页、音量键提示是否已展示、末页追更静默截止时间、漫画阅读进度。 */
interface ReaderSettings : SettingsKeyStore, ReaderProgressSettings {

    suspend fun readerHorizontal(): Boolean = readBoolean(SettingsKeys.READER_HORIZONTAL) ?: false

    suspend fun setReaderHorizontal(enabled: Boolean) {
        writeBoolean(SettingsKeys.READER_HORIZONTAL, enabled)
    }

    /** 阅读页布局：默认单页，双页仅在宽屏横屏生效。 */
    suspend fun readerSpread(): ReaderSpreadMode =
        ReaderPreferences.normalizeSpreadMode(readString(SettingsKeys.READER_SPREAD))

    suspend fun setReaderSpread(mode: ReaderSpreadMode) {
        writeString(SettingsKeys.READER_SPREAD, mode.name)
    }

    suspend fun volumeKeyHintShown(): Boolean = readBoolean(SettingsKeys.VOLUME_KEY_HINT) ?: false

    suspend fun markVolumeKeyHintShown() {
        writeBoolean(SettingsKeys.VOLUME_KEY_HINT, true)
    }

    /** 音量键翻页；关闭时按键交还系统音量。 */
    suspend fun readerVolumeKeyPaging(): Boolean = readBoolean(SettingsKeys.READER_VOLUME_KEY_PAGING) ?: true

    suspend fun setReaderVolumeKeyPaging(enabled: Boolean) {
        writeBoolean(SettingsKeys.READER_VOLUME_KEY_PAGING, enabled)
    }

    /** 阅读时保持亮屏。 */
    suspend fun readerKeepScreenOn(): Boolean = readBoolean(SettingsKeys.READER_KEEP_SCREEN_ON) ?: true

    suspend fun setReaderKeepScreenOn(enabled: Boolean) {
        writeBoolean(SettingsKeys.READER_KEEP_SCREEN_ON, enabled)
    }

    /** 阅读亮度：-1 跟随系统，1..100 为窗口亮度。 */
    suspend fun readerBrightness(): Int =
        ReaderPreferences.normalizeBrightness(readInt(SettingsKeys.READER_BRIGHTNESS))

    suspend fun setReaderBrightness(value: Int) {
        writeInt(SettingsKeys.READER_BRIGHTNESS, ReaderPreferences.normalizeBrightness(value))
    }

    /** 图片质量档：省流/标准/高清。 */
    suspend fun readerImageQuality(): ReaderImageQuality =
        ReaderPreferences.normalizeImageQuality(readString(SettingsKeys.READER_IMAGE_QUALITY))

    suspend fun setReaderImageQuality(quality: ReaderImageQuality) {
        writeString(SettingsKeys.READER_IMAGE_QUALITY, quality.name)
    }

    /** 当前页之后预取的页数。 */
    suspend fun readerPrefetchPages(): Int =
        ReaderPreferences.normalizePrefetchPages(readInt(SettingsKeys.READER_PREFETCH_PAGES))

    suspend fun setReaderPrefetchPages(pages: Int) {
        writeInt(SettingsKeys.READER_PREFETCH_PAGES, ReaderPreferences.normalizePrefetchPages(pages))
    }

    /** 末页追更引导的静默截止时间（epoch 毫秒，0 表示未静默）。 */
    suspend fun trackPromptMuteUntil(): Long = readLong(SettingsKeys.TRACK_PROMPT_MUTE_UNTIL) ?: 0L

    suspend fun setTrackPromptMuteUntil(epochMillis: Long) {
        writeLong(SettingsKeys.TRACK_PROMPT_MUTE_UNTIL, epochMillis)
    }
}

/** 小说阅读偏好：字号、正文语言、按书进度。 */
interface NovelSettings : SettingsKeyStore {

    suspend fun novelFontSize(): Int =
        NovelPreferences.clampFontSize(readInt(SettingsKeys.NOVEL_FONT_SIZE) ?: NovelPreferences.DEFAULT_FONT_SIZE)

    suspend fun setNovelFontSize(sizeSp: Int) {
        writeInt(SettingsKeys.NOVEL_FONT_SIZE, NovelPreferences.clampFontSize(sizeSp))
    }

    /** tw（繁体）/ cn（简体）。 */
    suspend fun novelLang(): String =
        NovelPreferences.normalizeLang(readString(SettingsKeys.NOVEL_LANG) ?: NovelPreferences.DEFAULT_LANG)

    suspend fun setNovelLang(lang: String) {
        writeString(SettingsKeys.NOVEL_LANG, NovelPreferences.normalizeLang(lang))
    }

    /** 某部小说最后阅读的章节 id；未读过为 null。 */
    suspend fun novelLastChapter(novelId: String): String? =
        readString(SettingsKeys.novelLastChapter(novelId))

    suspend fun setNovelLastChapter(novelId: String, chapterId: String) {
        writeString(SettingsKeys.novelLastChapter(novelId), chapterId)
    }
}

/** 更新偏好：自动检查、忽略版本、已提示版本。 */
interface UpdateSettings : SettingsKeyStore {

    suspend fun autoCheckUpdate(): Boolean = readBoolean(SettingsKeys.AUTO_CHECK_UPDATE) ?: true

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        writeBoolean(SettingsKeys.AUTO_CHECK_UPDATE, enabled)
    }

    /** 用户主动忽略的版本号（不含 v 前缀）；null 表示未忽略任何版本。 */
    suspend fun ignoredUpdateVersion(): String? = VersionSettingText.normalize(readString(SettingsKeys.IGNORED_UPDATE_VERSION))

    suspend fun setIgnoredUpdateVersion(version: String?) {
        writeString(SettingsKeys.IGNORED_UPDATE_VERSION, VersionSettingText.normalize(version))
    }

    /** 启动期已弹过提示的版本号；同一版本在新会话内不重复打扰。 */
    suspend fun promptedUpdateVersion(): String? = VersionSettingText.normalize(readString(SettingsKeys.PROMPTED_UPDATE_VERSION))

    suspend fun setPromptedUpdateVersion(version: String?) {
        writeString(SettingsKeys.PROMPTED_UPDATE_VERSION, VersionSettingText.normalize(version))
    }
}

/** 通知权限一次性询问标记（Android 13+ 拒绝后不再打扰）。 */
interface NotificationPermissionSettings : SettingsKeyStore {

    suspend fun notificationPermissionAsked(): Boolean =
        readBoolean(SettingsKeys.NOTIFICATION_ASKED) ?: false

    suspend fun markNotificationPermissionAsked() {
        writeBoolean(SettingsKeys.NOTIFICATION_ASKED, true)
    }
}
