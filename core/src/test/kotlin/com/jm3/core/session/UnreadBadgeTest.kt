package com.jm3.core.session

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UnreadBadgeTest {

    /** 可换结果的桩：同一个角标要能被连续刷新，才测得到「失败保留上次值」。 */
    private class Loader {
        var calls = 0
        var value = 0
        var failure: Exception? = null

        suspend fun load(): Int {
            calls++
            failure?.let { throw it }
            return value
        }
    }

    @Test
    fun 未登录时归零且不读网络() = runBlocking {
        val loader = Loader().apply { value = 5 }
        val badge = UnreadBadge(loader::load)

        badge.refresh(loggedIn = false)

        assertEquals(0, badge.count.value)
        assertEquals(0, loader.calls, "未登录不应发起未读数请求")
    }

    @Test
    fun 登录后写入实际未读数() = runBlocking {
        val loader = Loader().apply { value = 5 }
        val badge = UnreadBadge(loader::load)

        badge.refresh(loggedIn = true)

        assertEquals(5, badge.count.value)
    }

    @Test
    fun 拉取失败保留上次计数() = runBlocking {
        val loader = Loader().apply { value = 7 }
        val badge = UnreadBadge(loader::load)
        badge.refresh(loggedIn = true)
        assertEquals(7, badge.count.value)

        loader.failure = IllegalStateException("网络断了")
        badge.refresh(loggedIn = true)

        assertEquals(7, badge.count.value, "拉取失败应保留上次结果，避免角标闪烁")
    }

    @Test
    fun 首次失败保持零而不是抛给界面() = runBlocking {
        val loader = Loader().apply { failure = IllegalStateException("网络断了") }
        val badge = UnreadBadge(loader::load)

        badge.refresh(loggedIn = true)

        assertEquals(0, badge.count.value)
    }

    @Test
    fun 登出后清掉旧计数() = runBlocking {
        val loader = Loader().apply { value = 9 }
        val badge = UnreadBadge(loader::load)
        badge.refresh(loggedIn = true)

        badge.refresh(loggedIn = false)

        assertEquals(0, badge.count.value)
    }

    @Test
    fun 已读操作可覆盖计数且不读网络() = runBlocking {
        val loader = Loader().apply { value = 9 }
        val badge = UnreadBadge(loader::load)
        badge.refresh(loggedIn = true)

        badge.sync(2)

        assertEquals(2, badge.count.value)
        assertEquals(1, loader.calls, "列表已加载时按本地已读状态覆盖，不再发请求")
    }
}
