// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 预取页数选项文案：0 表示不预取。 */
internal fun prefetchPagesLabel(pages: Int): String = if (pages == 0) "关闭" else "$pages 页"

/** 图片质量档的解码预算说明。 */
internal fun ReaderImageQuality.settingsSummary(): String = when (this) {
    ReaderImageQuality.ECONOMY -> "解码预算 2MP，最省流量与内存"
    ReaderImageQuality.STANDARD -> "解码预算 4MP，清晰度与内存平衡"
    ReaderImageQuality.HIGH -> "解码预算 6MP，优先清晰度"
}

/** 亮度滑杆位置：跟随系统固定在 0，其余按百分比。 */
internal fun readerBrightnessSliderValue(brightness: Int): Float =
    if (brightness == ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) 0f else brightness.toFloat()

/** 滑杆回写亮度：贴左即跟随系统，否则四舍五入并夹到 [ReaderPreferences.MIN_BRIGHTNESS]..[ReaderPreferences.MAX_BRIGHTNESS]。 */
internal fun readerBrightnessFromSlider(raw: Float): Int =
    if (raw <= 0f) {
        ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS
    } else {
        raw.roundToInt().coerceIn(ReaderPreferences.MIN_BRIGHTNESS, ReaderPreferences.MAX_BRIGHTNESS)
    }

/** 亮度展示文案：跟随系统时不显示百分比。 */
internal fun readerBrightnessLabel(brightness: Int): String =
    if (brightness == ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) "跟随系统" else "$brightness%"

/**
 * 阅读设置七项：设置中心与 COUI 设置页共用同一份实现、文案与写盘路径，
 * 两个页面只负责外层分组与排版，避免逐行复制后两侧行为悄悄分叉。
 *
 * 双页只改排布，不再联动「默认阅读方向」：选择双页不会替用户改写左右翻页设置；
 * 「双页阅读」的说明文案保留「按左右翻页生效」，如实告知两者叠加关系。
 */
@Composable
internal fun ReaderSettingsSection(
    repository: ReaderSettings,
    spreadAllowed: Boolean,
) {
    val scope = rememberCoroutineScope()
    var horizontal by remember { mutableStateOf(false) }
    var spread by remember { mutableStateOf(ReaderSpreadMode.SINGLE) }
    var volumeKeyPaging by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var brightness by remember { mutableIntStateOf(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) }
    var imageQuality by remember { mutableStateOf(ReaderImageQuality.STANDARD) }
    var prefetchPages by remember { mutableIntStateOf(ReaderPreferences.DEFAULT_PREFETCH_PAGES) }

    // 只在进入设置页时读一次：页面内的修改直接以本地状态为准并写盘
    LaunchedEffect(Unit) {
        horizontal = repository.readerHorizontal()
        spread = repository.readerSpread()
        volumeKeyPaging = repository.readerVolumeKeyPaging()
        keepScreenOn = repository.readerKeepScreenOn()
        brightness = repository.readerBrightness()
        imageQuality = repository.readerImageQuality()
        prefetchPages = repository.readerPrefetchPages()
    }

    OverlaySpinnerPreference(
        title = "默认阅读方向",
        summary = if (horizontal) "新打开的章节按左右翻页" else "新打开的章节纵向连续滚动",
        items = listOf("纵向长图", "左右翻页").mapIndexed { index, label ->
            DropdownItem(text = label, selected = (index == 1) == horizontal)
        },
        selectedIndex = if (horizontal) 1 else 0,
        showValue = false,
        onSelectedIndexChange = { index ->
            val next = index == 1
            if (next != horizontal) {
                horizontal = next
                scope.launch { repository.setReaderHorizontal(next) }
            }
        },
    )
    // 双页是大屏横屏的附加排布：窗口不够宽或竖屏时整行不渲染，避免给手机增加噪音
    if (spreadAllowed) {
        OverlaySpinnerPreference(
            title = "双页阅读",
            summary = if (spread == ReaderSpreadMode.DOUBLE) {
                "大屏横屏下两页并排，按左右翻页生效"
            } else {
                "单页显示，可按左右翻页"
            },
            items = ReaderSpreadMode.entries.map { mode ->
                DropdownItem(text = mode.label, selected = mode == spread)
            },
            selectedIndex = ReaderSpreadMode.entries.indexOf(spread),
            showValue = false,
            onSelectedIndexChange = { index ->
                ReaderSpreadMode.entries.getOrNull(index)?.let { mode ->
                    if (mode != spread) {
                        spread = mode
                        scope.launch { repository.setReaderSpread(mode) }
                    }
                }
            },
        )
    }
    SwitchPreference(
        title = "音量键翻页",
        summary = "关闭后音量键恢复调节系统音量",
        checked = volumeKeyPaging,
        onCheckedChange = { enabled ->
            volumeKeyPaging = enabled
            scope.launch { repository.setReaderVolumeKeyPaging(enabled) }
        },
    )
    SwitchPreference(
        title = "阅读时保持亮屏",
        summary = "阅读页不自动息屏",
        checked = keepScreenOn,
        onCheckedChange = { enabled ->
            keepScreenOn = enabled
            scope.launch { repository.setReaderKeepScreenOn(enabled) }
        },
    )
    SliderPreference(
        value = readerBrightnessSliderValue(brightness),
        onValueChange = { raw -> brightness = readerBrightnessFromSlider(raw) },
        title = "阅读亮度",
        summary = "0 表示跟随系统亮度，只在阅读页生效",
        valueText = readerBrightnessLabel(brightness),
        valueRange = 0f..ReaderPreferences.MAX_BRIGHTNESS.toFloat(),
        onValueChangeFinished = {
            scope.launch { repository.setReaderBrightness(brightness) }
        },
    )
    OverlaySpinnerPreference(
        title = "图片质量",
        summary = imageQuality.settingsSummary(),
        items = ReaderImageQuality.entries.map { quality ->
            DropdownItem(text = quality.label, selected = quality == imageQuality)
        },
        selectedIndex = ReaderImageQuality.entries.indexOf(imageQuality),
        showValue = false,
        onSelectedIndexChange = { index ->
            ReaderImageQuality.entries.getOrNull(index)?.let { quality ->
                if (quality != imageQuality) {
                    imageQuality = quality
                    scope.launch { repository.setReaderImageQuality(quality) }
                }
            }
        },
    )
    OverlaySpinnerPreference(
        title = "预取页数",
        summary = if (prefetchPages == 0) {
            "不提前加载后续页"
        } else {
            "提前加载当前页之后的 $prefetchPages 页"
        },
        items = ReaderPreferences.PREFETCH_OPTIONS.map { pages ->
            DropdownItem(text = prefetchPagesLabel(pages), selected = pages == prefetchPages)
        },
        selectedIndex = ReaderPreferences.PREFETCH_OPTIONS.indexOf(prefetchPages),
        showValue = false,
        onSelectedIndexChange = { index ->
            ReaderPreferences.PREFETCH_OPTIONS.getOrNull(index)?.let { pages ->
                if (pages != prefetchPages) {
                    prefetchPages = pages
                    scope.launch { repository.setReaderPrefetchPages(pages) }
                }
            }
        },
    )
}
