package com.jm3.app.miuix.ui

import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsSectionTest {

    @Test
    fun `亮度滑杆贴左归为跟随系统`() {
        assertEquals(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS, readerBrightnessFromSlider(0f))
        assertEquals(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS, readerBrightnessFromSlider(-5f))
    }

    @Test
    fun `亮度滑杆回写四舍五入并夹在可用区间`() {
        assertEquals(43, readerBrightnessFromSlider(42.6f))
        assertEquals(1, readerBrightnessFromSlider(0.4f))
        assertEquals(100, readerBrightnessFromSlider(100f))
        assertEquals(ReaderPreferences.MAX_BRIGHTNESS, readerBrightnessFromSlider(150f))
    }

    @Test
    fun `亮度展示跟随系统时不显示百分比`() {
        assertEquals("跟随系统", readerBrightnessLabel(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS))
        assertEquals("80%", readerBrightnessLabel(80))
        assertEquals(0f, readerBrightnessSliderValue(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS))
        assertEquals(80f, readerBrightnessSliderValue(80))
    }

    @Test
    fun `预取页数零档文案为关闭`() {
        assertEquals("关闭", prefetchPagesLabel(0))
        assertEquals("3 页", prefetchPagesLabel(3))
        assertEquals("5 页", prefetchPagesLabel(5))
    }

    @Test
    fun `图片质量说明按档位给出解码预算`() {
        val cases = listOf(
            ReaderImageQuality.ECONOMY to "解码预算 2MP，最省流量与内存",
            ReaderImageQuality.STANDARD to "解码预算 4MP，清晰度与内存平衡",
            ReaderImageQuality.HIGH to "解码预算 6MP，优先清晰度",
        )
        for ((quality, expected) in cases) assertEquals(expected, quality.settingsSummary())
    }
}
