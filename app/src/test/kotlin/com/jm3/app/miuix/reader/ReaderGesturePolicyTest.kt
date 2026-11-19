package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.isPageZoomed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 放大阅读时谁接管翻页手势。
 *
 * 两套皮肤的 HorizontalPager 都写着 pagerGestureOverride(pagerState, enabled = !pageZoomed)，
 * 而 pageZoomed 本身来自 isPageZoomed 的阈值判定；阈值与手势开关必须同一口径，
 * 否则放大到一半的画面会被 pager 抢走拖拽，平移变成翻页。
 */
class ReaderGesturePolicyTest {

    @Test
    fun `未放大时翻页手势交给 pager`() {
        assertTrue(pagerGestureEnabled(pageZoomed = false))
    }

    @Test
    fun `放大时让出翻页手势`() {
        assertFalse(pagerGestureEnabled(pageZoomed = true))
    }

    @Test
    fun `缩放阈值处的开关与 isPageZoomed 同一口径`() {
        // 未测量缩放比、1 倍、恰好等于阈值（不放大），仍由 pager 接管
        assertTrue(pagerGestureEnabled(isPageZoomed(null)))
        assertTrue(pagerGestureEnabled(isPageZoomed(0f)))
        assertTrue(pagerGestureEnabled(isPageZoomed(0.02f)))
        // 越过阈值即为放大：让出手势给画面的平移
        assertFalse(pagerGestureEnabled(isPageZoomed(0.021f)))
        assertFalse(pagerGestureEnabled(isPageZoomed(2.5f)))
    }
}
