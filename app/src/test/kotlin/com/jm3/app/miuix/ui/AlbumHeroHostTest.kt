package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumHeroHostTest {

    private val coverRect = HeroRect(left = 100f, top = 200f, width = 60f, height = 80f)
    private val headerRect = HeroRect(left = 20f, top = 50f, width = 120f, height = 160f)
    private val albumId = "album_123"
    private val coverUrl = "https://example.com/cover.jpg"

    @Test
    fun albumCoverMemoryKey正常提取并忽略空白() {
        val key = albumCoverMemoryKey("https://example.com/cover.jpg")
        assertNotNull(key)
        assertEquals("https://example.com/cover.jpg", key.key)

        assertNull(albumCoverMemoryKey(null))
        assertNull(albumCoverMemoryKey(""))
        assertNull(albumCoverMemoryKey("   "))
    }

    @Test
    fun 封面登记与注销正确维护状态() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        assertEquals(coverRect, host.coverRect(albumId))

        // 相同参数重复登记应防抖安全跳过
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        assertEquals(coverRect, host.coverRect(albumId))

        // 注销封面
        host.unregisterCover(albumId, coverRect)
        assertNull(host.coverRect(albumId))
    }

    @Test
    fun 头图登记记录最新矩形与历史快照() {
        val host = AlbumHeroHost()
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        assertEquals(headerRect, host.headerRect(albumId))

        // 相同参数重复登记防抖
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        assertEquals(headerRect, host.headerRect(albumId))

        // 注销当前头图，当前矩形清空
        host.unregisterHeader(albumId, headerRect)
        assertNull(host.headerRect(albumId))
    }

    @Test
    fun 正向飞行准备阶段登记并记录待飞() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)

        val started = host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        assertTrue(started)
        val pending = host.pending
        assertNotNull(pending)
        assertEquals(albumId, pending.albumId)
        assertEquals(coverUrl, pending.url)
        assertEquals(coverRect, pending.from)
        assertEquals(HERO_SEARCH_ROW_CORNER_DP, pending.cornerFromDp)

        // 起飞前飞行计划为空，封面与头图均不隐藏
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 封面矩形无效或未登记时不触发正向待飞() {
        val host = AlbumHeroHost()
        // 未登记封面直接启动正向飞行
        assertFalse(host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP))
        assertNull(host.pending)

        // 登记非法宽高矩形
        val zeroRect = coverRect.copy(width = 0f)
        host.registerCover(albumId, coverUrl, zeroRect, HERO_SEARCH_ROW_CORNER_DP)
        assertFalse(host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP))
        assertNull(host.pending)
    }

    @Test
    fun 详情头图测量完成后正向起飞并锁定两端隐藏与圆角契约() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)

        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        val resolved = host.resolvePending()
        assertTrue(resolved)

        // pending 必须清空
        assertNull(host.pending)

        // flight 属性闭环校验
        val flight = host.flight
        assertNotNull(flight)
        assertEquals(coverRect, flight.from)
        assertEquals(headerRect, flight.to)
        assertEquals(HERO_SEARCH_ROW_CORNER_DP, flight.cornerFromDp)
        assertEquals(HERO_CARD_CORNER_DP, flight.cornerToDp)
        assertEquals(coverUrl, flight.url)

        // 飞行期间两端均隐藏避让
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))
    }

    @Test
    fun 正向飞行结束落位后两端恢复可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()

        val flightId = host.flight!!.id
        host.finishFlight(flightId)

        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 待飞超时取消后待飞记录清空且两端保持可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        assertNotNull(host.pending)

        host.cancelPending()
        assertNull(host.pending)
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 退栈时触发反向飞行且起止矩形圆角对调() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.finishFlight(host.flight!!.id)

        val backwardStarted = host.startBackward(albumId)
        assertTrue(backwardStarted)

        val flight = host.flight
        assertNotNull(flight)
        // 反向起止端点与圆角严格对调
        assertEquals(headerRect, flight.from)
        assertEquals(coverRect, flight.to)
        assertEquals(HERO_CARD_CORNER_DP, flight.cornerFromDp)
        assertEquals(HERO_SEARCH_ROW_CORNER_DP, flight.cornerToDp)
        assertEquals(coverUrl, flight.url)

        // 反向飞行期间两端再次隐藏
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))
    }

    @Test
    fun 反向飞行结束落位后完整复原状态() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.finishFlight(host.flight!!.id)

        host.startBackward(albumId)
        host.finishFlight(host.flight!!.id)

        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 非列表卡片进入的相册退栈时不触发反向飞行() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)

        // 未调用 startForward，不在 heroOrigins 集合内
        val backwardStarted = host.startBackward(albumId)
        assertFalse(backwardStarted)
        assertNull(host.flight)
    }

    @Test
    fun 头图注销后退栈能降级使用历史测量快照触发反向飞行() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.finishFlight(host.flight!!.id)

        // 详情退栈时页面先销毁注销头图
        host.unregisterHeader(albumId, headerRect)
        assertNull(host.headerRect(albumId))

        // 依然能降级历史快照成功触发反向飞行
        val backwardStarted = host.startBackward(albumId)
        assertTrue(backwardStarted)
        val flight = host.flight
        assertNotNull(flight)
        assertEquals(headerRect, flight.from)
        assertEquals(coverRect, flight.to)
    }

    @Test
    fun 正向飞行启动时记录相册标题与封面并在注销前可查() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP)
        val title = "测试相册标题"
        host.startForward(albumId, coverUrl, HERO_SEARCH_ROW_CORNER_DP, title)

        assertEquals(coverUrl, host.coverUrl(albumId))
        assertEquals(title, host.albumTitle(albumId))

        host.unregisterCover(albumId, coverRect)
        assertNull(host.coverUrl(albumId))
        assertNull(host.albumTitle(albumId))
    }

    @Test
    fun rememberAlbum与带title的registerCover均能正确持久化缓存并在header注销后依然有效() {
        val host = AlbumHeroHost()
        val title = "预存相册标题"
        host.rememberAlbum(albumId, title = title, coverUrl = coverUrl)
        assertEquals(title, host.albumTitle(albumId))
        assertEquals(coverUrl, host.coverUrl(albumId))

        // 通过带 title 的 registerCover 登记
        val anotherAlbumId = "album_456"
        val anotherTitle = "另一个标题"
        val anotherUrl = "https://example.com/another.jpg"
        host.registerCover(anotherAlbumId, anotherUrl, coverRect, HERO_SEARCH_ROW_CORNER_DP, anotherTitle)
        assertEquals(anotherTitle, host.albumTitle(anotherAlbumId))
        assertEquals(anotherUrl, host.coverUrl(anotherAlbumId))
    }
}
