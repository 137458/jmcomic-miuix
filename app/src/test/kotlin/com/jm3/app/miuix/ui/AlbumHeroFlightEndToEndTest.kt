package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumHeroFlightEndToEndTest {

    private val coverRect = HeroRect(left = 100f, top = 200f, width = 60f, height = 80f)
    private val headerRect = HeroRect(left = 20f, top = 50f, width = 120f, height = 160f)

    @Test
    fun testLerpHeroRectInterpolationAndClamping() {
        // t = 0f
        val at0 = lerpHeroRect(coverRect, headerRect, 0f)
        assertEquals(coverRect.left, at0.left, 0.001f)
        assertEquals(coverRect.top, at0.top, 0.001f)
        assertEquals(coverRect.width, at0.width, 0.001f)
        assertEquals(coverRect.height, at0.height, 0.001f)

        // t = 1f
        val at1 = lerpHeroRect(coverRect, headerRect, 1f)
        assertEquals(headerRect.left, at1.left, 0.001f)
        assertEquals(headerRect.top, at1.top, 0.001f)
        assertEquals(headerRect.width, at1.width, 0.001f)
        assertEquals(headerRect.height, at1.height, 0.001f)

        // t = 0.5f
        val atHalf = lerpHeroRect(coverRect, headerRect, 0.5f)
        assertEquals(60f, atHalf.left, 0.001f)
        assertEquals(125f, atHalf.top, 0.001f)
        assertEquals(90f, atHalf.width, 0.001f)
        assertEquals(120f, atHalf.height, 0.001f)

        // 越界保护: t = -0.5f 钳制为 0f
        val atNeg = lerpHeroRect(coverRect, headerRect, -0.5f)
        assertEquals(coverRect.left, atNeg.left, 0.001f)

        // 越界保护: t = 1.5f 钳制为 1f
        val atOver = lerpHeroRect(coverRect, headerRect, 1.5f)
        assertEquals(headerRect.left, atOver.left, 0.001f)
    }

    @Test
    fun testLerpHeroCornerInterpolationAndClamping() {
        // 从 6dp 到 16dp
        assertEquals(6f, lerpHeroCorner(6f, 16f, 0f), 0.001f)
        assertEquals(16f, lerpHeroCorner(6f, 16f, 1f), 0.001f)
        assertEquals(11f, lerpHeroCorner(6f, 16f, 0.5f), 0.001f)
        assertEquals(6f, lerpHeroCorner(6f, 16f, -0.2f), 0.001f)
        assertEquals(16f, lerpHeroCorner(6f, 16f, 1.8f), 0.001f)
    }

    @Test
    fun testShouldRunHeroTravelThresholdAndFiniteCheck() {
        // 正常位移充足
        assertTrue(shouldRunHero(coverRect, headerRect))

        // 小于 2px 位移
        val almostSame = coverRect.copy(left = coverRect.left + 0.5f, top = coverRect.top + 0.5f)
        assertFalse(shouldRunHero(coverRect, almostSame))

        // 宽或高为 0
        val zeroWidth = coverRect.copy(width = 0f)
        assertFalse(shouldRunHero(coverRect, zeroWidth))

        // NaN 或 Infinity
        val nanRect = coverRect.copy(left = Float.NaN)
        assertFalse(shouldRunHero(coverRect, nanRect))
    }

    @Test
    fun testEndToEndForwardAndBackwardFlightCycle() {
        val host = AlbumHeroHost()
        val albumId = "album_123"
        val coverUrl = "https://example.com/cover.jpg"

        // 1. 列表封面登记
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)

        // 2. 点击列表项触发正向飞行准备
        val started = host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        assertTrue(started)
        assertNotNull(host.pending)
        assertEquals(albumId, host.pending?.albumId)
        assertNull(host.flight)
        // 起飞前封面不隐藏
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        // 3. 详情头图登记并起飞
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        val resolved = host.resolvePending()
        assertTrue(resolved)
        assertNull(host.pending)
        assertNotNull(host.flight)
        assertEquals(coverRect, host.flight?.from)
        assertEquals(headerRect, host.flight?.to)
        // 飞行期间封面与头图均隐藏
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))

        // 4. 正向飞行结束落位
        host.finishFlight(host.flight!!.id)
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        // 5. 返回时触发反向飞行
        val backwardStarted = host.startBackward(albumId)
        assertTrue(backwardStarted)
        assertNotNull(host.flight)
        // 反向飞行起止矩形对调
        assertEquals(headerRect, host.flight?.from)
        assertEquals(coverRect, host.flight?.to)
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))

        // 6. 反向飞行落位结束
        host.finishFlight(host.flight!!.id)
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }
}
