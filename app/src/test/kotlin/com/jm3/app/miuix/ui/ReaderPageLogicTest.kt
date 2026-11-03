package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageLogicTest {

    @Test
    fun 宽高比按解码后的宽除以高算() {
        assertEquals(0.75f, pageAspectRatio(1080f, 1440f))
        // 800x20000 的长条图：真实比例约 1:25，长图模式必须按它解码
        assertEquals(0.04f, pageAspectRatio(800f, 20000f))
    }

    @Test
    fun 宽或高为零时没有比例() {
        assertNull(pageAspectRatio(0f, 1440f))
        assertNull(pageAspectRatio(1080f, 0f))
        assertNull(pageAspectRatio(0f, 0f))
    }

    @Test
    fun 负的宽高不算比例() {
        assertNull(pageAspectRatio(-1080f, 1440f))
        assertNull(pageAspectRatio(1080f, -1440f))
    }

    @Test
    fun NaN与无穷大不算比例() {
        assertNull(pageAspectRatio(Float.NaN, 1440f))
        assertNull(pageAspectRatio(1080f, Float.NaN))
        assertNull(pageAspectRatio(Float.POSITIVE_INFINITY, 1440f))
        assertNull(pageAspectRatio(1080f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun 比例未知时按三比四兜底() {
        // 写死 1080x1440：比例未知时解码尺寸必须与旧的 3:4 占位盒一致，不能悄悄变大变小
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, null))
    }

    @Test
    fun 三比四的页解码尺寸与兜底一致() {
        // 普通竖版页解出真实比例后应算出同一个尺寸，否则每页都会重解码一次
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, 0.75f))
    }

    @Test
    fun 长条图按真实比例解码铺满页宽() {
        // 1:2 的页：宽度铺满页宽，高度按比例撑开，不再压进 3:4 的框
        assertEquals(IntSize(1080, 2160), readerPageDecodeSize(1080, 0.5f))
    }

    @Test
    fun 宽图解码高度按比例收窄() {
        // 2:1 的横图：宽度仍是页宽，高度只有一半
        assertEquals(IntSize(1080, 540), readerPageDecodeSize(1080, 2f))
    }

    @Test
    fun 超长条幅按单边上限与像素预算收敛() {
        // 800x20000 的长条图按 1080 页宽原比例解码要 1080x27000（约 116 MB），
        // 收敛后是 328x8192（约 10.7 MB），既守住 16 MB 上界又比写死 3:4 清晰得多
        val size = readerPageDecodeSize(1080, 800f / 20000f)
        assertEquals(IntSize(328, 8192), size)
        assertTrue(size.width.toLong() * size.height <= 4_000_000L)
    }

    @Test
    fun 极窄的页只受单边上限约束() {
        // 页宽 480、比例 1:100：高度先被单边上限截断到 8192，面积仍在上界内，宽度等比缩到 82
        val size = readerPageDecodeSize(480, 0.01f)
        assertEquals(IntSize(82, 8192), size)
        assertTrue(size.width.toLong() * size.height <= 4_000_000L)
    }

    @Test
    fun 页宽未知时至少解码一像素() {
        assertEquals(IntSize(1, 1), readerPageDecodeSize(0, null))
        assertEquals(IntSize(1, 2), readerPageDecodeSize(0, 0.5f))
    }

    @Test
    fun 非法比例退回三比四兜底() {
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, 0f))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, -0.5f))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, Float.NaN))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, Float.POSITIVE_INFINITY))
    }

    @Test
    fun 本页已知比例优先于章节种子() {
        assertEquals(0.5f, readerPagePlaceholderRatio(0.5f, 0.75f))
    }

    @Test
    fun 本页比例未知时借用章节种子() {
        // 同一话各页比例基本一致，借用前一页的比例可以少跳一次
        assertEquals(0.04f, readerPagePlaceholderRatio(null, 0.04f))
    }

    @Test
    fun 都未知时用三比四占位() {
        assertEquals(0.75f, readerPagePlaceholderRatio(null, null))
    }

    @Test
    fun 非法比例不参与占位() {
        assertEquals(0.04f, readerPagePlaceholderRatio(0f, 0.04f))
        assertEquals(0.75f, readerPagePlaceholderRatio(Float.NaN, null))
    }

    @Test
    fun 条幅不参与章节种子() {
        // 1:25 的条幅若当章节种子，后续未解码页的占位框会被撑到上万 px，加载完成后又塌回来
        assertNull(chapterSeedCandidate(0.04f))
    }

    @Test
    fun 章节种子下限取到一比二() {
        // 边界值本身可以当种子，更窄的长条交给本页自己的比例
        assertEquals(0.5f, chapterSeedCandidate(0.5f))
        assertEquals(0.75f, chapterSeedCandidate(0.75f))
    }

    @Test
    fun 非法比例不能当章节种子() {
        assertNull(chapterSeedCandidate(0f))
        assertNull(chapterSeedCandidate(Float.NaN))
        assertNull(chapterSeedCandidate(Float.POSITIVE_INFINITY))
    }

    @Test
    fun 请求尺寸只跟随解码比例() {
        // 布局比例已是 3:4，请求尺寸仍按解码比例 1:2 算：两份状态各管一段
        val frame = readerPageFrame(1080, knownRatio = 0.75f, chapterSeed = null, decodeRatio = 0.5f)
        assertEquals(0.75f, frame.layoutRatio)
        assertEquals(IntSize(1080, 2160), frame.decodeSize)
    }

    @Test
    fun 解码比例未知时请求按三比四兜底() {
        val frame = readerPageFrame(1080, knownRatio = 0.5f, chapterSeed = null, decodeRatio = null)
        assertEquals(0.5f, frame.layoutRatio)
        assertEquals(IntSize(1080, 1440), frame.decodeSize)
    }

    @Test
    fun 布局比例变化不改变请求尺寸() {
        // 回填真实比例只重排布局；请求尺寸跟着变会让 coil3 判缓存失效，每页都重解一次
        val unknown = readerPageFrame(1080, knownRatio = null, chapterSeed = 0.75f, decodeRatio = null)
        val detected = readerPageFrame(1080, knownRatio = 0.5f, chapterSeed = 0.5f, decodeRatio = null)
        assertEquals(0.75f, unknown.layoutRatio)
        assertEquals(0.5f, detected.layoutRatio)
        assertEquals(unknown.decodeSize, detected.decodeSize)
    }

    @Test
    fun 顶满请求框才算受框限制() {
        // 源码 800x1200 小于 1080x1440 的请求框：两维都没顶满，重算只会再解一次同一张图
        assertFalse(isBoxLimited(1080, 1440, 800f, 1200f))
        // 解出 77x1440：高度顶满，按真实比例重算才能换到 328x8192
        assertTrue(isBoxLimited(1080, 1440, 77f, 1440f))
        // 宽度顶满同样算受框限制
        assertTrue(isBoxLimited(1080, 1440, 1080f, 540f))
    }

    @Test
    fun 各比例收敛后仍保持等比() {
        // 离散点用例可能同时通过，等比这条不变式才能锁住钳位链路
        for (ratio in listOf(0.25f, 0.5f, 0.667f, 0.75f, 1f, 1.5f, 2f)) {
            val size = readerPageDecodeSize(1080, ratio)
            val actual = size.width.toFloat() / size.height.toFloat()
            assertTrue(abs(actual - ratio) / ratio < 0.01f, "比例 $ratio 收敛后为 $actual")
        }
    }

    @Test
    fun 面积上限边界() {
        // 正好 4 MP 不缩放
        assertEquals(IntSize(1000, 4000), readerPageDecodeSize(1000, 0.25f))
        // 超过 4 MP 按面积缩回预算内，取整不能把面积又顶出去
        val shrunk = readerPageDecodeSize(1000, 0.24f)
        assertEquals(IntSize(979, 4082), shrunk)
        assertTrue(shrunk.width.toLong() * shrunk.height <= MAX_PAGE_DECODE_PIXELS)
    }

    @Test
    fun 极端比例不崩且至少一像素() {
        // 宽 / 极小比例会溢出到 Int.MAX，两维仍须不小于 1：coil3 的 Dimension.Pixels 要求 px > 0
        assertEquals(IntSize(1, 8192), readerPageDecodeSize(1080, 1e-30f))
    }

    @Test
    fun 超大页宽也不越预算() {
        // 单边上限先于面积预算生效，页宽再大也要落回预算内
        val size = readerPageDecodeSize(Int.MAX_VALUE, 0.75f)
        assertTrue(size.width.toLong() * size.height <= MAX_PAGE_DECODE_PIXELS)
    }

    @Test
    fun 内存预算估算RGB565相比ARGB8888节省一半内存() {
        val size = IntSize(1080, 1440)
        val argbBytes = estimatePageMemoryBytes(size, isRgb565 = false)
        val rgb565Bytes = estimatePageMemoryBytes(size, isRgb565 = true)
        assertEquals(1080L * 1440L * 4L, argbBytes)
        assertEquals(1080L * 1440L * 2L, rgb565Bytes)
        assertEquals(argbBytes / 2, rgb565Bytes)
    }

    @Test
    fun 单页四百万像素上限在RGB565下守住八兆字节上界() {
        val maxPixels = 4_000_000
        val size = IntSize(2000, 2000)
        val bytes = estimatePageMemoryBytes(size, isRgb565 = true)
        assertEquals(8_000_000L, bytes)
        assertTrue(bytes <= 8_000_000L)
    }
}

