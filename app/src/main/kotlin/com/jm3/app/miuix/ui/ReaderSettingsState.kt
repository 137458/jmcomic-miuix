// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode

/**
 * 阅读设置七项的状态与写盘，两套皮肤共用。
 *
 * 皮肤只决定用什么控件渲染，读盘、归一与写盘路径都在这里：切换先改本地值再写盘，
 * 重复提交同值直接返回，避免同一档位反复落盘；亮度拖动期间只改本地值，
 * 松手时才提交（与 Miuix 版 SliderPreference 的行为一致）。
 */
internal class ReaderSettingsState(private val settings: ReaderSettings) {

    var horizontal by mutableStateOf(false)
        private set

    var spread by mutableStateOf(ReaderSpreadMode.SINGLE)
        private set

    var volumeKeyPaging by mutableStateOf(true)
        private set

    var keepScreenOn by mutableStateOf(true)
        private set

    var brightness by mutableIntStateOf(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS)
        private set

    var imageQuality by mutableStateOf(ReaderImageQuality.STANDARD)
        private set

    var prefetchPages by mutableIntStateOf(ReaderPreferences.DEFAULT_PREFETCH_PAGES)
        private set

    /** 进页时读一次盘；之后的修改以本地值为准，不再回头覆盖。 */
    suspend fun load() {
        horizontal = settings.readerHorizontal()
        spread = settings.readerSpread()
        volumeKeyPaging = settings.readerVolumeKeyPaging()
        keepScreenOn = settings.readerKeepScreenOn()
        brightness = settings.readerBrightness()
        imageQuality = settings.readerImageQuality()
        prefetchPages = settings.readerPrefetchPages()
    }

    suspend fun applyHorizontal(enabled: Boolean) {
        if (enabled == horizontal) return
        horizontal = enabled
        settings.setReaderHorizontal(enabled)
    }

    suspend fun applySpread(mode: ReaderSpreadMode) {
        if (mode == spread) return
        spread = mode
        settings.setReaderSpread(mode)
    }

    suspend fun applyVolumeKeyPaging(enabled: Boolean) {
        if (enabled == volumeKeyPaging) return
        volumeKeyPaging = enabled
        settings.setReaderVolumeKeyPaging(enabled)
    }

    suspend fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled == keepScreenOn) return
        keepScreenOn = enabled
        settings.setReaderKeepScreenOn(enabled)
    }

    suspend fun applyImageQuality(quality: ReaderImageQuality) {
        if (quality == imageQuality) return
        imageQuality = quality
        settings.setReaderImageQuality(quality)
    }

    suspend fun applyPrefetchPages(pages: Int) {
        if (pages == prefetchPages) return
        prefetchPages = pages
        settings.setReaderPrefetchPages(pages)
    }

    /** 拖动中的亮度只改本地值，等待 [commitBrightness] 落盘。 */
    fun previewBrightness(raw: Float) {
        brightness = readerBrightnessFromSlider(raw)
    }

    suspend fun commitBrightness() {
        settings.setReaderBrightness(brightness)
    }
}

/** 建立并读一次阅读设置状态；仓库实例变化时重建。 */
@Composable
internal fun rememberReaderSettingsState(settings: ReaderSettings): ReaderSettingsState {
    val state = remember(settings) { ReaderSettingsState(settings) }
    LaunchedEffect(state) { state.load() }
    return state
}
