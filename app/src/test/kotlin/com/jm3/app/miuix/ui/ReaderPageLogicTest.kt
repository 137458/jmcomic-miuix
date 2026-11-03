package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
