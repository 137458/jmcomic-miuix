package com.jm3.app.miuix.ui

import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDisplayNameTest {

    @Test
    fun `主题模式显示名与设置页文案一致`() {
        assertEquals("跟随系统", ThemeMode.SYSTEM.displayName)
        assertEquals("浅色", ThemeMode.LIGHT.displayName)
        assertEquals("深色", ThemeMode.DARK.displayName)
    }

    @Test
    fun `分页模式显示名与设置页文案一致`() {
        assertEquals("无限滚动", PaginationMode.INFINITE.displayName)
        assertEquals("点击翻页", PaginationMode.CLICK.displayName)
    }

    @Test
    fun `分页模式既有 label 保持不变`() {
        assertEquals("上拉加载更多", PaginationMode.INFINITE.label)
        assertEquals("点击切换分页", PaginationMode.CLICK.label)
    }
}
