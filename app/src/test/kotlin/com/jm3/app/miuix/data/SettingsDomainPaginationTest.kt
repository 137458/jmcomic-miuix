package com.jm3.app.miuix.data

import android.content.Context
import android.content.ContextWrapper
import com.jm3.core.settings.PaginationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 设置域的分页模式状态：值与「用户是否显式改过」标记。
 *
 * 标记的用途是启动读盘：用户在读盘返回前就改了设置时，盘上的旧值不该盖掉用户刚选的那个。
 */
class SettingsDomainPaginationTest {

    /** 假上下文：只需要应用上下文，装配期不碰 DataStore。 */
    private class FakeContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun domain() = SettingsDomain(FakeContext())

    @Test
    fun 初始分页模式是上拉加载更多且没被用户改过() {
        val domain = domain()
        assertEquals(PaginationMode.INFINITE, domain.paginationMode)
        assertFalse(domain.paginationModeUserModified)
    }

    @Test
    fun 用户赋值后取到的就是赋的值且标记为用户改过() {
        val domain = domain()
        domain.paginationMode = PaginationMode.CLICK
        assertEquals(PaginationMode.CLICK, domain.paginationMode)
        assertTrue(domain.paginationModeUserModified)
    }

    @Test
    fun 用户改回上拉加载更多同样标记为用户改过() {
        val domain = domain()
        domain.paginationMode = PaginationMode.CLICK
        domain.paginationMode = PaginationMode.INFINITE
        assertEquals(PaginationMode.INFINITE, domain.paginationMode)
        assertTrue(domain.paginationModeUserModified)
    }

    @Test
    fun 启动落盘上值不算用户改过() {
        val domain = domain()
        domain.applyStartupPaginationMode(PaginationMode.CLICK)
        assertEquals(PaginationMode.CLICK, domain.paginationMode)
        assertFalse(domain.paginationModeUserModified)
    }

    @Test
    fun 用户改过之后启动落值不会抹掉用户改过的标记() {
        val domain = domain()
        domain.paginationMode = PaginationMode.CLICK
        domain.applyStartupPaginationMode(PaginationMode.INFINITE)
        assertTrue(domain.paginationModeUserModified)
    }
}
