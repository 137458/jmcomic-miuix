package com.jm3.core.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BottomBarPreferencesTest {

    @Test
    fun `未设置时按窗口宽度断点解析默认值`() {
        assertFalse(BottomBarPreferencePolicy.isTablet(599))
        assertTrue(BottomBarPreferencePolicy.isTablet(600))

        // 手机默认普通，平板默认偏大
        assertEquals(BottomBarSize.STANDARD, BottomBarPreferencePolicy.resolveSize(null, 400))
        assertEquals(BottomBarSize.GENEROUS, BottomBarPreferencePolicy.resolveSize(null, 800))

        // 形态两档都默认悬浮
        assertEquals(BottomBarForm.FLOATING, BottomBarPreferencePolicy.resolveForm(null, 400))
        assertEquals(BottomBarForm.FLOATING, BottomBarPreferencePolicy.resolveForm(null, 800))
    }

    @Test
    fun `用户显式设置优先于断点`() {
        assertEquals(BottomBarSize.GENEROUS, BottomBarPreferencePolicy.resolveSize(BottomBarSize.GENEROUS, 400))
        assertEquals(BottomBarSize.STANDARD, BottomBarPreferencePolicy.resolveSize(BottomBarSize.STANDARD, 800))
        assertEquals(BottomBarForm.DOCKED, BottomBarPreferencePolicy.resolveForm(BottomBarForm.DOCKED, 400))
        assertEquals(BottomBarForm.DOCKED, BottomBarPreferencePolicy.resolveForm(BottomBarForm.DOCKED, 800))
    }

    @Test
    fun `未写入与非法值都回落为未设置`() = runBlocking {
        val repo = InMemorySettingsRepository()
        assertNull(repo.bottomBarSizeOrNull())
        assertNull(repo.bottomBarFormOrNull())

        repo.writeString(SettingsKeys.BOTTOM_BAR_SIZE, "HUGE")
        repo.writeString(SettingsKeys.BOTTOM_BAR_FORM, "SIDEWAYS")
        assertNull(repo.bottomBarSizeOrNull())
        assertNull(repo.bottomBarFormOrNull())
        // 非法值等价于未设置，继续走断点默认
        assertEquals(BottomBarSize.STANDARD, BottomBarPreferencePolicy.resolveSize(repo.bottomBarSizeOrNull(), 400))
        assertEquals(BottomBarSize.GENEROUS, BottomBarPreferencePolicy.resolveSize(repo.bottomBarSizeOrNull(), 800))
    }

    @Test
    fun `显式写入后可原样读回`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.setBottomBarSize(BottomBarSize.GENEROUS)
        repo.setBottomBarForm(BottomBarForm.DOCKED)
        assertEquals(BottomBarSize.GENEROUS, repo.bottomBarSizeOrNull())
        assertEquals(BottomBarForm.DOCKED, repo.bottomBarFormOrNull())
        assertFalse(BottomBarPreferencePolicy.resolveSize(repo.bottomBarSizeOrNull(), 400) == BottomBarSize.STANDARD)
    }
}
