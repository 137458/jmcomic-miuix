package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlbumHeroRegistrationTest {

    private val rect1 = HeroRect(left = 10f, top = 20f, width = 100f, height = 150f)
    private val rect2 = HeroRect(left = 15f, top = 25f, width = 100f, height = 150f)
    private val badRect = HeroRect(left = 0f, top = 0f, width = 0f, height = 0f)

    @Test
    fun 注册合法cover能通过coverRect读出() {
        val host = AlbumHeroHost()
        host.registerCover("album-1", "https://cover1", rect1, 8f)
        assertEquals(rect1, host.coverRect("album-1"))
    }

    @Test
    fun 注册非法cover不会写入() {
        val host = AlbumHeroHost()
        host.registerCover("album-1", "https://cover1", badRect, 8f)
        assertNull(host.coverRect("album-1"))
    }

    @Test
    fun 注销cover时若rect匹配则清理cover() {
        val host = AlbumHeroHost()
        host.registerCover("album-1", "https://cover1", rect1, 8f)
        host.unregisterCover("album-1", rect1)
        assertNull(host.coverRect("album-1"))
    }

    @Test
    fun 注销cover时若rect不匹配则保留当前最新矩形() {
        val host = AlbumHeroHost()
        host.registerCover("album-1", "https://cover1", rect2, 8f)
        // 尝试注销旧的 rect1
        host.unregisterCover("album-1", rect1)
        assertEquals(rect2, host.coverRect("album-1"))
    }

    @Test
    fun 注册合法header能通过headerRect读出() {
        val host = AlbumHeroHost()
        host.registerHeader("album-1", "https://cover1", rect1, 16f)
        assertEquals(rect1, host.headerRect("album-1"))
    }

    @Test
    fun 注销header时若rect匹配则清理header() {
        val host = AlbumHeroHost()
        host.registerHeader("album-1", "https://cover1", rect1, 16f)
        host.unregisterHeader("album-1", rect1)
        assertNull(host.headerRect("album-1"))
    }

    @Test
    fun 多相册同时登记互不干扰() {
        val host = AlbumHeroHost()
        host.registerCover("album-1", "https://cover1", rect1, 8f)
        host.registerCover("album-2", "https://cover2", rect2, 8f)

        assertEquals(rect1, host.coverRect("album-1"))
        assertEquals(rect2, host.coverRect("album-2"))

        host.unregisterCover("album-1", rect1)
        assertNull(host.coverRect("album-1"))
        assertEquals(rect2, host.coverRect("album-2"))
    }
}
