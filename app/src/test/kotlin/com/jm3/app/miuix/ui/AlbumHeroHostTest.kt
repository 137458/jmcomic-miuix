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
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        assertEquals(coverRect, host.coverRect(albumId))

        // 相同参数重复登记应防抖安全跳过
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
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
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)

        val started = host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        assertTrue(started)
        val pending = host.pending
        assertNotNull(pending)
        assertEquals(albumId, pending.albumId)
        assertEquals(coverUrl, pending.url)
        assertEquals(coverRect, pending.from)
        assertEquals(HERO_CARD_CORNER_DP, pending.cornerFromDp)

        // 起飞前飞行计划为空，封面与头图均不隐藏
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 封面矩形无效或未登记时不触发正向待飞() {
        val host = AlbumHeroHost()
        // 未登记封面直接启动正向飞行
        assertFalse(host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP))
        assertNull(host.pending)

        // 登记非法宽高矩形
        val zeroRect = coverRect.copy(width = 0f)
        host.registerCover(albumId, coverUrl, zeroRect, HERO_CARD_CORNER_DP)
        assertFalse(host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP))
        assertNull(host.pending)
    }

    @Test
    fun 详情头图测量完成后正向起飞并锁定两端隐藏与圆角契约() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)

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
        assertEquals(HERO_CARD_CORNER_DP, flight.cornerFromDp)
        assertEquals(HERO_CARD_CORNER_DP, flight.cornerToDp)
        assertEquals(coverUrl, flight.url)

        // 覆盖层首帧还没画出来：两端必须保持可见，否则原位会露出背景空洞
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        // 覆盖层画出首帧后才让位
        host.markOverlayReady(flight.id)
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))
    }

    @Test
    fun 正向飞行结束落位后两端恢复可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()

        val flightId = host.flight!!.id
        host.markOverlayReady(flightId)
        assertTrue(host.isCoverHidden(albumId))
        host.finishFlight(flightId)

        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 待飞超时取消后待飞记录清空且两端保持可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
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
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
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
        assertEquals(HERO_CARD_CORNER_DP, flight.cornerToDp)
        assertEquals(coverUrl, flight.url)

        // 反向飞行起点进度为 0：覆盖层尚未参与绘制，两端封面必须先显示自己
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
        host.setBackwardProgress(flight.id, 0.35f)
        host.markOverlayReady(flight.id)
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))
    }

    @Test
    fun 反向飞行结束落位后完整复原状态() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
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
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)

        // 未调用 startForward，不在 heroOrigins 集合内
        val backwardStarted = host.startBackward(albumId)
        assertFalse(backwardStarted)
        assertNull(host.flight)
    }

    @Test
    fun 头图注销后退栈能降级使用历史测量快照触发反向飞行() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
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
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        val title = "测试相册标题"
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP, title)

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
        host.registerCover(anotherAlbumId, anotherUrl, coverRect, HERO_CARD_CORNER_DP, anotherTitle)
        assertEquals(anotherTitle, host.albumTitle(anotherAlbumId))
        assertEquals(anotherUrl, host.coverUrl(anotherAlbumId))
    }

    @Test
    fun 覆盖层就绪前两端封面保持可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        assertTrue(host.resolvePending())

        // 起飞瞬间覆盖层还是空白的；此时隐藏两端封面会露出一帧空洞，正是"闪一下"的来源
        assertNotNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 覆盖层画出首帧后两端封面隐藏() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()

        val flightId = host.flight!!.id
        host.markOverlayReady(flightId)

        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))
        // 只对当前飞行生效，别的相册不受影响
        assertFalse(host.isCoverHidden("album_other"))
    }

    @Test
    fun 就绪标记只认当前飞行id() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()

        val flightId = host.flight!!.id
        // 过期的飞行回调不得把封面藏起来
        host.markOverlayReady(flightId + 1)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        host.markOverlayReady(flightId)
        assertTrue(host.isCoverHidden(albumId))
    }

    @Test
    fun 飞行结束后就绪标记清空且下一段飞行重新等待() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.markOverlayReady(host.flight!!.id)

        host.finishFlight(host.flight!!.id)
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))

        // 反向飞行是新的 id，必须重新等覆盖层画出首帧，不能沿用上一段的就绪标记
        assertTrue(host.startBackward(albumId))
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        host.setBackwardProgress(host.flight!!.id, 0.2f)
        host.markOverlayReady(host.flight!!.id)
        assertTrue(host.isCoverHidden(albumId))
    }

    @Test
    fun 待飞阶段详情头图保持可见() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)

        // 待飞阶段覆盖层还没开始画，头图必须先显示，否则详情页头图位置会空一段
        assertNotNull(host.pending)
        assertNull(host.flight)
        assertFalse(host.isHeaderHidden(albumId))
        assertFalse(host.isCoverHidden(albumId))
    }

    @Test
    fun 没有飞行时标记就绪不产生副作用() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)

        host.markOverlayReady(1)
        assertNull(host.flight)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 追踪与云端观看记录行以缩略图契约圆角起飞并完成反向飞行() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_THUMB_CORNER_DP)
        val started = host.startForward(albumId, coverUrl, HERO_THUMB_CORNER_DP, "测试作品")
        assertTrue(started)
        assertEquals(HERO_THUMB_CORNER_DP, host.pending?.cornerFromDp)
        assertEquals("测试作品", host.albumTitle(albumId))

        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        assertTrue(host.resolvePending())
        assertEquals(HERO_THUMB_CORNER_DP, host.flight?.cornerFromDp)
        assertEquals(HERO_CARD_CORNER_DP, host.flight?.cornerToDp)

        val flightId = host.flight!!.id
        host.markOverlayReady(flightId)
        host.finishFlight(flightId)

        // 反向飞行落回追踪与观看记录缩略图契约圆角
        assertTrue(host.startBackward(albumId))
        assertEquals(HERO_CARD_CORNER_DP, host.flight?.cornerFromDp)
        assertEquals(HERO_THUMB_CORNER_DP, host.flight?.cornerToDp)
        host.finishFlight(host.flight!!.id)
    }

    @Test
    fun 反向飞行进度为0时两端封面保持可见且进度恢复后重新让位() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.finishFlight(host.flight!!.id)

        assertTrue(host.startBackward(albumId))
        val flightId = host.flight!!.id
        // 进度 0 时覆盖层与头图完全重合，由两端封面自己显示，覆盖层不参与绘制
        host.markOverlayReady(flightId)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))

        // 手势推开后才让位
        host.setBackwardProgress(flightId, 0.5f)
        assertTrue(host.isCoverHidden(albumId))
        assertTrue(host.isHeaderHidden(albumId))

        // 回弹回起点：覆盖层退场，两端封面恢复可见，不残留
        host.setBackwardProgress(flightId, 0f)
        assertFalse(host.isCoverHidden(albumId))
        assertFalse(host.isHeaderHidden(albumId))
    }

    @Test
    fun 反向飞行取消后恢复origin并允许再次起飞() {
        val host = AlbumHeroHost()
        host.registerCover(albumId, coverUrl, coverRect, HERO_CARD_CORNER_DP)
        host.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP)
        host.registerHeader(albumId, coverUrl, headerRect, HERO_CARD_CORNER_DP)
        host.resolvePending()
        host.finishFlight(host.flight!!.id)

        assertTrue(host.startBackward(albumId))
        val flightId = host.flight!!.id
        host.setBackwardProgress(flightId, 0.4f)
        // 取消回弹到静息：收场并把 origin 放回去
        host.finishFlight(flightId)
        host.restoreOrigin(albumId)
        assertNull(host.flight)

        // 下一次返回仍要能起飞
        assertTrue(host.startBackward(albumId))
        assertEquals(headerRect, host.flight?.from)
        assertEquals(coverRect, host.flight?.to)
    }
}
