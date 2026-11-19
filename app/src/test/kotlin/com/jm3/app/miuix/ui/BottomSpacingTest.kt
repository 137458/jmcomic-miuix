package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.coui.page.couiBottomBarClearance
import com.jm3.app.miuix.ui.glass.bottomBarTokens
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BottomSpacingTest {

    @Test
    fun 无底栏提供者时净空为零且脚手架只留系统栏与呼吸() {
        // 没有提供者：宽屏侧栏与不展示底栏的子页面底部没有覆盖层，净空就是 0
        assertEquals(0f, NO_BOTTOM_BAR_CLEARANCE_DP)
        // 脚手架此时只剩 20dp 呼吸，不会凭空空出一段底栏高度
        assertEquals(
            20f,
            scaffoldBottomPaddingDp(
                innerBottomDp = 0f,
                systemBarBottomDp = 0f,
                floatingBarContentPaddingDp = NO_BOTTOM_BAR_CLEARANCE_DP,
            ),
        )
        // 系统栏与内边距取较大者再叠加底栏与呼吸：max(16, 24) + 80 + 20 = 124
        assertEquals(
            124f,
            scaffoldBottomPaddingDp(
                innerBottomDp = 16f,
                systemBarBottomDp = 24f,
                floatingBarContentPaddingDp = 80f,
            ),
        )
        // 0 只能表示「无底栏」：每个真实底栏档位的净空都不为 0
        for (size in BottomBarSize.entries) {
            for (form in BottomBarForm.entries) {
                assertNotEquals(0f, bottomBarTokens(size, form).contentPadding.value, "$size/$form 有底栏就必然占净空")
            }
        }
    }

    @Test
    fun 主屏提供净空等于底栏推导结果且侧栏归零() {
        for (size in BottomBarSize.entries) {
            for (form in BottomBarForm.entries) {
                val tokens = bottomBarTokens(size, form)
                val provided = mainBottomBarClearanceDp(
                    useRail = false,
                    contentPaddingDp = tokens.contentPadding.value,
                )
                // 提供者交出的净空必须就是推导结果本身，不能在调用点重算成别的数
                assertEquals(
                    bottomBarContentPaddingDp(tokens.height.value, size, form),
                    provided,
                    "$size/$form 主屏净空必须与底栏推导同源",
                )
                assertEquals(tokens.contentPadding.value, provided)
                // 侧栏模式没有底栏，净空必须归零
                assertEquals(0f, mainBottomBarClearanceDp(useRail = true, contentPaddingDp = tokens.contentPadding.value))
            }
        }
        // COUI 皮肤的提供者与玻璃皮肤共用同一推导
        val standard = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        assertEquals(80f, couiBottomBarClearance(useRail = false, tokens = standard).value)
        assertEquals(0f, couiBottomBarClearance(useRail = true, tokens = standard).value)
    }

    @Test
    fun 阅读器尾部净空与原硬编码96dp等价() {
        // 默认参数必须仍是 96dp：80(工具条净空) + 16(呼吸)，两套皮肤视觉不变
        assertEquals(96f, readerTailClearanceDp())
        // 分量可独立演进：只换工具条净空时结果跟着走，没有把 96 写死
        assertEquals(80f, readerTailClearanceDp(barClearanceDp = 64f))
        assertEquals(60f, readerTailClearanceDp(barClearanceDp = 44f, tailBreathingDp = 16f))
    }

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
    fun coui页面底部安全边距按有无FAB与软键盘态统一收敛() {
        // 无 FAB：给页面呼吸 24dp；键盘真的占用本页时归零（键盘自己让位）
        assertEquals(24f, couiPageExtraBottomDp(hasFab = false, imeOccupiesPage = false))
        assertEquals(0f, couiPageExtraBottomDp(hasFab = false, imeOccupiesPage = true))
        assertEquals(24f, couiPageExtraBottomDp())
        // 有 FAB：固定 72dp，键盘不影响
        assertEquals(72f, couiPageExtraBottomDp(hasFab = true, imeOccupiesPage = false))
        assertEquals(72f, couiPageExtraBottomDp(hasFab = true, imeOccupiesPage = true))
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
