package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.glass.bottomBarTokens
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomSpacingTest {

    @Test
    fun coui首页底栏在双FAB上方留出额外呼吸净空() {
        // 16(bottom) + 56(fab) + 12(gap) + 56(fab) + 16(breathing) = 156
        assertEquals(156f, couiHomeExtraBottomDp(fabCount = 2))
        assertEquals(88f, couiHomeExtraBottomDp(fabCount = 1))
        assertEquals(0f, couiHomeExtraBottomDp(fabCount = 0))
    }

    @Test
    fun hyperos首页按底栏档位扣除脚手架自带净空后补足FAB净空() {
        // 宽屏侧栏（无底栏）：FAB 16..68 + 16 呼吸 = 84；Scaffold 已有 20 -> 需补 64
        assertEquals(
            64f,
            glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = 0f,
                floatingActionsBottomDp = 16f,
            ),
        )
        // 标准悬浮底栏：height=56, contentPadding=80, floatingActionsBottom=56+21=77
        // required = 77 + 52 + 16 = 145；provided = 80 + 20 = 100 -> 需补 45
        assertEquals(
            45f,
            glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = 80f,
                floatingActionsBottomDp = 77f,
            ),
        )
        // 偏大悬浮底栏：height=72, contentPadding=108, floatingActionsBottom=72+21=93
        // required = 93 + 52 + 16 = 161；provided = 108 + 20 = 128 -> 需补 33
        assertEquals(
            33f,
            glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = 108f,
                floatingActionsBottomDp = 93f,
            ),
        )
        // Miuix NavigationBar 贴底底栏：height=64, contentPadding=64, floatingActionsBottom=85 -> 153 - 84 = 69
        assertEquals(
            69f,
            glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = 64f,
                floatingActionsBottomDp = 85f,
            ),
        )
        assertEquals(
            0f,
            glassHomeExtraBottomDp(
                hasActions = false,
                floatingBarContentPaddingDp = 80f,
                floatingActionsBottomDp = 77f,
            ),
        )
    }

    @Test
    fun 作者作品详情仅在展示底部FloatingToolbar时增加净空() {
        assertEquals(72f, creatorWorkExtraBottomDp(hasRelated = true))
        assertEquals(0f, creatorWorkExtraBottomDp(hasRelated = false))
    }

    @Test
    fun coui页面底部安全边距按FAB数量与软键盘态统一收敛() {
        assertEquals(24f, couiPageExtraBottomDp(hasFab = false, isImeVisible = false))
        assertEquals(0f, couiPageExtraBottomDp(hasFab = false, isImeVisible = true))
        assertEquals(24f, couiPageExtraBottomDp(fabCount = 0, isImeVisible = false))
        assertEquals(0f, couiPageExtraBottomDp(fabCount = 0, isImeVisible = true))
        assertEquals(24f, couiPageExtraBottomDp(fabCount = -2, isImeVisible = false))
        assertEquals(72f, couiPageExtraBottomDp(hasFab = true, isImeVisible = false))
        assertEquals(72f, couiPageExtraBottomDp(hasFab = true, isImeVisible = true))
        assertEquals(156f, couiPageExtraBottomDp(fabCount = 2, isImeVisible = false))
    }

    @Test
    fun 底栏净空由条高底距呼吸三段相加得出且三档结果不变() {
        // 普通档悬浮：56 + 16 + 8 = 80
        assertEquals(80f, bottomBarContentPaddingDp(56f, BottomBarSize.STANDARD, BottomBarForm.FLOATING))
        // 偏大档悬浮：72 + 24 + 12 = 108
        assertEquals(108f, bottomBarContentPaddingDp(72f, BottomBarSize.GENEROUS, BottomBarForm.FLOATING))
        // 贴底通栏没有底距与呼吸：净空等于条高
        for (size in BottomBarSize.entries) {
            assertEquals(64f, bottomBarContentPaddingDp(64f, size, BottomBarForm.DOCKED))
        }
    }

    @Test
    fun token净空与条高始终来自同一次推导() {
        // 净空只由「条高 + 底距 + 呼吸」派生：谁把 80/108/64 写回 BottomBarTokens，这里就会对不上。
        for (size in BottomBarSize.entries) {
            for (form in BottomBarForm.entries) {
                val tokens = bottomBarTokens(size, form)
                val derived = bottomBarContentPaddingDp(tokens.height.value, size, form)
                assertEquals(derived, tokens.contentPadding.value, "$size/$form 的净空必须由本模块派生")
                if (form == BottomBarForm.FLOATING) {
                    assertEquals(
                        bottomBarContentPaddingDp(0f, size, form),
                        tokens.contentPadding.value - tokens.height.value,
                        "$size 的悬浮余量必须等于底距加呼吸",
                    )
                } else {
                    assertEquals(tokens.height.value, tokens.contentPadding.value, "$size 贴底净空必须等于条高")
                }
            }
        }
        // 三档结果逐位钉住，防止公式被"化简"成别的数
        assertEquals(80f, bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING).contentPadding.value)
        assertEquals(108f, bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING).contentPadding.value)
        assertEquals(64f, bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.DOCKED).contentPadding.value)
    }
}
