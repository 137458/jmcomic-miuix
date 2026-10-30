package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumHeroBackwardFlightTest {

    private val cover = HeroRect(left = 16f, top = 400f, width = 48f, height = 64f)
    private val header = HeroRect(left = 48f, top = 220f, width = 336f, height = 448f)

    @Test
    fun headerRect在注销后仍可利用最后已知位置成功启动反向飞行() {
        val host = AlbumHeroHost()
        // 模拟列表点击启动正向飞行并记录 origin
        host.registerCover("album-1", "https://cover", cover, 6f)
        val started = host.startForward("album-1", "https://cover", 6f)
        assertTrue(started)

        // 模拟进入详情页测量头图
        host.registerHeader("album-1", "https://cover", header, 16f)
        host.resolvePending()
        host.finishFlight(host.flight?.id ?: 0)

        // 模拟退栈导致 header 先被注销
        host.unregisterHeader("album-1", header)
        assertNull(host.headerRect("album-1"))

        // 尝试启动反向飞行：应当能从最后已知位置成功起飞
        val backwardStarted = host.startBackward("album-1")
        assertTrue(backwardStarted)
        assertNotNull(host.flight)
    }

    @Test
    fun 未在heroOrigins中的相册即使有历史header也绝不起飞() {
        val host = AlbumHeroHost()
        // 未调用 startForward，未记录在 heroOrigins
        host.registerCover("album-2", "https://cover", cover, 6f)
        host.registerHeader("album-2", "https://cover", header, 16f)
        host.unregisterHeader("album-2", header)

        val backwardStarted = host.startBackward("album-2")
        assertFalse(backwardStarted)
        assertNull(host.flight)
    }
}
