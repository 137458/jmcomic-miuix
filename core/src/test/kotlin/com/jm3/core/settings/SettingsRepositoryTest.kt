package com.jm3.core.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

    private fun repo() = InMemorySettingsRepository()

    @Test
    fun 默认主题为跟随系统() = runTest {
        assertEquals(ThemeMode.SYSTEM, repo().themeMode())
    }

    @Test
    fun 设置主题后可读回() = runTest {
        val r = repo()
        r.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, r.themeMode())
        r.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, r.themeMode())
    }

    @Test
    fun 默认线路为空表() = runTest {
        assertEquals(emptyList(), repo().apiDomains())
        assertEquals(null, repo().selectedApiDomain())
    }

    @Test
    fun 设置线路后可读回且空表合法() = runTest {
        val r = repo()
        r.setApiDomains(listOf("a.example", "b.example"))
        assertEquals(listOf("a.example", "b.example"), r.apiDomains())
        r.setApiDomains(emptyList())
        assertEquals(emptyList(), r.apiDomains())
    }

    @Test
    fun 固定线路写入后可读回且支持清空() = runTest {
        val r = repo()
        r.setSelectedApiDomain("cdn.example")
        assertEquals("cdn.example", r.selectedApiDomain())
        r.setSelectedApiDomain(null)
        assertEquals(null, r.selectedApiDomain())
    }

    @Test
    fun 动态取色默认关闭() = runTest {
        assertEquals(false, repo().dynamicColor())
    }

    @Test
    fun 设置动态取色后可读回() = runTest {
        val r = repo()
        r.setDynamicColor(true)
        assertEquals(true, r.dynamicColor())
        r.setDynamicColor(false)
        assertEquals(false, r.dynamicColor())
    }

    @Test
    fun 通知权限标记默认未询问() = runTest {
        assertEquals(false, repo().notificationPermissionAsked())
    }

    @Test
    fun 标记通知权限已询问后可读回() = runTest {
        val r = repo()
        r.markNotificationPermissionAsked()
        assertEquals(true, r.notificationPermissionAsked())
    }

    @Test
    fun 签到日期按账号读写并过滤非法日期与空白账号() = runTest {
        val r = repo()
        assertEquals(null, r.lastCheckInDate("1001"))

        r.setLastCheckInDate("1001", " 2026-11-12 ")
        r.setLastCheckInDate("1002", "2026-11-11")
        assertEquals("2026-11-12", r.lastCheckInDate("1001"))
        assertEquals("2026-11-11", r.lastCheckInDate("1002"))

        // 非法或空值写为清空，不误当已签
        r.setLastCheckInDate("1001", "not-a-date")
        assertEquals(null, r.lastCheckInDate("1001"))

        r.setLastCheckInDate("   ", "2026-11-12")
        assertEquals(null, r.lastCheckInDate("   "))
    }
}
