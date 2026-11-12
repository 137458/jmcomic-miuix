package com.jm3.core.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WideScreenNavigationTest {

    @Test
    fun `显式解析未设置时回落底栏兜底`() {
        // 该函数只解析盘上的显式值；窗口断点默认由 resolveActiveWideScreenNavigation 负责
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(null),
        )
    }

    @Test
    fun `未设置时宽高都够才默认侧栏`() {
        assertEquals(
            WideScreenNavigation.RAIL,
            BottomBarPreferencePolicy.defaultWideScreenNavigation(1280, 800),
        )
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.defaultWideScreenNavigation(1280, 480),
        )
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.defaultWideScreenNavigation(400, 900),
        )
    }

    @Test
    fun `显式选择侧栏与底栏都原样生效`() {
        assertEquals(
            WideScreenNavigation.RAIL,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(WideScreenNavigation.RAIL),
        )
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(WideScreenNavigation.BOTTOM_BAR),
        )
    }

    @Test
    fun `切成侧栏再清空后回落默认底栏`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.setWideScreenNavigation(WideScreenNavigation.RAIL)
        assertEquals(
            WideScreenNavigation.RAIL,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(repo.wideScreenNavigationOrNull()),
        )

        repo.clearWideScreenNavigation()
        assertNull(repo.wideScreenNavigationOrNull())
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(repo.wideScreenNavigationOrNull()),
        )
    }

    @Test
    fun `未写入与非法值都回落为未设置`() = runBlocking {
        val repo = InMemorySettingsRepository()
        assertNull(repo.wideScreenNavigationOrNull())

        repo.writeString(SettingsKeys.WIDE_SCREEN_NAVIGATION, "SIDEBAR")
        assertNull(repo.wideScreenNavigationOrNull())
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(repo.wideScreenNavigationOrNull()),
        )
    }

    @Test
    fun `设置项只在 600dp 及以上可见`() {
        assertFalse(BottomBarPreferencePolicy.supportsWideScreenNavigation(599))
        assertTrue(BottomBarPreferencePolicy.supportsWideScreenNavigation(600))
        assertTrue(BottomBarPreferencePolicy.supportsWideScreenNavigation(1200))
    }

    @Test
    fun `窗口宽度变化后可见性重新判定`() {
        // 折叠展开或分屏拖动会跨过 600dp：同一份入参在两侧得到相反结论，调用方只需重组即可
        assertFalse(BottomBarPreferencePolicy.supportsWideScreenNavigation(580))
        assertTrue(BottomBarPreferencePolicy.supportsWideScreenNavigation(650))
        assertFalse(BottomBarPreferencePolicy.supportsWideScreenNavigation(540))
    }

    @Test
    fun `不可见设备残留显式侧栏也走底栏`() {
        // 用户曾在大屏选过侧栏，回到窄窗口时布局按默认底栏，不因残留值走 Rail
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(WideScreenNavigation.RAIL, 580, 800),
        )
        assertEquals(
            WideScreenNavigation.RAIL,
            BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(WideScreenNavigation.RAIL, 1000, 800),
        )
        assertEquals(
            WideScreenNavigation.RAIL,
            BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(null, 1000, 800),
        )
        // 高度不足时侧栏不成立：即便盘上残留显式侧栏，布局也必须回落底栏
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(WideScreenNavigation.RAIL, 1000, 400),
        )
    }
}
