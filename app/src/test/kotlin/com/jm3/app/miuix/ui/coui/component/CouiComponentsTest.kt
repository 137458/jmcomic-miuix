// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouiComponentsTest {

    @Test
    fun `CouiMotion 动效规范参数符合预期`() {
        assertEquals(320, CouiMotion.PushDurationMs)
        assertEquals(200, CouiMotion.TabCrossfadeMs)
        assertEquals(240, CouiMotion.TopBarFadeMs)
        // 减速阻尼与弹簧参数
        assertEquals(0.55f, CouiMotion.PressSpring.dampingRatio)
        assertEquals(800f, CouiMotion.PressSpring.stiffness)
        assertEquals(0.78f, CouiMotion.TabSpring.dampingRatio)
        assertEquals(560f, CouiMotion.TabSpring.stiffness)
        assertEquals(0.78f, CouiMotion.TabColorSpring.dampingRatio)
        assertEquals(560f, CouiMotion.TabColorSpring.stiffness)
        assertEquals(0.85f, CouiMotion.ListSizeSpring.dampingRatio)
        assertEquals(400f, CouiMotion.ListSizeSpring.stiffness)
        assertEquals(0.8f, CouiMotion.DialogSpring.dampingRatio)
        assertEquals(500f, CouiMotion.DialogSpring.stiffness)
        assertEquals(0.85f, CouiMotion.SheetSpring.dampingRatio)
        assertEquals(450f, CouiMotion.SheetSpring.stiffness)
        assertEquals(0.85f, CouiMotion.SheetOffsetSpring.dampingRatio)
        assertEquals(450f, CouiMotion.SheetOffsetSpring.stiffness)
    }

    @Test
    fun `CouiBadge 数据契约正确表达计数与红点`() {
        val countBadge = CouiBadge(count = 5)
        assertEquals(5, countBadge.count)
        assertFalse(countBadge.dot)

        val dotBadge = CouiBadge(dot = true)
        assertNull(dotBadge.count)
        assertTrue(dotBadge.dot)
    }

    @Test
    fun `CouiBadge 正确格式化角标文本`() {
        assertEquals("5", CouiBadge(count = 5).displayText)
        assertEquals("99+", CouiBadge(count = 100).displayText)
        assertEquals("99+", CouiBadge(count = 999).displayText)
        assertNull(CouiBadge(dot = true).displayText)
        assertNull(CouiBadge().displayText)
    }

    @Test
    fun `couiVisibleTags 正确按折叠限制截断或展开标签`() {
        val tags = listOf("A", "B", "C", "D", "E")
        assertEquals(listOf("A", "B", "C"), couiVisibleTags(tags, expanded = false, collapsedLimit = 3))
        assertEquals(tags, couiVisibleTags(tags, expanded = true, collapsedLimit = 3))
        assertEquals(tags, couiVisibleTags(tags, expanded = false, collapsedLimit = null))
        assertEquals(tags, couiVisibleTags(tags, expanded = false, collapsedLimit = 10))
    }

    @Test
    fun `CouiNavItem 正确持有标签与图标`() {
        val item = CouiNavItem(label = "首页", icon = MiuixIcons.Home)
        assertEquals("首页", item.label)
        assertEquals(MiuixIcons.Home, item.icon)
    }
}
