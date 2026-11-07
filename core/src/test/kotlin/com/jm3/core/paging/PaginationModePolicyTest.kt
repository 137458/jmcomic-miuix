package com.jm3.core.paging

import com.jm3.core.settings.PaginationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 分页模式策略判定：启动读盘取哪个值、模式变了要不要重载。
 *
 * 判定是纯函数，测试只认公开接口上的业务结果，不碰实现细节。
 */
class PaginationModePolicyTest {

    private fun resolve(
        persisted: PaginationMode,
        current: PaginationMode,
        userModified: Boolean,
    ): PaginationMode = PaginationModePolicy.resolveStartupPaginationMode(
        persisted = persisted,
        current = current,
        userModified = userModified,
    )

    @Test
    fun 启动读盘_用户没改过时用盘上的值() {
        assertEquals(
            PaginationMode.CLICK,
            resolve(
                persisted = PaginationMode.CLICK,
                current = PaginationMode.INFINITE,
                userModified = false,
            ),
        )
    }

    @Test
    fun 启动读盘_用户改过时保留内存里的值() {
        assertEquals(
            PaginationMode.INFINITE,
            resolve(
                persisted = PaginationMode.CLICK,
                current = PaginationMode.INFINITE,
                userModified = true,
            ),
        )
    }

    @Test
    fun 启动读盘_盘上是上拉加载更多时同样用盘上的值() {
        assertEquals(
            PaginationMode.INFINITE,
            resolve(
                persisted = PaginationMode.INFINITE,
                current = PaginationMode.CLICK,
                userModified = false,
            ),
        )
    }

    @Test
    fun 启动读盘_用户改过时盘上的旧值不覆盖内存里的值() {
        assertEquals(
            PaginationMode.CLICK,
            resolve(
                persisted = PaginationMode.INFINITE,
                current = PaginationMode.CLICK,
                userModified = true,
            ),
        )
    }

    @Test
    fun 模式从无限滚动切成点击翻页算变了() {
        assertTrue(
            PaginationModePolicy.paginationModeChanged(
                previous = PaginationMode.INFINITE,
                next = PaginationMode.CLICK,
            ),
        )
    }

    @Test
    fun 模式从点击翻页切回无限滚动算变了() {
        assertTrue(
            PaginationModePolicy.paginationModeChanged(
                previous = PaginationMode.CLICK,
                next = PaginationMode.INFINITE,
            ),
        )
    }

    @Test
    fun 模式没变算没变() {
        assertFalse(
            PaginationModePolicy.paginationModeChanged(
                previous = PaginationMode.CLICK,
                next = PaginationMode.CLICK,
            ),
        )
    }
}
