package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class BottomClearanceTest {

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
        // 贴底标准底栏：contentPadding=56, floatingActionsBottom=77 -> 145 - 76 = 69
        assertEquals(
            69f,
            glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = 56f,
                floatingActionsBottomDp = 77f,
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
}
