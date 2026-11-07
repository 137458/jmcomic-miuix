package com.jm3.core.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WideScreenNavigationTest {

    @Test
    fun `未设置时宽屏默认底栏`() {
        // 未显式设置时不再按设备类型分派：宽屏同样渲染底栏，侧栏只在用户主动选择时启用
        assertEquals(
            WideScreenNavigation.BOTTOM_BAR,
            BottomBarPreferencePolicy.resolveWideScreenNavigation(null),
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
}
