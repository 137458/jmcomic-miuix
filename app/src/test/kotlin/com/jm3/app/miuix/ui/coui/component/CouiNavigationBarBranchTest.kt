// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import com.jm3.app.miuix.ui.glass.bottomBarTokens
import com.jm3.core.settings.BottomBarForm
import androidx.compose.ui.unit.dp
import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 底栏渲染分支：只有「普通档 + 悬浮形态 + API 34 及以上」交给库的浮动材质，其余组合自绘。
 *
 * 分支判定是纯函数（sdkInt 由参数传入），因此这里直接覆盖版本与 token 档位的组合矩阵，
 * 不依赖 Android 运行时，也就不会因为 JVM 单测里 SDK_INT 是桩而失真。
 */
class CouiNavigationBarBranchTest {

    @Test
    fun `普通档悬浮在 API 34 交给库渲染`() {
        assertTrue(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING), 34))
    }

    @Test
    fun `普通档悬浮在更高的 API 仍交给库渲染`() {
        assertTrue(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING), 35))
        assertTrue(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING), 36))
    }

    @Test
    fun `普通档悬浮在 API 34 以下自绘`() {
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING), 33))
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING), 30))
    }

    @Test
    fun `贴底形态即使 API 满足也自绘`() {
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.DOCKED), 34))
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.DOCKED), 35))
    }

    @Test
    fun `偏大档悬浮即使 API 满足也自绘`() {
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING), 34))
        assertFalse(couiUsesLibraryNavBar(bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING), 36))
    }

    @Test
    fun `库渲染的最低版本是 API 34`() {
        assertEquals(34, CouiLibraryNavBarMinSdk)
    }

    @Test
    fun `全部档位与版本组合只有一个象限交给库渲染`() {
        val libraryRendered = mutableListOf<String>()
        for (size in BottomBarSize.entries) {
            for (form in BottomBarForm.entries) {
                for (sdkInt in 29..36) {
                    if (couiUsesLibraryNavBar(bottomBarTokens(size, form), sdkInt)) {
                        libraryRendered += "$size/$form/$sdkInt"
                    }
                }
            }
        }
        // 2 档尺寸 × 2 种形态 × 8 个版本里，只有普通档悬浮在 34 及以上这 3 个组合走库
        assertEquals(
            listOf(
                "STANDARD/FLOATING/34",
                "STANDARD/FLOATING/35",
                "STANDARD/FLOATING/36",
            ),
            libraryRendered,
        )
    }

    @Test
    fun `自绘底栏宽度上限复算库的菜单宽加两侧圆键`() {
        // 24 + 70×n + 8×(n-1) + 136：n=4 时 464dp，和库的 maximumMenuWidth + 136dp 同源
        assertEquals(464.dp, couiPlainNavMaxWidth(4))
        assertEquals(230.dp, couiPlainNavMaxWidth(1))
        assertTrue(couiPlainNavMaxWidth(5) > couiPlainNavMaxWidth(4))
    }

    @Test
    fun `拖动预选按库语义决定释放项`() {
        // 释放点在另一项上：直接切到释放点
        assertEquals(3, couiPlainNavReleaseTarget(preview = 1, pending = null, down = 1, up = 3, cancelled = false))
        // 拖回按下项：待定项若已等于预览项，仍按预览项切换
        assertEquals(2, couiPlainNavReleaseTarget(preview = 2, pending = 2, down = 1, up = 1, cancelled = false))
        // 按下即抬起：同点点击补一次判定
        assertEquals(2, couiPlainNavReleaseTarget(preview = 1, pending = null, down = 2, up = 2, cancelled = false))
    }

    @Test
    fun `取消的拖动不产生切换`() {
        assertEquals(null, couiPlainNavReleaseTarget(preview = 1, pending = null, down = 2, up = 2, cancelled = true))
        assertEquals(null, couiPlainNavReleaseTarget(preview = 1, pending = null, down = 1, up = 1, cancelled = false))
        assertEquals(null, couiPlainNavReleaseTarget(preview = 1, pending = null, down = null, up = null, cancelled = false))
    }
}
