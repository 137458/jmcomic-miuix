// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
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
 * 阅读设置七项的 Miuix 渲染：读盘、判等与写盘都在 [ReaderSettingsState]，
 * 本组件只负责控件与文案；COUI 皮肤用库控件渲染同一份状态，两侧不再各自实现业务。
 *
 * 双页只改排布，不再联动「默认阅读方向」：选择双页不会替用户改写左右翻页设置；
 * 「双页阅读」的说明文案保留「按左右翻页生效」，如实告知两者叠加关系。
 */
@Composable
internal fun ReaderSettingsSection(
    state: ReaderSettingsState,
    spreadAllowed: Boolean,
) {
    val scope = rememberCoroutineScope()

    OverlaySpinnerPreference(
        title = "默认阅读方向",
        summary = if (state.horizontal) "新打开的章节按左右翻页" else "新打开的章节纵向连续滚动",
        items = listOf("纵向长图", "左右翻页").mapIndexed { index, label ->
            DropdownItem(text = label, selected = (index == 1) == state.horizontal)
        },
        selectedIndex = if (state.horizontal) 1 else 0,
        showValue = false,
        onSelectedIndexChange = { index -> scope.launch { state.applyHorizontal(index == 1) } },
    )
    // 双页是大屏横屏的附加排布：窗口不够宽或竖屏时整行不渲染，避免给手机增加噪音
    if (spreadAllowed) {
        OverlaySpinnerPreference(
            title = "双页阅读",
            summary = if (state.spread == ReaderSpreadMode.DOUBLE) {
                "大屏横屏下两页并排，按左右翻页生效"
            } else {
                "单页显示，可按左右翻页"
            },
            items = ReaderSpreadMode.entries.map { mode ->
                DropdownItem(text = mode.label, selected = mode == state.spread)
            },
            selectedIndex = ReaderSpreadMode.entries.indexOf(state.spread),
            showValue = false,
            onSelectedIndexChange = { index ->
                ReaderSpreadMode.entries.getOrNull(index)?.let { mode ->
                    scope.launch { state.applySpread(mode) }
                }
            },
        )
    }
    SwitchPreference(
        title = "音量键翻页",
        summary = "关闭后音量键恢复调节系统音量",
        checked = state.volumeKeyPaging,
        onCheckedChange = { enabled -> scope.launch { state.applyVolumeKeyPaging(enabled) } },
    )
    SwitchPreference(
        title = "阅读时保持亮屏",
        summary = "阅读页不自动息屏",
        checked = state.keepScreenOn,
        onCheckedChange = { enabled -> scope.launch { state.applyKeepScreenOn(enabled) } },
    )
    SliderPreference(
        value = readerBrightnessSliderValue(state.brightness),
        onValueChange = state::previewBrightness,
        title = "阅读亮度",
        summary = "0 表示跟随系统亮度，只在阅读页生效",
        valueText = readerBrightnessLabel(state.brightness),
        valueRange = 0f..ReaderPreferences.MAX_BRIGHTNESS.toFloat(),
        onValueChangeFinished = { scope.launch { state.commitBrightness() } },
    )
    OverlaySpinnerPreference(
        title = "图片质量",
        summary = state.imageQuality.settingsSummary(),
        items = ReaderImageQuality.entries.map { quality ->
            DropdownItem(text = quality.label, selected = quality == state.imageQuality)
        },
        selectedIndex = ReaderImageQuality.entries.indexOf(state.imageQuality),
        showValue = false,
        onSelectedIndexChange = { index ->
            ReaderImageQuality.entries.getOrNull(index)?.let { quality ->
                scope.launch { state.applyImageQuality(quality) }
            }
        },
    )
    OverlaySpinnerPreference(
        title = "预取页数",
        summary = if (state.prefetchPages == 0) {
            "不提前加载后续页"
        } else {
            "提前加载当前页之后的 ${state.prefetchPages} 页"
        },
        items = ReaderPreferences.PREFETCH_OPTIONS.map { pages ->
            DropdownItem(text = prefetchPagesLabel(pages), selected = pages == state.prefetchPages)
        },
        selectedIndex = ReaderPreferences.PREFETCH_OPTIONS.indexOf(state.prefetchPages),
        showValue = false,
        onSelectedIndexChange = { index ->
            ReaderPreferences.PREFETCH_OPTIONS.getOrNull(index)?.let { pages ->
                scope.launch { state.applyPrefetchPages(pages) }
            }
        },
    )
}
