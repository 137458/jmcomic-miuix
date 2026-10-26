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
    }

    @Test
    fun 设置线路后可读回且空表合法() = runTest {
        val r = repo()
        r.setApiDomains(listOf("a.example", "b.example"))
        assertEquals(listOf("a.example", "b.example"), r.apiDomains())
        r.setApiDomains(emptyList())
        assertEquals(emptyList(), r.apiDomains())
    }
}
