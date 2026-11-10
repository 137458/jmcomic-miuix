// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.bottomBarTokens
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 底栏净空：底栏模式取 token 的净空值，侧栏模式为 0。
 *
 * 净空只允许有一个来源（token）。这里同时验证贴底档的净空随条高走，
 * 避免把悬浮档的 100/108dp 经验值带到通栏底栏上、把提示条顶到屏幕中部。
 */
class CouiMainScreenLayoutTest {

    @Test
    fun `侧栏模式不占底部净空`() {
        assertEquals(0.dp, couiBottomBarClearance(useRail = true, tokens = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)))
        assertEquals(0.dp, couiBottomBarClearance(useRail = true, tokens = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING)))
        assertEquals(0.dp, couiBottomBarClearance(useRail = true, tokens = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.DOCKED)))
    }

    @Test
    fun `底栏模式普通档悬浮按 token 净空让位`() {
        val tokens = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        assertEquals(100.dp, tokens.contentPadding)
        assertEquals(100.dp, couiBottomBarClearance(useRail = false, tokens = tokens))
    }

    @Test
    fun `底栏模式偏大档悬浮按 token 净空让位`() {
        val tokens = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING)
        assertEquals(108.dp, tokens.contentPadding)
        assertEquals(108.dp, couiBottomBarClearance(useRail = false, tokens = tokens))
    }

    @Test
    fun `底栏模式贴底档的净空等于条高`() {
        val standard = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.DOCKED)
        assertEquals(64.dp, standard.height)
        assertEquals(standard.height, couiBottomBarClearance(useRail = false, tokens = standard))

        val generous = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.DOCKED)
        assertEquals(72.dp, generous.height)
        assertEquals(generous.height, couiBottomBarClearance(useRail = false, tokens = generous))
    }

    @Test
    fun `600dp 断点向下取整不提前进入平板`() {
        assertEquals(599, 599.6f.dp.toLayoutDpInt())
        assertEquals(599, 599.99f.dp.toLayoutDpInt())
        assertEquals(600, 600.0f.dp.toLayoutDpInt())
    }
}
