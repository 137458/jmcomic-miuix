package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 阅读页窗口状态的引用计数：换章时新旧阅读页会短暂同时挂载，
 * 亮度原值与亮屏标记必须由首个页面记录、最后一个页面恢复，否则旧页会把新页的设置抹掉。
 */
class ReaderWindowLeaseTest {

    @Test
    fun `换章并存时原亮度只在首屏记录`() {
        val lease = ReaderWindowLease()

        assertTrue(lease.enterBrightness(-1f))
        assertFalse(lease.enterBrightness(0.4f))
        assertEquals(-1f, lease.originalBrightness)
    }

    @Test
    fun `最后一个页面离开才恢复亮度`() {
        val lease = ReaderWindowLease()
        lease.enterBrightness(-1f)
        lease.enterBrightness(0.4f)

        assertFalse(lease.exitBrightness())
        assertTrue(lease.exitBrightness())
    }

    @Test
    fun `亮屏标记按引用计数保留`() {
        val lease = ReaderWindowLease()

        assertTrue(lease.enterKeepScreenOn())
        assertFalse(lease.enterKeepScreenOn())
        assertFalse(lease.exitKeepScreenOn())
        assertTrue(lease.exitKeepScreenOn())
    }

    @Test
    fun `未持有就退出不会误恢复`() {
        val lease = ReaderWindowLease()

        assertFalse(lease.exitBrightness())
        assertFalse(lease.exitKeepScreenOn())
        assertNull(lease.originalBrightness)
    }

    @Test
    fun `亮度与亮屏计数互不影响`() {
        val lease = ReaderWindowLease()
        lease.enterBrightness(-1f)
        lease.enterBrightness(0.4f)
        lease.enterKeepScreenOn()

        assertTrue(lease.exitKeepScreenOn())
        assertFalse(lease.exitBrightness())
    }
}
