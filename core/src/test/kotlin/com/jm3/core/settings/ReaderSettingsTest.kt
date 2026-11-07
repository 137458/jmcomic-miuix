package com.jm3.core.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阅读器设置的默认值与归一规则：默认值只在没写过键时生效，越界输入按固定档位收敛。
 */
class ReaderSettingsTest {

    private fun repo() = InMemorySettingsRepository()

    @Test
    fun 未写入时使用默认值() = runTest {
        val r = repo()
        assertEquals(false, r.readerHorizontal())
        assertEquals(true, r.readerVolumeKeyPaging())
        assertEquals(true, r.readerKeepScreenOn())
        assertEquals(-1, r.readerBrightness())
        assertEquals(ReaderImageQuality.STANDARD, r.readerImageQuality())
        assertEquals(3, r.readerPrefetchPages())
    }

    @Test
    fun 音量键翻页可关闭并读回() = runTest {
        val r = repo()
        r.setReaderVolumeKeyPaging(false)
        assertEquals(false, r.readerVolumeKeyPaging())
        r.setReaderVolumeKeyPaging(true)
        assertEquals(true, r.readerVolumeKeyPaging())
    }

    @Test
    fun 保持亮屏可关闭并读回() = runTest {
        val r = repo()
        r.setReaderKeepScreenOn(false)
        assertEquals(false, r.readerKeepScreenOn())
        r.setReaderKeepScreenOn(true)
        assertEquals(true, r.readerKeepScreenOn())
    }

    @Test
    fun 亮度跟随系统用负一表示() = runTest {
        val r = repo()
        r.setReaderBrightness(-1)
        assertEquals(-1, r.readerBrightness())
        // 0 不是合法亮度，归到跟随系统而不是最暗
        r.setReaderBrightness(0)
        assertEquals(-1, r.readerBrightness())
        r.setReaderBrightness(-9)
        assertEquals(-1, r.readerBrightness())
    }

    @Test
    fun 亮度越界夹取到可用区间() = runTest {
        val r = repo()
        r.setReaderBrightness(42)
        assertEquals(42, r.readerBrightness())
        r.setReaderBrightness(150)
        assertEquals(100, r.readerBrightness())
    }

    @Test
    fun 预取页数只接受预设档位() = runTest {
        val r = repo()
        r.setReaderPrefetchPages(0)
        assertEquals(0, r.readerPrefetchPages())
        r.setReaderPrefetchPages(2)
        assertEquals(2, r.readerPrefetchPages())
        r.setReaderPrefetchPages(5)
        assertEquals(5, r.readerPrefetchPages())
        // 非预设档位回退默认值
        r.setReaderPrefetchPages(4)
        assertEquals(3, r.readerPrefetchPages())
    }

    @Test
    fun 图片质量读回且损坏值回退标准() = runTest {
        val r = repo()
        r.setReaderImageQuality(ReaderImageQuality.HIGH)
        assertEquals(ReaderImageQuality.HIGH, r.readerImageQuality())
        r.writeString(SettingsKeys.READER_IMAGE_QUALITY, "BROKEN")
        assertEquals(ReaderImageQuality.STANDARD, r.readerImageQuality())
    }

    @Test
    fun 图片质量三档映射解码预算() {
        assertEquals(2_000_000, ReaderImageQuality.ECONOMY.maxDecodePixels)
        assertEquals(4_000_000, ReaderImageQuality.STANDARD.maxDecodePixels)
        assertEquals(6_000_000, ReaderImageQuality.HIGH.maxDecodePixels)
    }
}
