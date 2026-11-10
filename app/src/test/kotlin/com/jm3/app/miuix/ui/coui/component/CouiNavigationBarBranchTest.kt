// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import com.jm3.app.miuix.ui.glass.bottomBarTokens
import com.jm3.core.settings.BottomBarForm
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
}
